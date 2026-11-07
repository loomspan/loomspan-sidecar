import base64
import json
import logging
import pickle
from decimal import Decimal, localcontext
from uuid import UUID

import pytest
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from loomspan_sdk import (
    AuthenticatedIdentity,
    ColocatedSigning,
    CredentialSnapshot,
    ExecutionTokenIssuer,
    IssuanceSettings,
    LocalFailure,
    _issuance,
)

NOW = 1792324800_000_000_000
LAST_INSTANT = 31556889864403199_999_999_999


def unbase64(value):
    return base64.urlsafe_b64decode(value + b"=" * (-len(value) % 4))


def inspect(credential, jwks=None):
    header, payload, signature = credential._authorization().removeprefix(b"Bearer ").split(b".")
    headers = json.loads(unbase64(header))
    if jwks is not None:
        [jwk] = jwks["keys"]
        assert headers == {"alg": "RS256", "typ": "loomspan-execution+jwt", "kid": jwk["kid"]}
        public = rsa.RSAPublicNumbers(
            int.from_bytes(unbase64(jwk["e"].encode())), int.from_bytes(unbase64(jwk["n"].encode()))
        ).public_key()
        public.verify(unbase64(signature), header + b"." + payload, padding.PKCS1v15(), hashes.SHA256())
    raw = unbase64(payload)
    return json.loads(raw, parse_float=Decimal), raw


def test_issued_credential_has_default_profile(tmp_path, monkeypatch):
    monkeypatch.setattr(_issuance, "time_ns", lambda: NOW)
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path))
    credential = issuer.issue(AuthenticatedIdentity("alice", ["admin"]))

    assert isinstance(credential, CredentialSnapshot)
    claims, _ = inspect(credential, json.loads((tmp_path / "jwks.json").read_bytes()))
    assert claims == {
        "iss": "loomspan-app", "sub": "alice", "aud": ["loomspan-sidecar", "loomspan-app"],
        "roles": [], "token_use": "loomspan_execution", "iat": Decimal("1792324800.000000000"),
        "exp": Decimal("1792325700.000000000"), "jti": claims["jti"],
    }
    assert UUID(claims["jti"]).version == 4
    assert isinstance(claims["iat"], Decimal) and isinstance(claims["exp"], Decimal)


def test_identity_and_settings_are_immutable_snapshots_with_exact_roles(tmp_path):
    authorities = ["ORDER_EDITOR", "admin", "ROLE_ORDER_EDITOR", "ORDER_EDITOR", "ROLE_ORDER_READER", "\u00a0"]
    allowed = ["ROLE_ORDER_EDITOR", "ORDER_EDITOR", "ORDER_READER", "ORDER_EDITOR", "\u00a0"]
    extra = ["callbacks", "app", "loomspan-sidecar", "callbacks", "extra"]
    first = AuthenticatedIdentity(" alice ", authorities)
    second = AuthenticatedIdentity("bob", ["ROLE_ORDER_EDITOR"])
    settings = IssuanceSettings(issuer="app", roles=allowed, additional_audiences=extra)
    authorities[:] = ["ORDER_READER"]
    allowed[:] = ["admin"]
    extra[:] = ["changed"]
    with pytest.raises(AttributeError):
        first.subject = "changed"
    with pytest.raises(AttributeError):
        settings.roles = ()

    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path), settings)
    jwks = json.loads((tmp_path / "jwks.json").read_bytes())
    results = [inspect(issuer.issue(identity), jwks)[0] for identity in (
        first, second, AuthenticatedIdentity("charlie"), first,
    )]
    assert [(item["sub"], item["roles"]) for item in results] == [
        (" alice ", ["ORDER_EDITOR", "ROLE_ORDER_EDITOR", "\u00a0"]),
        ("bob", ["ROLE_ORDER_EDITOR"]),
        ("charlie", []),
        (" alice ", ["ORDER_EDITOR", "ROLE_ORDER_EDITOR", "\u00a0"]),
    ]
    assert all(item["aud"] == ["loomspan-sidecar", "app", "callbacks", "extra"] for item in results)
    assert len({item["jti"] for item in results}) == len(results)


def test_identical_issuer_and_sidecar_audience_is_still_an_array(tmp_path):
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path), IssuanceSettings(issuer="loomspan-sidecar"))
    claims, _ = inspect(issuer.issue(AuthenticatedIdentity("alice")))
    assert claims["aud"] == ["loomspan-sidecar"]


@pytest.mark.parametrize("name", ["", " \t", "\u2003", 12, None])
def test_rejects_blank_and_non_string_names(name):
    for build in (
        lambda: AuthenticatedIdentity(name),
        lambda: AuthenticatedIdentity("alice", [name]),
        lambda: IssuanceSettings(issuer=name),
        lambda: IssuanceSettings(roles=[name]),
        lambda: IssuanceSettings(additional_audiences=[name]),
    ):
        with pytest.raises(LocalFailure, match="nonblank string"):
            build()


@pytest.mark.parametrize("names", ["admin", b"", {"admin": True}, None])
def test_requires_name_collections(names):
    for build in (
        lambda: AuthenticatedIdentity("alice", names),
        lambda: IssuanceSettings(roles=names),
        lambda: IssuanceSettings(additional_audiences=names),
    ):
        with pytest.raises(LocalFailure, match="collection of strings"):
            build()


def test_issuer_requires_explicit_trusted_identity(tmp_path):
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path))
    with pytest.raises(LocalFailure, match="authenticated identity"):
        issuer.issue({"subject": "alice", "roles": ["admin"]})


@pytest.mark.parametrize(
    ("now", "duration", "issued", "expires"),
    [
        (NOW, 1, "1792324800.000000000", "1792324800.000000001"),
        (NOW, 123456789, "1792324800.000000000", "1792324800.123456789"),
        (-1123456789, 250_000_000, "-1.123456789", "-0.873456789"),
        (LAST_INSTANT - 1, 1, "31556889864403199.999999998", "31556889864403199.999999999"),
    ],
)
def test_exact_numeric_dates_with_one_clock_sample_per_operation(tmp_path, monkeypatch, now, duration, issued, expires):
    times = iter([now, now])
    monkeypatch.setattr(_issuance, "time_ns", lambda: next(times))
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path), IssuanceSettings(lifetime_ns=duration))
    with localcontext(prec=3):
        claims, raw = inspect(issuer.issue(AuthenticatedIdentity("alice")))
    assert next(times, None) is None
    assert claims["iat"] == Decimal(issued) and claims["exp"] == Decimal(expires)
    assert f'"iat":{issued},'.encode() in raw and f'"exp":{expires},'.encode() in raw
    assert claims["exp"] - claims["iat"] == Decimal(f"{duration}e-9")


@pytest.mark.parametrize("lifetime", [0, -1, 1.0, True, "1", Decimal(1), None])
def test_lifetime_must_be_positive_integer_nanoseconds(lifetime):
    with pytest.raises(LocalFailure, match="positive integer"):
        IssuanceSettings(lifetime_ns=lifetime)


@pytest.mark.parametrize("lifetime, warnings", [(3600_000_000_000, 0), (3600_000_000_001, 1), (10**20, 1)])
def test_long_lifetime_warns_once_above_one_hour(tmp_path, caplog, lifetime, warnings):
    caplog.set_level(logging.WARNING, logger=_issuance.__name__)
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path), IssuanceSettings(lifetime_ns=lifetime))
    for _ in range(2):
        issuer.issue(AuthenticatedIdentity("alice"))
    assert caplog.messages == [
        "Execution credential lifetime exceeds one hour. Roles remain snapshots until expiry."
    ] * warnings


@pytest.mark.parametrize("bad_time", [LAST_INSTANT, LAST_INSTANT + 1, -31557014167219200_000_000_001])
def test_overflow_fails_at_startup_and_issue(tmp_path, monkeypatch, bad_time):
    export = tmp_path / "jwks.json"
    export.write_bytes(b"prior public export")
    settings = IssuanceSettings(lifetime_ns=1)
    monkeypatch.setattr(_issuance, "time_ns", lambda: bad_time)
    with pytest.raises(LocalFailure, match="representable range"):
        ExecutionTokenIssuer(ColocatedSigning(tmp_path), settings)
    assert export.read_bytes() == b"prior public export"

    monkeypatch.setattr(_issuance, "time_ns", lambda: LAST_INSTANT - 1)
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path), settings)
    monkeypatch.setattr(_issuance, "time_ns", lambda: bad_time)
    with pytest.raises(LocalFailure, match="representable range"):
        issuer.issue(AuthenticatedIdentity("alice"))


def test_secret_outputs_and_signing_failures_are_redacted(tmp_path, monkeypatch, caplog):
    issuer = ExecutionTokenIssuer(ColocatedSigning(tmp_path))
    identity = AuthenticatedIdentity("alice")
    credential = issuer.issue(identity)
    token = credential._authorization().removeprefix(b"Bearer ").decode()
    assert token not in repr(credential) + str(credential) + caplog.text
    assert "PRIVATE KEY" not in repr(issuer) + str(issuer)
    for secret in (issuer, credential):
        for serialize in (pickle.dumps, json.dumps):
            with pytest.raises(TypeError) as error:
                serialize(secret)
            assert token not in str(error.value)

    def fail_sign(*args):
        raise ValueError("synthetic-private-marker")

    monkeypatch.setattr(_issuance, "_sign_rs256", fail_sign)
    with pytest.raises(LocalFailure, match="Execution credential issuance failed") as error:
        issuer.issue(identity)
    assert error.value.__cause__ is None and error.value.__context__ is None
    assert "synthetic-private-marker" not in str(error.value) + repr(error.value) + caplog.text
