import base64
import hashlib
import json
import os
import re
import stat
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Any, final

import asn1
from cryptography.exceptions import UnsupportedAlgorithm
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from pyasn1.codec.ber import decoder  # type: ignore[import-untyped]
from pyasn1.codec.der import encoder  # type: ignore[import-untyped]
from pyasn1.error import PyAsn1Error  # type: ignore[import-untyped]
from pyasn1.type import univ  # type: ignore[import-untyped]
from pyasn1_modules import rfc5958, rfc8017  # type: ignore[import-untyped]

from ._errors import LocalFailure

_PRIVATE_KEY_PEM = re.compile(
    rb"\s*-----BEGIN PRIVATE KEY-----[ \t]*[\r\n]([A-Za-z0-9+/= \t\r\n]+)[\r\n]"
    rb"-----END PRIVATE KEY-----\s*"
)
_RSA_ENCRYPTION = "1.2.840.113549.1.1.1"
_MINIMUM_RSA_BITS = 2048


@final
@dataclass(frozen=True, slots=True)
class ColocatedSigning:
    """Generate a process-local signer and publish jwks.json in this directory."""

    key_directory: Path

    def __post_init__(self) -> None:
        if not isinstance(self.key_directory, Path):
            raise LocalFailure("Colocated key directory must be a Path")


@final
@dataclass(frozen=True, slots=True)
class ProvidedSigning:
    """Load one operator-owned private key and publish its public JWKS."""

    key_location: Path
    jwks_export: Path

    def __post_init__(self) -> None:
        if not isinstance(self.key_location, Path) or not isinstance(self.jwks_export, Path):
            raise LocalFailure("Provided key location and JWKS export must be Paths")


def _base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _unsigned(value: int) -> str:
    return _base64url(value.to_bytes((value.bit_length() + 7) // 8, "big"))


def _sign_rs256(key: rsa.RSAPrivateKey, content: bytes) -> bytes:
    return key.sign(content, padding.PKCS1v15(), hashes.SHA256())


def _validate_ber_identifiers(data: bytes) -> None:
    # The structural decoder normalizes long-form tags and incomplete OIDs.
    # Check the original identifiers, including those in open attributes.
    stream = BytesIO(data)
    structure = asn1.Decoder(stream)
    tag = structure.peek()
    if tag is None:
        raise ValueError
    if (data[0] & 0x1F) == 0x1F and (tag.nr < 0x1F or data[1] == 0x80):
        raise ValueError
    if tag.cls == asn1.Classes.Universal and tag.nr in (asn1.Numbers.ObjectIdentifier, asn1.Numbers.RelativeOID):
        decoder.decode(data)
    if tag.typ == asn1.Types.Constructed:
        structure.enter()
        children = stream.read()
        while children and children != b"\x00\x00":
            _, remainder = decoder.decode(children, asn1Spec=univ.Any())
            _validate_ber_identifiers(children[:len(children) - len(remainder)])
            children = remainder


def _decode_complete(data: bytes, schema: Any) -> Any:
    # Check the full wire structure before typed decoding. The typed decoder
    # leaves open attribute values opaque and normalizes some malformed values.
    structure = asn1.Decoder(data)
    try:
        structure.read()
    except IndexError:
        # The BER decoder can index an empty INTEGER payload.
        raise ValueError from None
    if not structure.eof():
        raise ValueError
    _validate_ber_identifiers(data)
    value, remainder = decoder.decode(data, asn1Spec=schema)
    if remainder:
        raise ValueError
    return value


def _parse_private_key(material: bytes) -> rsa.RSAPrivateKey:
    match = _PRIVATE_KEY_PEM.fullmatch(material)
    if match is None:
        raise ValueError
    payload = match[1].translate(None, b" \t\r\n")
    if not payload or len(payload) % 4:
        raise ValueError
    encoded = base64.b64decode(payload, validate=True)
    info = _decode_complete(encoded, rfc5958.OneAsymmetricKey())
    algorithm = info["privateKeyAlgorithm"]
    version = int(info["version"])
    if version not in (0, 1) or str(algorithm["algorithm"]) != _RSA_ENCRYPTION:
        raise ValueError
    if algorithm["parameters"].isValue:
        _decode_complete(algorithm["parameters"].asOctets(), univ.Null())
    if info["attributes"].isValue:
        for attribute in info["attributes"]:
            if not attribute["attrValues"]:
                raise ValueError

    private = _decode_complete(info["privateKey"].asOctets(), rfc8017.RSAPrivateKey())
    key = serialization.load_der_private_key(encoder.encode(private), password=None)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < _MINIMUM_RSA_BITS:
        raise ValueError
    embedded = info["publicKey"]
    if embedded.isValue:
        if version != 1 or len(embedded) % 8:
            raise ValueError
        public = _decode_complete(embedded.asOctets(), rfc8017.RSAPublicKey())
        actual = key.public_key().public_numbers()
        if int(public["modulus"]) != actual.n or int(public["publicExponent"]) != actual.e:
            raise ValueError
    return key


def _publication_failure(destination: str) -> str:
    return (
        f"Unable to publish the public JWKS to {destination}. "
        "Use a file path in an existing directory that permits writing and atomic replacement."
    )


def _read_private_key(private_path: Path, export: Path) -> bytes:
    if Path(os.path.abspath(private_path)) == Path(os.path.abspath(export)):
        raise LocalFailure("JWKS export must not replace the signing input") from None
    message = _publication_failure("jwks_export")
    try:
        try:
            existing_export = export.stat()
        except FileNotFoundError:
            existing_export = None

        message = "key_location must point to a readable regular file. Check the path and the application's read access."
        try:
            # Use the unnormalized paths for actual traversal, including symlink parents.
            private_info = private_path.stat()
            if existing_export is not None and os.path.samestat(private_info, existing_export):
                raise LocalFailure("JWKS export must not replace the signing input") from None
            if stat.S_ISREG(private_info.st_mode):
                return private_path.read_bytes()
        except FileNotFoundError:
            message = "The private key file at key_location is missing. Provision the intended signing key file."
    except (OSError, ValueError):
        pass
    raise LocalFailure(message) from None


def _load_private_key(private_path: Path, export: Path) -> rsa.RSAPrivateKey:
    material = _read_private_key(private_path, export)
    try:
        return _parse_private_key(material)
    except (ValueError, asn1.Error, PyAsn1Error, UnsupportedAlgorithm, OverflowError, RecursionError):
        pass
    raise LocalFailure(
        "key_location must contain one unencrypted PKCS#8 PRIVATE KEY PEM block "
        f"for an RSA key of at least {_MINIMUM_RSA_BITS} bits. Check the provisioned key's format and size."
    ) from None


def _publish(export: Path, content: bytes) -> None:
    # tempfile normalizes its dir argument. Resolve it first so a symlink
    # followed by '..' stages in the destination's actual directory.
    parent = export.parent.resolve(strict=True)
    staging = None
    try:
        with NamedTemporaryFile(dir=parent, prefix=".jwks-", suffix=".tmp", delete=False) as output:
            staging = Path(output.name)
            output.write(content)
        os.replace(staging, parent / export.name)
        staging = None
    finally:
        if staging is not None:
            staging.unlink(missing_ok=True)


def _initialize_signing(signing: ColocatedSigning | ProvidedSigning) -> tuple[rsa.RSAPrivateKey, str]:
    failure = "Signing initialization failed. Check the signing configuration and SDK runtime."
    colocated = isinstance(signing, ColocatedSigning)
    field = "key_directory" if colocated else "key_location"
    destination = "jwks.json in key_directory" if colocated else "jwks_export"
    signing_failure = f"Unable to initialize RSA RS256 signing for {field}. Check the application's cryptographic runtime."
    try:
        if isinstance(signing, ColocatedSigning):
            directory = signing.key_directory.absolute()
            try:
                is_directory = directory.is_dir()
            except (OSError, ValueError):
                is_directory = False
            if not is_directory:
                raise LocalFailure(
                    "key_directory must point to an existing directory the application can access."
                ) from None
            export = directory / "jwks.json"
            failure = signing_failure
            key = rsa.generate_private_key(public_exponent=65537, key_size=_MINIMUM_RSA_BITS)
        else:
            private_path = signing.key_location.absolute()
            export = signing.jwks_export.absolute()
            key = _load_private_key(private_path, export)
            failure = signing_failure

        public = key.public_key()
        challenge = b"Loomspan signing key validation"
        public.verify(_sign_rs256(key, challenge), challenge, padding.PKCS1v15(), hashes.SHA256())
        numbers = public.public_numbers()
        public_jwk = {"e": _unsigned(numbers.e), "kty": "RSA", "n": _unsigned(numbers.n)}
        thumbprint_input = json.dumps(public_jwk, sort_keys=True, separators=(",", ":")).encode("ascii")
        kid = _base64url(hashlib.sha256(thumbprint_input).digest())
        public_jwk.update(kid=kid, alg="RS256", use="sig")
        result = key, kid
        content = json.dumps({"keys": [public_jwk]}).encode("ascii")
        failure = _publication_failure(destination)
        _publish(export, content)
        return result
    except LocalFailure:
        raise
    except Exception:
        # Crypto and parser diagnostics may contain private input.
        pass
    raise LocalFailure(failure) from None
