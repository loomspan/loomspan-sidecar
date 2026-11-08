import base64
import json
import traceback
from dataclasses import asdict
from decimal import localcontext
from pathlib import Path

import anyio
import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from loomspan_sdk import (
    AuthenticatedIdentity,
    CallbackVerifier,
    ColocatedSigning,
    ExecutionTokenIssuer,
    InvalidCallbackCredential,
    IssuanceSettings,
    LocalFailure,
    ProvidedSigning,
    _callback,
    _issuance,
)

NOW = 1792324800_123_456_789
SYNTHETIC_PEM = Path(__file__).resolve().parents[2] / "java/core/src/test/resources/signing/synthetic-rsa.pem"
ABSENT = object()


def base64url(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=")


def sign(private, header, claims):
    def segment(value):
        return base64url(value if isinstance(value, bytes) else json.dumps(value).encode("ascii"))

    message = b".".join((segment(header), segment(claims)))
    signature = private.sign(message, padding.PKCS1v15(), hashes.SHA256())
    return (message + b"." + base64url(signature)).decode("ascii")


@pytest.fixture
def profile(tmp_path, monkeypatch):
    export = tmp_path / "own.json"
    issuer = ExecutionTokenIssuer(ProvidedSigning(SYNTHETIC_PEM, export))
    [jwk] = json.loads(export.read_bytes())["keys"]
    key = serialization.load_pem_private_key(SYNTHETIC_PEM.read_bytes(), password=None)
    monkeypatch.setattr(_callback, "time_ns", lambda: NOW)
    return (
        issuer, key,
        {"alg": "RS256", "typ": "loomspan-execution+jwt", "kid": jwk["kid"]},
        {"iss": "loomspan-app", "sub": "alice", "token_use": "loomspan_execution", "aud": "loomspan-app",
         "roles": [], "exp": 1792324801},
    )


async def assert_invalid(verifier, token):
    with pytest.raises(InvalidCallbackCredential) as caught:
        await verifier.verify(token)
    error = caught.value
    assert str(error) == "Invalid callback execution credential"
    assert error.__cause__ is None and error.__context__ is None
    assert "private-test-marker" not in "".join(traceback.format_exception(error))


def test_verifies_own_issued_credential(tmp_path, monkeypatch):
    monkeypatch.setattr(_issuance, "time_ns", lambda: NOW)
    monkeypatch.setattr(_callback, "time_ns", lambda: NOW)
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path), IssuanceSettings(
        roles=["ORDER_EDITOR", "ROLE_ORDER_EDITOR"], additional_audiences=["callbacks"],
    ))
    token = issuer.issue(AuthenticatedIdentity(
        " alice ", ["ROLE_ORDER_EDITOR", "admin", "ORDER_EDITOR"],
    ))._authorization().removeprefix(b"Bearer ").decode("ascii")

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            principal = await verifier.verify(token)
        assert principal.authorities == ("ROLE_ORDER_EDITOR", "ORDER_EDITOR")
        assert json.loads(json.dumps(asdict(principal))) == {
            "issuer": "loomspan-app", "subject": " alice ", "authorities": list(principal.authorities),
        }
        with pytest.raises(AttributeError):
            principal.subject = "someone else"

    anyio.run(verify)


def test_explicit_audience_and_roles_without_other_claim_policy(profile):
    issuer, key, header, claims = profile
    claims.update(
        roles=[" EDITOR ", "ROLE_EDITOR", " EDITOR ", "\u00a0"],
        scope="admin", jti="", iat=1792324900, exp=1792324901,
    )
    header.update(b64=True, crit=["b64"], jku="https://unused.invalid/jwks")

    async def verify():
        async with CallbackVerifier.open(issuer, audience="receiver") as verifier:
            for audience in ("receiver", ["", "unused", "receiver"]):
                token = sign(key, header, dict(claims, aud=audience))
                principal = await verifier.verify(token)
                assert (principal.issuer, principal.subject, principal.authorities) == (
                    "loomspan-app", "alice", (" EDITOR ", "ROLE_EDITOR", " EDITOR ", "\u00a0"),
                )
            await assert_invalid(verifier, sign(key, header, claims))

    anyio.run(verify)


@pytest.mark.parametrize(("field", "value"), [
    ("alg", ABSENT), ("alg", "HS256"), ("alg", "none"), ("typ", ABSENT), ("typ", "JWT"),
    ("kid", ABSENT), ("kid", " \t"), ("kid", 1), ("kid", "unknown"),
    ("crit", []), ("crit", ["private-test-marker"]), ("crit", ["b64"]), ("crit", "b64"),
    ("b64", False), ("b64", 1), ("b64", None),
])
def test_rejects_invalid_protected_headers(profile, field, value):
    issuer, key, header, claims = profile
    if value is ABSENT:
        del header[field]
    else:
        header[field] = value

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            await assert_invalid(verifier, sign(key, header, claims))

    anyio.run(verify)


@pytest.mark.parametrize(("field", "value"), [
    ("iss", ABSENT), ("iss", "private-test-marker"), ("iss", 1), ("sub", ABSENT), ("sub", "\u2003"),
    ("sub", None), ("sub", 1), ("token_use", ABSENT), ("token_use", "management"),
    ("aud", ABSENT), ("aud", []), ("aud", ""), ("aud", "wrong"), ("aud", ["wrong"]),
    ("aud", ["loomspan-app", None]), ("roles", ABSENT), ("roles", "admin"),
    ("roles", [" "]), ("roles", [True]), ("roles", {"admin": True}),
    ("exp", ABSENT), ("jti", None), ("jti", 1),
])
def test_rejects_invalid_claim_shapes(profile, field, value):
    issuer, key, header, claims = profile
    if value is ABSENT:
        del claims[field]
    else:
        claims[field] = value

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            await assert_invalid(verifier, sign(key, header, claims))

    anyio.run(verify)


def test_rejects_malformed_wire_and_does_not_trust_embedded_keys(profile):
    issuer, key, header, claims = profile
    valid = sign(key, header, claims)
    protected, payload, signature = valid.split(".")
    other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    numbers = other.public_key().public_numbers()
    embedded = {
        "kty": "RSA", "kid": header["kid"],
        "n": base64url(numbers.n.to_bytes(256)).decode(), "e": "AQAB",
    }
    tokens = (
        123, "private-test-marker", "Bearer " + valid, valid + ".", f"{protected}.*.{signature}",
        f"{protected}.{payload}.{signature}=", f"{protected}.{payload}.A",
        f"{protected}.{payload}.{'B' if signature[0] == 'A' else 'A'}{signature[1:]}",
        sign(key, b"\xff", claims), sign(key, header, b"\xff"), sign(key, header, b"[]"),
        sign(key, header, b'{"exp":1,"exp":2}'),
        sign(key, json.dumps(header).encode()[:-1] + b',"alg":"RS256"}', claims),
        sign(key, header, json.dumps(claims).encode() + b"{}"),
        sign(other, dict(header, jwk=embedded), claims),
    )

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            assert (await verifier.verify(valid)).authorities == ()
            for token in tokens:
                await assert_invalid(verifier, token)

    anyio.run(verify)


@pytest.mark.parametrize(("now", "dates", "accepted"), [
    (NOW, '{"exp":1792324740.123456789}', True),
    (NOW, '{"exp":1792324740.123456788}', False),
    (NOW, '{"exp":1792324740.1234567891}', True),
    (NOW, '{"exp":1792324740.1234567889}', False),
    (NOW, '{"exp":1792324801,"nbf":1792324860.123456789}', True),
    (NOW, '{"exp":1792324801,"nbf":1792324860.123456790}', False),
    (NOW, '{"exp":1792324801,"nbf":1792324860.1234567899}', True),
    (NOW, '{"exp":1792324800.123456790,"iat":1792324800.123456789}', True),
    (NOW, '{"exp":1792324800.123456789,"iat":1792324800.123456789}', False),
    (NOW, '{"exp":1792324800.123456788,"iat":1792324800.123456789}', False),
    (59_999_999_999, '{"exp":-0.0000000001}', True),
    (59_999_999_999, '{"exp":-0.0000000011}', False),
    (NOW, '{"exp":31556889864403199.999999999,"iat":-31557014167219200}', True),
    (NOW, '{"exp":31556889864403200}', False),
    (NOW, '{"exp":1792324801,"iat":-31557014167219200.000000001}', False),
    (NOW, '{"exp":1792324801,"nbf":-31557014167219200.000000001}', False),
])
def test_exact_timestamp_comparisons(profile, monkeypatch, now, dates, accepted):
    issuer, key, header, claims = profile
    monkeypatch.setattr(_callback, "time_ns", lambda: now)
    del claims["exp"]
    payload = (json.dumps(claims)[:-1] + "," + dates[1:]).encode("ascii")

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            token = sign(key, header, payload)
            if accepted:
                assert (await verifier.verify(token)).subject == "alice"
            else:
                await assert_invalid(verifier, token)

    with localcontext(prec=3):
        anyio.run(verify)


@pytest.mark.parametrize("name", ["exp", "iat", "nbf"])
@pytest.mark.parametrize("number", ['true', 'null', '"1792324801"', 'NaN', 'Infinity', '-Infinity', '1e10000'])
def test_invalid_numeric_date_types_and_overflow(profile, name, number):
    issuer, key, header, claims = profile
    claims[name] = "replace-number"
    payload = json.dumps(claims).replace('"replace-number"', number).encode("ascii")

    async def verify():
        async with CallbackVerifier.open(issuer) as verifier:
            await assert_invalid(verifier, sign(key, header, payload))

    anyio.run(verify)


def test_invalid_callback_settings_fail_safely(profile):
    issuer, *_ = profile

    async def verify():
        for settings in ({"audience": " "}, {"audience": 2}, {"overlap_jwks": "private-test-marker"}):
            with pytest.raises(LocalFailure, match="^Callback (audience|overlap JWKS) must") as caught:
                async with CallbackVerifier.open(issuer, **settings):
                    pytest.fail("invalid settings initialized")
            assert "private-test-marker" not in str(caught.value)

    anyio.run(verify)
