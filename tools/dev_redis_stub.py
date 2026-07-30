"""
Tiny Redis-compatible server for local development on Windows.

This is not a production Redis replacement. It implements the small RESP command
subset used by the legacy backend during local startup and login flows.
"""

from __future__ import annotations

import socketserver
import threading
import time
import traceback
from pathlib import Path
from typing import BinaryIO


HOST = "127.0.0.1"
PORT = 6379
LOG_FILE = Path(__file__).with_suffix(".log")

_lock = threading.RLock()
_values: dict[bytes, bytes] = {}
_sets: dict[bytes, set[bytes]] = {}
_expires: dict[bytes, float] = {}


def _log(text: str) -> None:
    try:
        with LOG_FILE.open("a", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + text + "\n")
    except Exception:
        pass


def _now() -> float:
    return time.time()


def _purge(key: bytes) -> None:
    exp = _expires.get(key)
    if exp is not None and exp <= _now():
        _values.pop(key, None)
        _sets.pop(key, None)
        _expires.pop(key, None)


def _has_key(key: bytes) -> bool:
    _purge(key)
    return key in _values or key in _sets


def _simple(text: str) -> bytes:
    return b"+" + text.encode() + b"\r\n"


def _error(text: str) -> bytes:
    return b"-ERR " + text.encode() + b"\r\n"


def _int(num: int) -> bytes:
    return b":" + str(num).encode() + b"\r\n"


def _bulk(data: bytes | str | None) -> bytes:
    if data is None:
        return b"$-1\r\n"
    if isinstance(data, str):
        data = data.encode()
    return b"$" + str(len(data)).encode() + b"\r\n" + data + b"\r\n"


def _array(items: list[bytes | str | int | None]) -> bytes:
    out = [b"*" + str(len(items)).encode() + b"\r\n"]
    for item in items:
        if isinstance(item, int):
            out.append(_int(item))
        else:
            out.append(_bulk(item))
    return b"".join(out)


def _read_line(rfile: BinaryIO) -> bytes | None:
    line = rfile.readline()
    if not line:
        return None
    if line.endswith(b"\r\n"):
        return line[:-2]
    return line.rstrip(b"\n")


def _read_command(rfile: BinaryIO) -> list[bytes] | None:
    first = _read_line(rfile)
    if first is None:
        return None
    if not first:
        return []
    if first.startswith(b"*"):
        count = int(first[1:])
        parts: list[bytes] = []
        for _ in range(count):
            length_line = _read_line(rfile)
            if length_line is None or not length_line.startswith(b"$"):
                return None
            length = int(length_line[1:])
            data = rfile.read(length)
            rfile.read(2)
            parts.append(data)
        return parts
    return first.split()


def _ttl_seconds(key: bytes) -> int:
    _purge(key)
    if key not in _values and key not in _sets:
        return -2
    exp = _expires.get(key)
    if exp is None:
        return -1
    return max(0, int(exp - _now()))


def _handle(parts: list[bytes]) -> bytes:
    if not parts:
        return _error("empty command")
    cmd = parts[0].upper()

    with _lock:
        if cmd == b"PING":
            return _bulk(parts[1]) if len(parts) > 1 else _simple("PONG")
        if cmd in {b"QUIT"}:
            return _simple("OK")
        if cmd == b"SELECT":
            return _simple("OK")
        if cmd == b"AUTH":
            return _simple("OK")
        if cmd == b"CLIENT":
            return _simple("OK")
        if cmd == b"COMMAND":
            return b"*0\r\n"
        if cmd == b"INFO":
            return _bulk("redis_version:dev-stub\r\n")
        if cmd == b"HELLO":
            return _array(["server", "redis", "version", "dev-stub", "proto", 2])

        if len(parts) < 2:
            return _error("wrong number of arguments")

        key = parts[1]
        _purge(key)

        if cmd == b"GET":
            return _bulk(_values.get(key))

        if cmd == b"SET":
            value = parts[2] if len(parts) >= 3 else b""
            opts = [p.upper() for p in parts[3:]]
            if b"XX" in opts and not _has_key(key):
                return _bulk(None)
            if b"NX" in opts and _has_key(key):
                return _bulk(None)
            _sets.pop(key, None)
            _values[key] = value
            _expires.pop(key, None)
            for idx, opt in enumerate(opts):
                if opt == b"EX" and idx + 1 < len(opts):
                    _expires[key] = _now() + int(opts[idx + 1])
                if opt == b"PX" and idx + 1 < len(opts):
                    _expires[key] = _now() + int(opts[idx + 1]) / 1000
            return _simple("OK")

        if cmd in {b"DEL", b"UNLINK"}:
            removed = 0
            for k in parts[1:]:
                _purge(k)
                if k in _values or k in _sets:
                    removed += 1
                _values.pop(k, None)
                _sets.pop(k, None)
                _expires.pop(k, None)
            return _int(removed)

        if cmd == b"EXISTS":
            return _int(sum(1 for k in parts[1:] if _has_key(k)))

        if cmd in {b"EXPIRE", b"PEXPIRE"}:
            if not _has_key(key):
                return _int(0)
            amount = int(parts[2])
            _expires[key] = _now() + (amount / 1000 if cmd == b"PEXPIRE" else amount)
            return _int(1)

        if cmd == b"TTL":
            return _int(_ttl_seconds(key))

        if cmd == b"PTTL":
            ttl = _ttl_seconds(key)
            return _int(ttl if ttl < 0 else ttl * 1000)

        if cmd in {b"INCR", b"INCRBY"}:
            amount = int(parts[2]) if cmd == b"INCRBY" and len(parts) > 2 else 1
            current = int(_values.get(key, b"0"))
            current += amount
            _sets.pop(key, None)
            _values[key] = str(current).encode()
            return _int(current)

        if cmd == b"SADD":
            _values.pop(key, None)
            members = _sets.setdefault(key, set())
            added = 0
            for member in parts[2:]:
                if member not in members:
                    members.add(member)
                    added += 1
            return _int(added)

        if cmd == b"SMEMBERS":
            return _array(list(_sets.get(key, set())))

        if cmd == b"SREM":
            members = _sets.get(key, set())
            removed = 0
            for member in parts[2:]:
                if member in members:
                    members.remove(member)
                    removed += 1
            return _int(removed)

        if cmd == b"TYPE":
            if key in _sets:
                return _simple("set")
            if key in _values:
                return _simple("string")
            return _simple("none")

        if cmd == b"DBSIZE":
            for k in list(_expires):
                _purge(k)
            return _int(len(_values) + len(_sets))

    _log(f"unsupported command: {parts!r}")
    return _error(f"unsupported command {cmd.decode(errors='ignore')}")


class Handler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        try:
            while True:
                parts = _read_command(self.rfile)
                if parts is None:
                    return
                _log(f"cmd: {parts!r}")
                response = _handle(parts)
                self.wfile.write(response)
                self.wfile.flush()
                if parts and parts[0].upper() == b"QUIT":
                    return
        except Exception:
            _log("handler exception:\n" + traceback.format_exc())
            try:
                self.wfile.write(_error("internal dev redis stub error"))
                self.wfile.flush()
            except Exception:
                return


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


if __name__ == "__main__":
    with Server((HOST, PORT), Handler) as server:
        _log(f"dev redis stub listening on {HOST}:{PORT}")
        server.serve_forever()
