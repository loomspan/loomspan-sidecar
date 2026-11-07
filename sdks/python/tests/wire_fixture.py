"""Bounded loopback fixtures for real HTTP framing, TLS, and cleanup."""

import json
import socket
import ssl
from collections.abc import Callable
from dataclasses import dataclass
from threading import Event, Lock, Thread


@dataclass(frozen=True, slots=True)
class Request:
    method: str
    target: str
    headers: dict[str, str]
    body: bytes


class WirePeer:
    def __init__(
        self,
        handle: Callable[[socket.socket, Request, int], None] | None = None,
        *,
        raw_handle: Callable[[socket.socket], None] | None = None,
        tls: ssl.SSLContext | None = None,
        receive_buffer: int | None = None,
    ) -> None:
        self._listener = socket.socket()
        if receive_buffer is not None:
            self._listener.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, receive_buffer)
        self._listener.bind(("127.0.0.1", 0))
        self._listener.listen()
        self._listener.settimeout(0.05)
        self.port = self._listener.getsockname()[1]
        self.url = f"{'https' if tls is not None else 'http'}://127.0.0.1:{self.port}"
        self._handle = handle
        self._raw_handle = raw_handle
        self._tls = tls
        self._lock = Lock()
        self._stop = Event()
        self._errors: list[Exception] = []
        self._workers: list[Thread] = []
        self._thread = Thread(target=self._accept, daemon=True)
        self.requests: list[Request] = []
        self.connections = 0

    def __enter__(self):
        self._thread.start()
        return self

    def __exit__(self, exc_type, exc, tb):
        self._stop.set()
        self._thread.join(5)
        self._listener.close()
        assert not self._thread.is_alive(), "Loopback listener did not stop"
        for worker in self._workers:
            worker.join(5)
            assert not worker.is_alive(), "Loopback connection did not stop"
        if self._errors:
            raise ExceptionGroup("Loopback fixture errors", self._errors)

    def _accept(self) -> None:
        try:
            while not self._stop.is_set():
                try:
                    conn, _ = self._listener.accept()
                except TimeoutError:
                    continue
                self.connections += 1
                worker = Thread(target=self._serve, args=(conn,), daemon=True)
                self._workers.append(worker)
                worker.start()
        except Exception as error:
            self._errors.append(error)

    def _serve(self, conn: socket.socket) -> None:
        try:
            with conn:
                conn.settimeout(4)
                if self._raw_handle is not None:
                    self._raw_handle(conn)
                elif self._tls is not None:
                    try:
                        wrapped = self._tls.wrap_socket(conn, server_side=True)
                    except (ssl.SSLError, ConnectionResetError):
                        # A rejected handshake may reach the peer as a TLS alert or a TCP reset.
                        return
                    with wrapped:
                        self._serve_http(wrapped)
                else:
                    self._serve_http(conn)
        except Exception as error:
            with self._lock:
                self._errors.append(error)

    def _serve_http(self, conn: socket.socket) -> None:
        with conn.makefile("rb") as stream:
            line = stream.readline(16 * 1024)
            if not line:
                return
            method, target, version = line.decode("ascii").split()
            assert version == "HTTP/1.1"
            headers = {}
            while (line := stream.readline(16 * 1024)) != b"\r\n":
                name, value = line.decode("ascii").split(":", 1)
                headers[name.lower()] = value.strip()
            length = int(headers.get("content-length", "0"))
            body = stream.read(length)
            assert body is not None and len(body) == length
        request = Request(method, target, headers, body)
        with self._lock:
            index = len(self.requests)
            self.requests.append(request)
        assert self._handle is not None
        self._handle(conn, request, index)


def reply(
    conn: socket.socket,
    status: int,
    body: bytes,
    *,
    headers: tuple[tuple[str, str], ...] = (),
) -> None:
    extra = "".join(f"{name}: {value}\r\n" for name, value in headers)
    conn.sendall(
        f"HTTP/1.1 {status} Test\r\nConnection: close\r\n"
        f"Content-Length: {len(body)}\r\n{extra}\r\n".encode("ascii") + body
    )


def reply_json(
    conn: socket.socket,
    status: int,
    value: object,
    *,
    headers: tuple[tuple[str, str], ...] = (),
) -> None:
    reply(conn, status, json.dumps(value, ensure_ascii=False).encode("utf-8"), headers=headers)


def read_until_peer_closed(conn: socket.socket) -> int:
    conn.settimeout(1.8)
    count = 0
    try:
        while data := conn.recv(64 * 1024):
            count += len(data)
    except (ConnectionResetError, ConnectionAbortedError):
        pass
    return count
