import json
import pickle
import traceback
from decimal import Decimal
from inspect import signature
from uuid import UUID, uuid4

import pytest

from loomspan_sdk import (
    AcceptanceUnknown,
    CredentialSnapshot,
    ExecutionEvent,
    ExecutionStatus,
    HttpFailure,
    LocalFailure,
    ProtocolFailure,
    SidecarClient,
    WaitOptions,
)
from loomspan_sdk._protocol import (
    _Response,
    decode_json,
    decode_response,
    encode_input,
    parse_acceptance,
    parse_catalog,
    parse_origin,
    parse_skill,
    parse_snapshot,
    skill_segment,
)

EXECUTION_ID = UUID("ddecb932-3e77-4947-82df-64e4ecc148fa")
CONFIG_ID = UUID("5b42f91d-643a-48f5-8a31-46dfbd264f56")
CREATED = "2026-11-07T10:00:00.123456789Z"
CREDENTIAL = CredentialSnapshot("synthetic-credential")


def descriptor():
    return {
        "name": "default-echo",
        "description": "",
        "kind": "HTTP",
        "inputSchema": '{ "type": "object" }',
        "outputSchema": '{"type":"string"}',
        "extension": {"future": True},
    }


def snapshot(status="QUEUED"):
    value = {
        "id": str(EXECUTION_ID).upper(),
        "skillName": "default-echo",
        "status": status,
        "createdAt": CREATED,
    }
    if status in ("COMPLETED", "FAILED"):
        value["completedAt"] = "2026-11-07T10:00:01Z"
    if status == "FAILED":
        value["failure"] = {"kind": "VALIDATION", "message": "Invalid input", "issues": [
            {"path": "$.text", "code": "required", "message": ""},
            {},
        ]}
    return value


def test_public_api_and_defaults() -> None:
    credential = CredentialSnapshot("synthetic-credential")
    assert "synthetic-credential" not in repr(credential)
    assert WaitOptions().timeout == 60.0
    assert WaitOptions().poll_interval == 0.25
    options = signature(SidecarClient).parameters
    assert options["connect_timeout"].default == 2.0
    assert options["response_timeout"].default == 10.0
    assert options["max_response_size"].default == 8 * 1024 * 1024


@pytest.mark.parametrize("token", [None, b"abc", "", "a b", "\t", "\n", "\x00", "\x7f", "café"])
def test_reject_bad_credentials(token) -> None:
    with pytest.raises(LocalFailure) as error:
        CredentialSnapshot(token)
    assert error.value.__context__ is None


def test_credential_is_opaque_and_not_serializable() -> None:
    secret = "synthetic-token._~+/"
    credential = CredentialSnapshot(secret)
    assert secret not in str(credential) + repr(credential)
    assert not hasattr(credential, "__dict__")
    for protocol in range(pickle.HIGHEST_PROTOCOL + 1):
        with pytest.raises(TypeError, match="serialized|pickle") as error:
            pickle.dumps(credential, protocol=protocol)
        assert secret not in str(error.value)
    with pytest.raises(TypeError) as error:
        json.dumps(credential)
    assert secret not in str(error.value)


@pytest.mark.parametrize("base", [
    "", None, "/relative", "ftp://example.com", "https:///path", "http://",
    "http://name@example.com", "http://example.com?", "http://example.com#",
    "http://example.com?x=y", "http://example.com#path", "http://example.com\\path",
    "http://example.com:0", "http://example.com:65536", "http://example.com:",
    "http://example.com:port", "http://[::1]bad", "http://[::broken]/",
    "http://[::1%foo@bar]", "http://[::1%foo:bar]",
    "http://example..com", "http://-example.com", "http://999.999.999.999",
    " http://example.com", "http://example.com/\n", "http://example.com/a b",
    "http://example.com/%x1", "http://example.com/\ud800",
])
def test_invalid_base_urls(base) -> None:
    with pytest.raises(LocalFailure) as error:
        SidecarClient(base)
    assert error.value.__context__ is None


def test_base_and_skill_path_encoding() -> None:
    origin = parse_origin("HTTPS://Example.com:444/api/%2f/café///")
    assert (origin.scheme, origin.hostname, origin.port) == ("https", "example.com", 444)
    assert origin.base_path == "/api/%2f/caf%C3%A9"
    assert parse_origin("http://[::1]:8080").hostname == "::1"
    assert parse_origin("http://[fe80::1%25LocalZone]:8080").hostname == "fe80::1%LocalZone"
    assert skill_segment("café ?#:@") == "caf%C3%A9%20%3F%23%3A%40"
    assert skill_segment("\N{NO-BREAK SPACE}") == "%C2%A0"


@pytest.mark.parametrize("name", [
    None, "", " ", "\u2003", "\t", ".", "..", "a/b", "a\\b", "%2e", "a\x00b", "a\x85b", "\ud800",
])
def test_invalid_skill_names(name) -> None:
    with pytest.raises(LocalFailure) as error:
        skill_segment(name)
    assert error.value.__context__ is None


@pytest.mark.parametrize("value", [None, True, 0, -1, float("nan"), float("inf"), 10**300])
def test_invalid_limits(value) -> None:
    for name in ("connect_timeout", "response_timeout", "max_response_size"):
        with pytest.raises(LocalFailure):
            SidecarClient("http://localhost", **{name: value})
    for name in ("timeout", "poll_interval"):
        with pytest.raises(LocalFailure):
            WaitOptions(**{name: value})


@pytest.mark.parametrize("value", [
    None, [], "{}", 3, {1: "coercion"}, {"nested": [{False: "coercion"}]},
    {"n": float("nan")}, {"n": float("inf")}, {"n": object()}, {"n": (1, 2)},
])
def test_invalid_inputs(value) -> None:
    with pytest.raises(LocalFailure) as error:
        encode_input(value)
    assert error.value.__context__ is None


def test_json_object_input() -> None:
    value = {"café": [{"value": True}, 13.5, None, "日本語"]}
    assert json.loads(encode_input(value)) == value
    cycle = {}
    cycle["self"] = cycle
    with pytest.raises(LocalFailure):
        encode_input(cycle)


@pytest.mark.parametrize("body", [
    b'{"x":1,"x":2}', b'{"unknown":[{"x":1,"x":2}]}', b'{} {}', b'{}null',
    b'{"n":NaN}', b'{"n":Infinity}', b'{"n":-Infinity}', b'"\xff"',
])
def test_malformed_json(body) -> None:
    with pytest.raises(ValueError):
        decode_json(body)


def test_catalog_and_acceptance_model() -> None:
    (skill,) = parse_catalog([descriptor()])
    assert (skill.name, skill.description, skill.kind, skill.input_schema, skill.output_schema) == (
        "default-echo", "", "HTTP", '{ "type": "object" }', '{"type":"string"}'
    )
    assert parse_catalog([]) == ()
    assert parse_skill({k: v for k, v in descriptor().items() if k != "description"}).description is None
    assert parse_acceptance({"id": str(EXECUTION_ID).upper(), "future": True}).id == EXECUTION_ID
    for value in ({}, [], {"id": "0-0-0-0-0"}, {"id": 17}, {"id": str(EXECUTION_ID).replace("-", "")}):
        with pytest.raises(ValueError):
            parse_acceptance(value)
    with pytest.raises(ValueError):
        parse_catalog(descriptor())


def test_large_valid_json_number_does_not_invalidate_an_envelope() -> None:
    body = ('{"id":"%s","future":1e999}' % EXECUTION_ID).encode()
    value = decode_json(body)
    assert parse_acceptance(value).id == EXECUTION_ID
    assert isinstance(value, dict) and value["future"] == Decimal("1e999")


@pytest.mark.parametrize(("field", "invalid"), [
    ("name", None), ("name", " "), ("kind", None), ("kind", 13),
    ("inputSchema", None), ("inputSchema", {}), ("description", 1), ("outputSchema", []),
])
def test_descriptor_known_fields_must_be_valid(field, invalid) -> None:
    with pytest.raises(ValueError):
        parse_skill(descriptor() | {field: invalid})


@pytest.mark.parametrize("result", [None, "", " \n[broken 日本語", '{"text": "exact"}', "arbitrary text"])
def test_status_result_failure_and_event_fidelity(result) -> None:
    details = {"nested": [{"value": 7}]}
    value = snapshot("FAILED") | {
        "result": result,
        "configurationSnapshotId": str(CONFIG_ID),
        "events": [
            {"timestamp": CREATED, "level": "INFO", "type": "FINISHED", "details": details,
             "frameId": "frame-1", "route": "r1", "added": False},
            {"timestamp": CREATED, "level": "DEBUG", "type": "ENTRY", "details": {}},
        ],
        "extra": False,
    }
    current = parse_snapshot(value, EXECUTION_ID)
    assert current.id == EXECUTION_ID and current.skill_name == "default-echo"
    assert current.status is ExecutionStatus.FAILED and current.status.terminal
    assert current.created_at == CREATED and current.completed_at == value["completedAt"]
    assert current.result == result and current.configuration_snapshot_id == CONFIG_ID
    assert current.failure is not None
    assert (current.failure.kind, current.failure.message) == ("VALIDATION", "Invalid input")
    first_issue, second_issue = current.failure.issues
    assert (first_issue.path, first_issue.code, first_issue.message) == ("$.text", "required", "")
    assert (second_issue.path, second_issue.code, second_issue.message) == (None, None, None)
    first_event, second_event = current.events
    assert (first_event.timestamp, first_event.level, first_event.type) == (CREATED, "INFO", "FINISHED")
    assert (first_event.frame_id, first_event.route, second_event.frame_id, second_event.route) == (
        "frame-1", "r1", None, None
    )
    details["nested"][0]["value"] = 100
    first_event.details["nested"][0]["value"] = 200
    assert first_event.details == {"nested": [{"value": 7}]}


def test_optional_diagnostics() -> None:
    first = parse_snapshot(snapshot(), EXECUTION_ID)
    assert first.events is None and first.completed_at is None and first.configuration_snapshot_id is None
    assert not first.status.terminal
    assert parse_snapshot(snapshot() | {"events": None}, EXECUTION_ID).events is None
    assert parse_snapshot(snapshot() | {"events": []}, EXECUTION_ID).events == ()
    for extra in ({}, {"issues": None}, {"issues": []}):
        failure = parse_snapshot(
            snapshot("FAILED") | {"failure": {"kind": "ERROR", **extra}}, EXECUTION_ID
        ).failure
        assert failure.message is None and failure.issues == ()


@pytest.mark.parametrize(("field", "invalid"), [
    ("id", None), ("id", str(uuid4())), ("skillName", "\u2003"), ("skillName", {}),
    ("status", "pending"), ("createdAt", "2026-02-30T12:01:00Z"), ("createdAt", 0),
    ("createdAt", "-0000-01-01T00:00:00Z"),
    ("completedAt", ""), ("result", {"unexpected": "object"}), ("configurationSnapshotId", "uuid"),
    ("failure", []), ("failure", {}), ("failure", {"kind": "X", "message": 5}),
    ("failure", {"kind": "X", "issues": {}}), ("failure", {"kind": "X", "issues": [None]}),
    ("failure", {"kind": "X", "issues": [{"path": []}]}), ("events", {}), ("events", [None]),
    ("events", [{"level": "INFO", "type": "X", "details": {}, "timestamp": "today"}]),
    ("events", [{"timestamp": CREATED, "level": "", "type": "X", "details": {}}]),
    ("events", [{"timestamp": CREATED, "level": "INFO", "details": {}}]),
    ("events", [{"timestamp": CREATED, "level": "INFO", "type": "X", "details": []}]),
    ("events", [{"timestamp": CREATED, "level": "INFO", "type": "X", "details": {}, "route": 0}]),
    ("events", [{"timestamp": CREATED, "level": "INFO", "type": "X", "details": {}, "frameId": 0}]),
])
def test_status_rejects_invalid_known_fields(field, invalid) -> None:
    with pytest.raises(ValueError):
        parse_snapshot(snapshot() | {field: invalid}, EXECUTION_ID)


def test_required_terminal_fields() -> None:
    with pytest.raises(ValueError):
        parse_snapshot(snapshot("COMPLETED") | {"completedAt": None}, EXECUTION_ID)
    with pytest.raises(ValueError):
        parse_snapshot(snapshot("FAILED") | {"failure": None}, EXECUTION_ID)


@pytest.mark.parametrize("timestamp", [
    "+10000-02-29T12:00:00.000000001Z",
    "2026-11-07T15:00:00.1+04:30",
    "2000-02-29T24:00:00.000Z",
    "2016-12-31T23:59:60Z",
])
def test_timestamp_validation_preserves_original_text(timestamp) -> None:
    assert parse_snapshot(snapshot() | {"createdAt": timestamp}, EXECUTION_ID).created_at == timestamp


def test_public_event_copies_mutable_input() -> None:
    details = {"values": [1, {"value": 2}]}
    event = ExecutionEvent(CREATED, "INFO", "TEST", details)
    details["values"][1]["value"] = 9
    assert event.details == {"values": [1, {"value": 2}]}


def test_http_problem_is_redacted_defensive_and_safe() -> None:
    secret = "a-distinct-synthetic-token"
    credential = CredentialSnapshot(secret)
    body = json.dumps({secret: [f"bearer {secret}", {"text": f"x{secret}y"}]}).encode()
    with pytest.raises(HttpFailure) as raised:
        decode_response(_Response(503, body), parse_catalog, credential, execution_id=EXECUTION_ID)
    error = raised.value
    assert error.status_code == 503 and error.execution_id == EXECUTION_ID
    assert error.last_snapshot is None and error.__context__ is None and error.__cause__ is None
    assert secret not in "".join(traceback.format_exception(error)) + repr(error)
    problem = error.problem
    assert secret not in json.dumps(problem)
    assert len(problem) == 1
    problem.clear()
    assert len(error.problem) == 1


@pytest.mark.parametrize("body", [
    None, b'not json synthetic-credential', b'[]', b'{} {}', b'{"a":0,"a":1}',
    '{"synthetic-credential": 1, "�": 2}'.encode(),
])
def test_unusable_http_problem_is_absent(body) -> None:
    with pytest.raises(HttpFailure) as raised:
        decode_response(_Response(401, body), parse_catalog, CREDENTIAL)
    assert raised.value.status_code == 401 and raised.value.problem is None
    assert raised.value.__context__ is None


def test_parser_errors_never_escape_the_sdk_exception_chain() -> None:
    body = b'{"id":"synthetic-credential"} {}'
    for status, submission, expected in ((200, False, ProtocolFailure), (202, True, AcceptanceUnknown)):
        with pytest.raises(expected) as raised:
            decode_response(_Response(status, body), parse_acceptance, CREDENTIAL, submission=submission)
        assert raised.value.__context__ is None and raised.value.__cause__ is None
        assert "synthetic-credential" not in "".join(traceback.format_exception(raised.value))
