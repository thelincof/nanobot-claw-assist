#!/usr/bin/env python3
"""Small local gateway used to exercise the Nanobot KMP client.

HTTP:  http://127.0.0.1:8765
WS:    ws://127.0.0.1:8766/ws?token=ws-token

Bootstrap secret: test
API token: api-token

This is a development fixture, not a production server. It implements just
enough of the WebUI contract for the client's v1 flows: bootstrap, sessions,
thread, delete, new_chat, attach, message streaming, stop, and transcription.
"""

from __future__ import annotations

import asyncio
import io
import json
import math
import struct
import time
import urllib.parse
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed

HTTP_PORT = 8765
WS_PORT = 8766
SECRET = "test"
API_TOKEN = "api-token"
WS_TOKEN = "ws-token"

_lock = asyncio.Lock()
_sessions: dict[str, dict] = {}
_threads: dict[str, list[dict]] = {}
_chat_tasks: dict[str, asyncio.Task] = {}
_chat_counter = 0


def now_ms() -> int:
    return int(time.time() * 1000)


def iso_now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def session_key(chat_id: str) -> str:
    return f"websocket:{chat_id}"


def add_demo_session() -> None:
    chat_id = "demo-chat"
    key = session_key(chat_id)
    _sessions[key] = {
        "key": key,
        "channel": "webui",
        "chat_id": chat_id,
        "created_at": iso_now(),
        "updated_at": iso_now(),
        "title": "Demo chat",
        "preview": "Hi! This is the mock gateway.",
        "model_preset": "mock",
        "run_started_at": None,
    }
    _threads[chat_id] = [
        {
            "id": "demo-user-1",
            "role": "user",
            "content": "Hello",
            "createdAt": now_ms() - 60_000,
            "turnId": "demo-turn-1",
        },
        {
            "id": "demo-assistant-1",
            "role": "assistant",
            "content": "Hi! This is the **mock gateway**. Try sending a message.",
            "createdAt": now_ms() - 59_000,
            "turnId": "demo-turn-1",
            "latencyMs": 420,
            "isStreaming": False,
        },
    ]


def thread_payload(chat_id: str) -> dict:
    messages = _threads.get(chat_id, [])
    return {
        "schemaVersion": 1,
        "sessionKey": session_key(chat_id),
        "savedAt": iso_now(),
        "messages": messages,
        "completed_turn_ids": [m.get("turnId") for m in messages if m.get("turnId")],
        "has_pending_tool_calls": False,
        "active_turn_id": None,
        "page": {
            "before_cursor": None,
            "has_more_before": False,
            "loaded_message_count": len(messages),
            "total_known_message_count": len(messages),
        },
    }


def json_bytes(payload: object) -> bytes:
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    server_version = "NanobotMock/0.1"

    def log_message(self, fmt: str, *args) -> None:
        print(f"[http] {self.address_string()} {fmt % args}", flush=True)

    def _send(self, code: int, payload: object) -> None:
        body = json_bytes(payload)
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_bytes(self, code: int, body: bytes, content_type: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        auth = self.headers.get("Authorization", "")
        if auth.lower().startswith("bearer ") and auth[7:].strip() == API_TOKEN:
            return True
        return self.headers.get("X-Nanobot-Auth", "") == SECRET

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)
        if path == "/webui/bootstrap":
            if not self._authorized():
                self._send(401, {"error": "Unauthorized"})
                return
            self._send(
                200,
                {
                    "token": WS_TOKEN,
                    "api_token": API_TOKEN,
                    "ws_path": "/ws",
                    "ws_url": f"ws://127.0.0.1:{WS_PORT}/ws",
                    "expires_in": 3600,
                    "limits": {
                        "transport": {
                            "max_frame_bytes": 64 * 1024 * 1024,
                            "envelope_reserve_bytes": 65536,
                        },
                        "message": {"max_text_bytes": 65536},
                        "attachments": {
                            "max_count": 4,
                            "max_file_bytes": 6 * 1024 * 1024,
                            "max_total_bytes": 24 * 1024 * 1024,
                        },
                    },
                    "model_name": "mock-model",
                },
            )
            return
        if not self._authorized():
            self._send(401, {"error": "Unauthorized"})
            return
        if path == "/api/sessions":
            ordered = sorted(
                _sessions.values(),
                key=lambda row: row.get("updated_at") or "",
                reverse=True,
            )
            self._send(200, {"sessions": ordered})
            return
        prefix = "/api/sessions/"
        suffix = "/webui-thread"
        if path.startswith(prefix) and path.endswith(suffix):
            key = path[len(prefix) : -len(suffix)]
            session = _sessions.get(key)
            if session is None:
                self._send(404, {"error": "session not found"})
                return
            self._send(200, thread_payload(session["chat_id"]))
            return
        self._send(404, {"error": "not found"})

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)
        if path == "/v1/audio/speech":
            length = int(self.headers.get("Content-Length", "0") or 0)
            raw = self.rfile.read(length) if length > 0 else b"{}"
            try:
                request = json.loads(raw.decode("utf-8"))
            except Exception:
                request = {}
            print(f"[tts] speech request: {request}", flush=True)
            self._send_bytes(200, tone_wav(), "audio/wav")
            return
        if not self._authorized():
            self._send(401, {"error": "Unauthorized"})
            return
        prefix = "/api/sessions/"
        suffix = "/delete"
        if path.startswith(prefix) and path.endswith(suffix):
            key = path[len(prefix) : -len(suffix)]
            session = _sessions.pop(key, None)
            if session is None:
                self._send(404, {"error": "session not found"})
                return
            _threads.pop(session["chat_id"], None)
            self._send(200, {"deleted": True, "blocked_by_automations": False})
            return
        self._send(404, {"error": "not found"})


def tone_wav(seconds: float = 0.8, sample_rate: int = 16000) -> bytes:
    frames = int(seconds * sample_rate)
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        data = bytearray()
        for index in range(frames):
            value = int(9000 * math.sin(2 * math.pi * 440 * index / sample_rate))
            data.extend(struct.pack("<h", value))
        wav.writeframes(bytes(data))
    return buffer.getvalue()


async def send(ws, payload: dict) -> None:
    await ws.send(json.dumps(payload, ensure_ascii=False))


async def stream_turn(ws, chat_id: str, turn_id: str, user_text: str) -> None:
    try:
        await asyncio.sleep(0.25)
        await send(
            ws,
            {
                "event": "reasoning_delta",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "text": "Checking the request and preparing a mock answer...\n",
            },
        )
        await send(ws, {"event": "reasoning_end", "chat_id": chat_id, "turn_id": turn_id})
        await send(
            ws,
            {
                "event": "message",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "text": "",
                "kind": "progress",
                "tool_events": [
                    {
                        "version": 1,
                        "phase": "started",
                        "call_id": "mock-call-1",
                        "name": "mock_search",
                        "arguments": {"query": "nanobot"},
                    }
                ],
            },
        )
        await asyncio.sleep(0.35)
        await send(
            ws,
            {
                "event": "message",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "text": "",
                "kind": "progress",
                "tool_events": [
                    {
                        "version": 1,
                        "phase": "completed",
                        "call_id": "mock-call-1",
                        "name": "mock_search",
                        "arguments": {"query": "nanobot"},
                        "result": {"matches": 2, "summary": "Mock search finished"},
                    }
                ],
            },
        )
        await send(
            ws,
            {
                "event": "file_edit",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "edits": [
                    {
                        "version": 1,
                        "call_id": "mock-edit-1",
                        "tool": "write_file",
                        "path": "notes/mock.md",
                        "phase": "completed",
                        "added": 3,
                        "deleted": 1,
                        "status": "completed",
                        "diff": {
                            "format": "unified",
                            "text": "@@ -1 +1,3 @@\n-old\n+new\n+second\n+third\n",
                        },
                    }
                ],
            },
        )
        text = "Here is a **mock answer** with streaming, reasoning, a tool call, and a file edit."
        for chunk in ("Here is a ", "**mock answer** ", "with streaming, ", "reasoning, a tool call, ", "and a file edit."):
            await send(
                ws,
                {
                    "event": "delta",
                    "chat_id": chat_id,
                    "turn_id": turn_id,
                    "text": chunk,
                },
            )
            await asyncio.sleep(0.12)
        await send(
            ws,
            {
                "event": "stream_end",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "text": text,
            },
        )
        await send(
            ws,
            {
                "event": "turn_end",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "latency_ms": 1450,
            },
        )
        _threads.setdefault(chat_id, []).extend(
            [
                {
                    "id": f"user-{turn_id}",
                    "role": "user",
                    "content": user_text,
                    "createdAt": now_ms() - 1500,
                    "turnId": turn_id,
                },
                {
                    "id": f"assistant-{turn_id}",
                    "role": "assistant",
                    "content": text,
                    "createdAt": now_ms(),
                    "turnId": turn_id,
                    "latencyMs": 1450,
                    "isStreaming": False,
                    "reasoning": "Checking the request and preparing a mock answer.",
                    "toolEvents": [
                        {
                            "version": 1,
                            "phase": "completed",
                            "call_id": "mock-call-1",
                            "name": "mock_search",
                            "arguments": {"query": "nanobot"},
                            "result": {"matches": 2, "summary": "Mock search finished"},
                        }
                    ],
                    "fileEdits": [
                        {
                            "version": 1,
                            "call_id": "mock-edit-1",
                            "tool": "write_file",
                            "path": "notes/mock.md",
                            "phase": "completed",
                            "added": 3,
                            "deleted": 1,
                            "status": "completed",
                        }
                    ],
                },
            ]
        )
        key = session_key(chat_id)
        if key in _sessions:
            _sessions[key]["updated_at"] = iso_now()
            _sessions[key]["preview"] = text
            _sessions[key]["title"] = "Mock chat"
            _sessions[key]["run_started_at"] = None
        await send(ws, {"event": "session_updated", "chat_id": chat_id, "scope": "sidebar"})
    except asyncio.CancelledError:
        raise
    except Exception as exc:  # noqa: BLE001
        print(f"[ws] stream error: {exc}", flush=True)
        await send(
            ws,
            {
                "event": "error",
                "chat_id": chat_id,
                "turn_id": turn_id,
                "detail": str(exc),
            },
        )


async def ws_handler(ws) -> None:
    query = urllib.parse.parse_qs(urllib.parse.urlparse(ws.request.path).query)
    token = (query.get("token") or [""])[0]
    if token != WS_TOKEN:
        await ws.close(code=1008, reason="invalid token")
        return
    print("[ws] client connected", flush=True)
    await send(ws, {"event": "ready", "chat_id": "demo-chat", "client_id": "mock-client"})
    try:
        async for raw in ws:
            try:
                frame = json.loads(raw)
            except json.JSONDecodeError:
                continue
            kind = frame.get("type")
            chat_id = frame.get("chat_id")
            if kind == "new_chat":
                global _chat_counter
                _chat_counter += 1
                chat_id = f"mock-chat-{_chat_counter}"
                key = session_key(chat_id)
                _sessions[key] = {
                    "key": key,
                    "channel": "webui",
                    "chat_id": chat_id,
                    "created_at": iso_now(),
                    "updated_at": iso_now(),
                    "title": "New mock chat",
                    "preview": None,
                    "model_preset": "mock",
                    "run_started_at": None,
                }
                _threads[chat_id] = []
                await send(ws, {"event": "attached", "chat_id": chat_id, "temporary": False})
                await send(ws, {"event": "session_updated", "chat_id": chat_id, "scope": "sidebar"})
            elif kind == "attach":
                if chat_id:
                    await send(ws, {"event": "attached", "chat_id": chat_id, "temporary": False})
            elif kind == "message":
                if not chat_id:
                    continue
                content = frame.get("content") or ""
                turn_id = frame.get("turn_id") or f"turn-{now_ms()}"
                if content.strip() == "/stop":
                    task = _chat_tasks.pop(chat_id, None)
                    if task:
                        task.cancel()
                    await send(
                        ws,
                        {
                            "event": "turn_end",
                            "chat_id": chat_id,
                            "turn_id": turn_id,
                            "latency_ms": 0,
                        },
                    )
                    continue
                await send(
                    ws,
                    {
                        "event": "message_accepted",
                        "chat_id": chat_id,
                        "turn_id": turn_id,
                    },
                )
                old = _chat_tasks.pop(chat_id, None)
                if old:
                    old.cancel()
                _chat_tasks[chat_id] = asyncio.create_task(stream_turn(ws, chat_id, turn_id, content))
            elif kind == "transcribe_audio":
                request_id = frame.get("request_id")
                await asyncio.sleep(0.4)
                await send(
                    ws,
                    {
                        "event": "transcription_result",
                        "request_id": request_id,
                        "text": "Hello from the mock microphone",
                    },
                )
    except ConnectionClosed:
        pass
    finally:
        print("[ws] client disconnected", flush=True)
        for task in _chat_tasks.values():
            task.cancel()
        _chat_tasks.clear()


async def main() -> None:
    add_demo_session()
    http = ThreadingHTTPServer(("127.0.0.1", HTTP_PORT), Handler)
    http.daemon_threads = True
    loop = asyncio.get_running_loop()
    loop.run_in_executor(None, http.serve_forever)
    async with serve(ws_handler, "127.0.0.1", WS_PORT):
        print(f"Mock HTTP http://127.0.0.1:{HTTP_PORT}", flush=True)
        print(f"Mock WS   ws://127.0.0.1:{WS_PORT}/ws?token={WS_TOKEN}", flush=True)
        print(f"Secret: {SECRET}", flush=True)
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
