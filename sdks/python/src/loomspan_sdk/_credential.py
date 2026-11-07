from ._errors import LocalFailure


class CredentialSnapshot:
    """An opaque, already-issued execution credential."""

    __slots__ = ("__token",)

    def __init__(self, token: str) -> None:
        if type(token) is not str or not token or any(not 33 <= ord(ch) <= 126 for ch in token):
            raise LocalFailure("Invalid execution credential")
        self.__token = token

    def __repr__(self) -> str:
        return "CredentialSnapshot([redacted])"

    def __getstate__(self) -> None:
        raise TypeError("Execution credentials cannot be serialized")

    def _authorization(self) -> bytes:
        return b"Bearer " + self.__token.encode("ascii")

    def _redact(self, text: str) -> str:
        return text.replace(self.__token, "\N{REPLACEMENT CHARACTER}")
