import base64
import errno
import json
import os
import traceback
from contextlib import contextmanager
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from loomspan_sdk import AuthenticatedIdentity, ColocatedSigning, ExecutionTokenIssuer, LocalFailure, ProvidedSigning
from loomspan_sdk import _signing
from pyasn1.codec.ber import decoder
from pyasn1.codec.ber import encoder as ber
from pyasn1.codec.der import encoder as der
from pyasn1.type import univ
from pyasn1_modules import rfc5958, rfc8017

from test_issuance import inspect, unbase64

FIXTURES = Path(__file__).resolve().parents[2] / "java/core/src/test/resources/signing"
PEM = (FIXTURES / "synthetic-rsa.pem").read_bytes()
EXPECTED = json.loads((FIXTURES / "public-jwks.json").read_bytes())
PKCS8 = base64.b64decode(b"".join(PEM.splitlines()[1:-1]), validate=True)
PRIOR = b"prior complete public export"
MARKER = "synthetic-private-marker"
PATH_MARKER = "synthetic-caller-path"
TOKEN_MARKER = "Bearer synthetic-credential"
BACKEND_MARKER = "synthetic-backend-detail"


def assert_sanitized(error, caplog, *markers):
    assert error.__cause__ is None and error.__context__ is None
    displayed = str(error) + repr(error) + "".join(traceback.format_exception(error)) + caplog.text
    for marker in markers:
        assert marker not in displayed


def pem(encoded):
    return b"-----BEGIN PRIVATE KEY-----\n" + base64.b64encode(encoded) + b"\n-----END PRIVATE KEY-----\n"


def private_info():
    return decoder.decode(PKCS8, asn1Spec=rfc5958.OneAsymmetricKey())[0]


def embed(info, public, *, padding=0, version=1):
    info["version"] = version
    info["publicKey"] = info["publicKey"].clone(univ.BitString.fromOctetString(public, padding=padding))
    return info


def public_numbers():
    [jwk] = EXPECTED["keys"]
    public = rfc8017.RSAPublicKey()
    public["modulus"] = int.from_bytes(unbase64(jwk["n"].encode()))
    public["publicExponent"] = int.from_bytes(unbase64(jwk["e"].encode()))
    return public


def append_outer_field(tail):
    # The shared fixture's outer sequence uses two length octets.
    assert PKCS8[:2] == b"\x30\x82"
    body = PKCS8[4:] + bytes.fromhex(tail)
    return PKCS8[:2] + len(body).to_bytes(2, "big") + body


def replace_version(sequence, version):
    assert sequence[:2] == b"\x30\x82" and sequence[4:7] == b"\x02\x01\x00"
    body = bytes.fromhex(version) + sequence[7:]
    return sequence[:2] + len(body).to_bytes(2, "big") + body


def with_open_attribute(value):
    # Attribute OID 1.2.3 with one short BER-encoded open value.
    size = len(bytes.fromhex(value))
    return append_outer_field(f"a0{size + 8:02x}30{size + 6:02x}06022a0331{size:02x}" + value)


def make_provided(directory, material=PEM):
    private = directory / "signer.pem"
    private.write_bytes(material)
    return ProvidedSigning(private, directory / "public.json")


def test_colocated_restart_exports_only_public_material_and_changes_identity(tmp_path):
    mode = ColocatedSigning(tmp_path)
    identity = AuthenticatedIdentity("alice")
    keys = []
    for _ in range(2):
        issuer = ExecutionTokenIssuer(mode)
        export = tmp_path / "jwks.json"
        jwks = json.loads(export.read_bytes())
        [public] = jwks["keys"]
        assert set(public) == {"kty", "n", "e", "kid", "alg", "use"}
        assert (public["kty"], public["alg"], public["use"]) == ("RSA", "RS256", "sig")
        assert int.from_bytes(unbase64(public["n"].encode())).bit_length() == 2048
        assert inspect(issuer.issue(identity), jwks)[0]["sub"] == "alice"
        assert list(tmp_path.iterdir()) == [export]
        keys.append(public["kid"])
    assert keys[0] != keys[1]


@pytest.mark.parametrize("ending", [b"\n", b"\r\n", b"\r"])
def test_provided_whitespace_and_same_key_identity(tmp_path, ending):
    opening, *lines, closing = PEM.splitlines()
    material = ending.join([b" \t\f\v", opening + b" \t", *(b" \t" + line + b" " for line in lines), closing, b" \t\f\v"])
    mode = make_provided(tmp_path, material)
    for _ in range(2):
        issuer = ExecutionTokenIssuer(mode)
        jwks = json.loads(mode.jwks_export.read_bytes())
        assert jwks == EXPECTED
        assert inspect(issuer.issue(AuthenticatedIdentity("alice")), jwks)[0]["sub"] == "alice"
        assert mode.key_location.read_bytes() == material


def test_provided_signer_reads_once_and_changes_key_only_at_restart(tmp_path, monkeypatch):
    mode = make_provided(tmp_path)
    real_read = Path.read_bytes
    reads = []

    def record_read(path):
        if path == mode.key_location:
            reads.append(path)
        return real_read(path)

    monkeypatch.setattr(Path, "read_bytes", record_read)
    running = ExecutionTokenIssuer(mode)
    mode.key_location.unlink()
    identity = AuthenticatedIdentity("alice")
    inspect(running.issue(identity), EXPECTED)
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    mode.key_location.write_bytes(key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    ))
    inspect(running.issue(identity), EXPECTED)
    assert reads == [mode.key_location]
    restarted = ExecutionTokenIssuer(mode)
    assert reads == [mode.key_location, mode.key_location]
    new = json.loads(mode.jwks_export.read_bytes())
    assert new["keys"][0]["kid"] != EXPECTED["keys"][0]["kid"]
    inspect(restarted.issue(identity), new)


def test_provided_accepts_valid_pkcs8_optional_fields_and_ber(tmp_path):
    public = public_numbers()
    info = private_info()
    algorithm = info["privateKeyAlgorithm"].clone()
    algorithm["algorithm"] = info["privateKeyAlgorithm"]["algorithm"]
    info["privateKeyAlgorithm"] = algorithm
    no_parameters = der.encode(info)
    info = private_info()
    info["version"] = 1
    v2_without_public = der.encode(info)
    matching = der.encode(embed(private_info(), der.encode(public)))
    inner = decoder.decode(private_info()["privateKey"].asOctets(), asn1Spec=rfc8017.RSAPrivateKey())[0]
    info = private_info()
    info["privateKey"] = ber.encode(inner, defMode=False)
    ber_inner = der.encode(info)
    ber_public = der.encode(embed(private_info(), ber.encode(public, defMode=False)))
    ber_outer = ber.encode(private_info(), defMode=False)

    for encoded in (
        append_outer_field("a00b300906022a0331030c0141"), no_parameters, v2_without_public,
        matching, ber_inner, ber_public, ber_outer,
    ):
        mode = make_provided(tmp_path, pem(encoded))
        ExecutionTokenIssuer(mode)
        assert json.loads(mode.jwks_export.read_bytes()) == EXPECTED


def test_invalid_provided_material_never_falls_back_or_replaces_files(tmp_path, monkeypatch, caplog):
    key = serialization.load_pem_private_key(PEM, password=None)
    traditional_pem = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.TraditionalOpenSSL, serialization.NoEncryption()
    )
    encrypted_pem = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.BestAvailableEncryption(b"synthetic")
    )
    weak = rsa.generate_private_key(public_exponent=65537, key_size=1024)
    non_rsa = ec.generate_private_key(ec.SECP256R1())
    invalid = [
        b"", b"\xff", MARKER.encode(), PEM + PEM, MARKER.encode() + PEM, PEM + MARKER.encode(),
        b"-----BEGIN CERTIFICATE-----\n" + MARKER.encode() + b"\n-----END CERTIFICATE-----\n",
        key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo),
        PEM.replace(b"\n-----END", b"-----END"), PEM.replace(b"=", b""), PEM.replace(b"MII", b"M!II", 1),
        traditional_pem, traditional_pem.replace(b"RSA PRIVATE KEY", b"PRIVATE KEY"), encrypted_pem,
        pem(b"\x30\x00"), pem(b"\x30\x02\x02\x00"), pem(PKCS8[:-1]), pem(PKCS8 + b"\x00"),
    ]
    invalid += [key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    ) for key in (weak, non_rsa)]

    def forbid_generation(*args, **kwargs):
        pytest.fail("Provided mode tried to generate a key")

    monkeypatch.setattr(_signing.rsa, "generate_private_key", forbid_generation)
    for material in invalid:
        mode = make_provided(tmp_path, material)
        mode.jwks_export.write_bytes(PRIOR)
        with pytest.raises(LocalFailure, match=r"key_location.*unencrypted PKCS#8.*2048") as error:
            ExecutionTokenIssuer(mode)
        assert_sanitized(error.value, caplog, MARKER, "BEGIN PRIVATE", "index out of range")
        assert mode.key_location.read_bytes() == material and mode.jwks_export.read_bytes() == PRIOR
        assert set(tmp_path.iterdir()) == {mode.key_location, mode.jwks_export}

    mode.key_location.unlink()
    with pytest.raises(LocalFailure, match=r"key_location.*missing.*Provision"):
        ExecutionTokenIssuer(mode)
    assert not mode.key_location.exists() and mode.jwks_export.read_bytes() == PRIOR


@pytest.mark.parametrize("value", ["800141", "a000", "a006020101020102", "a0800201010000", "9f1f0141"],
                         ids=["implicit-primitive", "empty-constructed", "constructed", "indefinite", "long-tag"])
def test_provided_accepts_well_formed_open_attribute_values(tmp_path, value):
    mode = make_provided(tmp_path, pem(with_open_attribute(value)))
    ExecutionTokenIssuer(mode)
    assert json.loads(mode.jwks_export.read_bytes()) == EXPECTED


@pytest.mark.parametrize("value", [
    "050101", "0200", "30020101", "a0020101", "0000", "06022a81", "a08006022a810000", "1f020101", "9f801f0141",
], ids=[
    "nonempty-null", "empty-integer", "truncated-sequence", "truncated-context", "unexpected-eoc",
    "truncated-oid", "nested-truncated-oid", "long-small-tag", "long-tag-leading-zero",
])
def test_provided_rejects_malformed_open_attribute_values(tmp_path, value):
    material = pem(with_open_attribute(value))
    mode = make_provided(tmp_path, material)
    mode.jwks_export.write_bytes(PRIOR)
    with pytest.raises(LocalFailure):
        ExecutionTokenIssuer(mode)
    assert mode.key_location.read_bytes() == material and mode.jwks_export.read_bytes() == PRIOR


def test_rejects_malformed_pkcs8_structures_and_inconsistent_public_key(tmp_path):
    wrong_algorithm_parameters = private_info()
    wrong_algorithm_parameters["privateKeyAlgorithm"]["parameters"] = der.encode(univ.Integer(1))
    bad_version = private_info()
    bad_version["version"] = 2
    trailing_inner = private_info()
    trailing_inner["privateKey"] = trailing_inner["privateKey"].asOctets() + b"\x00"
    invalid_inner = private_info()
    invalid_inner["privateKey"] = der.encode(univ.Null(""))
    public = public_numbers()
    matching = der.encode(public)
    wrong_modulus = public_numbers()
    wrong_modulus["modulus"] = int(public["modulus"]) + 2
    wrong_exponent = public_numbers()
    wrong_exponent["publicExponent"] = 3
    invalid_attributes = [
        "a0020101",
        "a0020500",
        "a008300606022a033100",  # Empty attribute value set.
        "a0093007060031030c0141",  # Empty attribute identifier with a valid value.
        "a00b300906022a0331030c0141a000",
    ]
    malformed = [append_outer_field(value) for value in invalid_attributes]
    malformed += [der.encode(value) for value in (
        wrong_algorithm_parameters, bad_version, trailing_inner, invalid_inner,
        embed(private_info(), matching, padding=1), embed(private_info(), matching, version=0),
        embed(private_info(), b"\x05\x00"), embed(private_info(), matching + b"\x00"),
        embed(private_info(), der.encode(wrong_modulus)), embed(private_info(), der.encode(wrong_exponent)),
    )]
    for value in malformed:
        material = pem(value)
        mode = make_provided(tmp_path, material)
        mode.jwks_export.write_bytes(PRIOR)
        with pytest.raises(LocalFailure):
            ExecutionTokenIssuer(mode)
        assert mode.key_location.read_bytes() == material and mode.jwks_export.read_bytes() == PRIOR


@pytest.mark.parametrize("version", ["0200", "02020000"], ids=["empty-integer", "redundant-integer-sign"])
def test_provided_rejects_invalid_version_encodings_in_outer_and_inner_structures(tmp_path, version):
    info = private_info()
    info["privateKey"] = replace_version(info["privateKey"].asOctets(), version)
    for malformed in (replace_version(PKCS8, version), der.encode(info)):
        mode = make_provided(tmp_path, pem(malformed))
        with pytest.raises(LocalFailure):
            ExecutionTokenIssuer(mode)
        assert not mode.jwks_export.exists()


@pytest.mark.parametrize("failure,diagnostic", [
    (ValueError, r"key_location.*unencrypted PKCS#8.*2048"),
    (RuntimeError, r"Signing initialization failed.*signing configuration.*SDK runtime"),
])
def test_key_loader_error_is_sanitized(tmp_path, monkeypatch, caplog, failure, diagnostic):
    tmp_path = tmp_path / PATH_MARKER
    tmp_path.mkdir()
    mode = make_provided(tmp_path)
    mode.jwks_export.write_bytes(PRIOR)

    def fail_load(*args, **kwargs):
        raise failure(f"{mode.key_location}: {PEM.decode()} {TOKEN_MARKER} {BACKEND_MARKER}")

    monkeypatch.setattr(_signing.serialization, "load_der_private_key", fail_load)
    with pytest.raises(LocalFailure, match=diagnostic) as error:
        ExecutionTokenIssuer(mode)
    assert_sanitized(error.value, caplog, PATH_MARKER, "BEGIN PRIVATE", TOKEN_MARKER, BACKEND_MARKER)
    assert mode.key_location.read_bytes() == PEM and mode.jwks_export.read_bytes() == PRIOR


@pytest.mark.parametrize("is_directory", [False, True], ids=["unreadable", "non-file"])
def test_key_location_requires_a_readable_file(tmp_path, monkeypatch, caplog, is_directory):
    private = tmp_path / PATH_MARKER
    export = tmp_path / "public.json"
    export.write_bytes(PRIOR)
    mode = ProvidedSigning(private, export)
    if is_directory:
        private.mkdir()
    else:
        private.write_bytes(PEM)
        read_bytes = Path.read_bytes

        def unreadable(path):
            if path == private:
                raise PermissionError(errno.EACCES, BACKEND_MARKER, str(path))
            return read_bytes(path)

        monkeypatch.setattr(Path, "read_bytes", unreadable)
    with pytest.raises(LocalFailure, match=r"key_location.*readable regular file") as error:
        ExecutionTokenIssuer(mode)
    assert_sanitized(error.value, caplog, PATH_MARKER, BACKEND_MARKER)
    assert export.read_bytes() == PRIOR
    assert set(tmp_path.iterdir()) == {private, export}
    if not is_directory:
        assert read_bytes(private) == PEM


@pytest.mark.parametrize("fail_at", ["generate", "self-check"])
def test_signing_initialization_failure_precedes_publication(tmp_path, monkeypatch, caplog, fail_at):
    if fail_at == "generate":
        mode = ColocatedSigning(tmp_path)
        field = "key_directory"
        export = tmp_path / "jwks.json"
    else:
        mode = make_provided(tmp_path)
        field = "key_location"
        export = mode.jwks_export
    export.write_bytes(PRIOR)

    def fail(*args, **kwargs):
        raise RuntimeError(f"{BACKEND_MARKER} {TOKEN_MARKER}")

    if fail_at == "generate":
        monkeypatch.setattr(_signing.rsa, "generate_private_key", fail)
    else:
        monkeypatch.setattr(_signing, "_sign_rs256", fail)
    with pytest.raises(LocalFailure, match=rf"RSA RS256 signing.*{field}.*cryptographic runtime") as error:
        ExecutionTokenIssuer(mode)
    assert_sanitized(error.value, caplog, BACKEND_MARKER, TOKEN_MARKER)
    assert export.read_bytes() == PRIOR
    if isinstance(mode, ProvidedSigning):
        assert mode.key_location.read_bytes() == PEM


def test_settings_require_an_explicit_mode_and_paths_without_doing_io(tmp_path):
    missing = tmp_path / "absent"
    assert ColocatedSigning(missing).key_directory == missing
    assert ProvidedSigning(missing, missing).key_location == missing
    for build in (
        lambda: ColocatedSigning(), lambda: ProvidedSigning(missing),
        lambda: ColocatedSigning(missing, key_location=missing),
        lambda: ProvidedSigning(missing, missing, key_directory=missing),
        lambda: ProvidedSigning(missing, missing, password="unsupported"),
    ):
        with pytest.raises(TypeError):
            build()
    for build in (
        lambda: ColocatedSigning(str(missing)), lambda: ProvidedSigning(missing, None),
        lambda: ProvidedSigning("signer.pem", missing), lambda: ExecutionTokenIssuer({"mode": "colocated"}),
        lambda: ExecutionTokenIssuer(ColocatedSigning(tmp_path), {}),
    ):
        with pytest.raises(LocalFailure):
            build()
    assert not list(tmp_path.iterdir())


def test_export_rejects_normalized_overlap_and_hardlinks(tmp_path):
    mode = make_provided(tmp_path)
    (tmp_path / "through").mkdir()
    hardlink = tmp_path / "hardlink.pem"
    hardlink.hardlink_to(mode.key_location)
    for alias in (mode.key_location, tmp_path / "through" / ".." / mode.key_location.name, hardlink):
        with pytest.raises(LocalFailure, match="must not replace"):
            ExecutionTokenIssuer(ProvidedSigning(mode.key_location, alias))
        assert mode.key_location.read_bytes() == PEM and alias.read_bytes() == PEM


def symlink_or_skip(link, target):
    try:
        link.symlink_to(target, target_is_directory=target.is_dir())
    except OSError as failure:
        if failure.errno in {errno.EACCES, errno.EPERM, errno.ENOSYS, errno.ENOTSUP} or getattr(failure, "winerror", None) == 1314:
            pytest.skip("Host does not allow symbolic links in the test directory")
        raise


def test_export_rejects_symbolic_link_to_signing_input(tmp_path):
    mode = make_provided(tmp_path)
    symlink_or_skip(mode.jwks_export, mode.key_location)
    with pytest.raises(LocalFailure, match="must not replace"):
        ExecutionTokenIssuer(mode)
    assert mode.key_location.read_bytes() == PEM and mode.jwks_export.is_symlink()


@pytest.mark.skipif(os.name != "posix", reason="POSIX resolves symlink parent before traversal of ..")
def test_export_checks_identity_through_symlink_parent_and_dotdot(tmp_path):
    nested = tmp_path / "elsewhere" / "nested"
    nested.mkdir(parents=True)
    mode = make_provided(nested.parent)
    link = tmp_path / "through"
    symlink_or_skip(link, nested)
    alias = link / ".." / mode.key_location.name
    assert alias.samefile(mode.key_location)
    with pytest.raises(LocalFailure, match="must not replace"):
        ExecutionTokenIssuer(ProvidedSigning(mode.key_location, alias))
    assert mode.key_location.read_bytes() == PEM


@pytest.mark.skipif(os.name != "posix", reason="POSIX resolves symlink parent before traversal of ..")
def test_staging_uses_actual_export_parent_through_symlink_and_dotdot(tmp_path, monkeypatch):
    nested = tmp_path / "elsewhere" / "nested"
    nested.mkdir(parents=True)
    link = tmp_path / "through"
    symlink_or_skip(link, nested)
    export = link / ".." / "public.json"
    mode = make_provided(tmp_path)
    real_replace = os.replace
    moves = []

    def record_move(source, target):
        moves.append((source.parent, target))
        real_replace(source, target)

    monkeypatch.setattr(_signing.os, "replace", record_move)
    ExecutionTokenIssuer(ProvidedSigning(mode.key_location, export))
    assert moves == [(nested.parent, nested.parent / export.name)]
    assert json.loads(export.read_bytes()) == EXPECTED
    assert not mode.jwks_export.exists()


def test_invalid_export_destinations_do_not_create_parents(tmp_path):
    mode = make_provided(tmp_path)
    absent = tmp_path / "absent"
    directory = tmp_path / "directory"
    directory.mkdir()
    for target in (directory, absent / "public.json", mode.key_location / "public.json"):
        with pytest.raises(LocalFailure, match=r"publish the public JWKS.*jwks_export.*file path.*directory"):
            ExecutionTokenIssuer(ProvidedSigning(mode.key_location, target))
    for location in (absent, mode.key_location):
        with pytest.raises(LocalFailure, match=r"key_directory.*existing.*directory"):
            ExecutionTokenIssuer(ColocatedSigning(location))
    assert set(tmp_path.iterdir()) == {mode.key_location, directory}
    assert mode.key_location.read_bytes() == PEM


@pytest.mark.parametrize("mode_name", ["colocated", "provided"])
@pytest.mark.parametrize("fail_at", ["create", "write", "replace", "unsupported-replace"])
def test_atomic_publication_failure_preserves_existing_files(tmp_path, monkeypatch, caplog, mode_name, fail_at):
    if mode_name == "colocated":
        mode = ColocatedSigning(tmp_path)
        export = tmp_path / "jwks.json"
        expected_files = {export}
    else:
        mode = make_provided(tmp_path)
        export = mode.jwks_export
        expected_files = {export, mode.key_location}
    export.write_bytes(PRIOR)
    real_temporary = _signing.NamedTemporaryFile
    parents = []
    moves = []

    @contextmanager
    def temporary(*args, **kwargs):
        parents.append(kwargs["dir"])
        if fail_at == "create":
            raise PermissionError(MARKER)
        with real_temporary(*args, **kwargs) as stream:
            if fail_at == "write":
                real_write = stream.write

                def fail_write(data):
                    real_write(data[:len(data) // 2])
                    raise PermissionError(MARKER)

                monkeypatch.setattr(stream, "write", fail_write)
            yield stream

    def replace(source, target):
        moves.append((source.parent, target, target.read_bytes(), json.loads(source.read_bytes())))
        raise OSError(errno.ENOTSUP if fail_at == "unsupported-replace" else errno.EACCES, MARKER)

    monkeypatch.setattr(_signing, "NamedTemporaryFile", temporary)
    monkeypatch.setattr(_signing.os, "replace", replace)
    field = "key_directory" if mode_name == "colocated" else "jwks_export"
    with pytest.raises(LocalFailure, match=rf"publish the public JWKS.*{field}.*atomic replacement") as error:
        ExecutionTokenIssuer(mode)
    assert_sanitized(error.value, caplog, MARKER)
    assert parents == [export.parent.resolve(strict=True)]
    assert len(moves) == int(fail_at in {"replace", "unsupported-replace"})
    for parent, target, previous, content in moves:
        assert parent == export.parent and target == export and previous == PRIOR
        [public] = content["keys"]
        assert set(public) == {"kty", "n", "e", "kid", "alg", "use"}
    assert export.read_bytes() == PRIOR and set(tmp_path.iterdir()) == expected_files
    if isinstance(mode, ProvidedSigning):
        assert mode.key_location.read_bytes() == PEM
