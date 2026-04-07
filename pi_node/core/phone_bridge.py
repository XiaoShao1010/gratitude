import json
import os
import socket
import subprocess
import threading
import time
import uuid
from pathlib import Path


DEFAULT_BRIDGE_HOST = "127.0.0.1"
DEFAULT_BRIDGE_PORT = 8765


class PhoneBridge:
    def __init__(self, phone_name, helper_script=None, helper_python=None, bridge_host=DEFAULT_BRIDGE_HOST, bridge_port=DEFAULT_BRIDGE_PORT):
        self.phone_name = phone_name
        self.bridge_host = bridge_host
        self.bridge_port = bridge_port
        self.helper_script = Path(helper_script) if helper_script else Path(__file__).resolve().parents[1] / "bt_sender.py"
        self.helper_python = helper_python or os.environ.get("GRATITUDE_BT_PYTHON", "/usr/bin/python3")

        self.nav_status_callback = None
        self.arrived_callback = None
        self.location_callback = None

        self.running = True
        self._bridge_enabled = False
        self._helper_process = None
        self._bridge_thread = None
        self._bridge_sock = None
        self._bridge_file = None
        self._bridge_lock = threading.Lock()
        self._pending_lock = threading.Lock()
        self._pending_requests = {}
        self._connected_event = threading.Event()

    def connect(self):
        if self._bridge_thread is not None:
            return

        if not self.helper_script.exists():
            print(f"[BT] Helper script not found: {self.helper_script}")
            return

        if not self.helper_python or not Path(self.helper_python).exists():
            print("[BT] System Python with Bluetooth support not found; phone bridge disabled")
            return

        command = [
            self.helper_python,
            "-u",
            str(self.helper_script),
            "--bridge-host",
            self.bridge_host,
            "--bridge-port",
            str(self.bridge_port),
            "--phone-name",
            self.phone_name,
        ]

        try:
            self._helper_process = subprocess.Popen(command, cwd=str(self.helper_script.parent))
            self._bridge_enabled = True
            print(f"[BT] Helper process started with {self.helper_python}")
        except Exception as exc:
            print(f"[BT] Failed to start helper process: {exc}")
            return

        self._bridge_thread = threading.Thread(target=self._bridge_loop, daemon=True)
        self._bridge_thread.start()

    def send_nav_request(self, destination: str) -> bool:
        if not destination or destination == "null":
            return False
        return self._send_request({"type": "nav_request", "target": destination})

    def send_stop_nav(self) -> bool:
        return self._send_request({"type": "stop_nav"})

    def send_location_request(self) -> bool:
        return self._send_request({"type": "get_location"})

    def close(self):
        self.running = False
        self._connected_event.clear()

        with self._pending_lock:
            for pending in self._pending_requests.values():
                pending["response"] = {"ok": False, "reason": "bridge closed"}
                pending["event"].set()
            self._pending_requests.clear()

        with self._bridge_lock:
            self._close_bridge_locked()

        if self._helper_process is not None:
            try:
                self._helper_process.terminate()
                self._helper_process.wait(timeout=3)
            except Exception:
                try:
                    self._helper_process.kill()
                except Exception:
                    pass
            self._helper_process = None

        self._bridge_thread = None

    def _bridge_loop(self):
        try:
            while self.running:
                if self._helper_process is not None and self._helper_process.poll() is not None:
                    self._bridge_enabled = False
                    print(f"[BT] Helper exited with code {self._helper_process.returncode}")
                    break

                try:
                    sock = socket.create_connection((self.bridge_host, self.bridge_port), timeout=1.0)
                    sock.settimeout(None)
                    bridge_file = sock.makefile("r", encoding="utf-8")
                    with self._bridge_lock:
                        self._bridge_sock = sock
                        self._bridge_file = bridge_file
                    self._connected_event.set()
                    print(f"[BT] Local bridge connected on {self.bridge_host}:{self.bridge_port}")
                    self._read_loop()
                except OSError:
                    if not self.running:
                        break
                    time.sleep(1)
                finally:
                    self._connected_event.clear()
                    with self._bridge_lock:
                        self._close_bridge_locked()
                    self._fail_pending_requests("bridge disconnected")
        finally:
            self._bridge_thread = None

    def _read_loop(self):
        while self.running:
            with self._bridge_lock:
                bridge_file = self._bridge_file

            if bridge_file is None:
                break

            line = bridge_file.readline()
            if not line:
                break

            self._handle_bridge_message(line.strip())

    def _handle_bridge_message(self, line):
        try:
            message = json.loads(line)
        except json.JSONDecodeError:
            return

        message_type = message.get("type")
        if message_type == "response":
            request_id = message.get("request_id")
            if not request_id:
                return
            with self._pending_lock:
                pending = self._pending_requests.get(request_id)
            if pending is None:
                return
            pending["response"] = message
            pending["event"].set()
            return

        if message_type in ("phone_message", "phone_event"):
            payload = message.get("data") if message_type == "phone_message" else message
            if isinstance(payload, dict):
                self._handle_phone_message(payload)

    def _handle_phone_message(self, message):
        status = message.get("status")
        event = message.get("event", "")
        distance = message.get("distance", 0)

        if status == "ARRIVED":
            if self.arrived_callback:
                try:
                    self.arrived_callback()
                except Exception:
                    pass
            return

        if status in ("NAV_ACTIVE", "NAV_PAUSED", "NAV_RESUMED"):
            if self.nav_status_callback:
                try:
                    self.nav_status_callback(status, event, distance)
                except Exception:
                    pass
            return

        if status in ("OK", "ERROR") and event == "CURRENT_LOCATION":
            if self.location_callback:
                try:
                    self.location_callback(message)
                except Exception:
                    pass

    def _send_request(self, payload):
        if not self._bridge_enabled:
            print("[BT] Phone bridge is disabled")
            return False

        if not self._connected_event.wait(timeout=5):
            print("[BT] Local bridge is not connected")
            return False

        request_id = uuid.uuid4().hex
        payload = dict(payload)
        payload["request_id"] = request_id

        pending_event = threading.Event()
        with self._pending_lock:
            self._pending_requests[request_id] = {"event": pending_event, "response": None}

        if not self._send_json(payload):
            with self._pending_lock:
                self._pending_requests.pop(request_id, None)
            return False

        if not pending_event.wait(timeout=8):
            with self._pending_lock:
                self._pending_requests.pop(request_id, None)
            return False

        with self._pending_lock:
            pending = self._pending_requests.pop(request_id, None)

        if not pending or not pending.get("response"):
            return False

        response = pending["response"]
        return bool(response.get("ok"))

    def _send_json(self, payload):
        data = (json.dumps(payload) + "\n").encode("utf-8")
        with self._bridge_lock:
            bridge_sock = self._bridge_sock

        if bridge_sock is None:
            return False

        try:
            bridge_sock.sendall(data)
            return True
        except Exception as exc:
            print(f"[BT] Local bridge send error: {exc}")
            return False

    def _fail_pending_requests(self, reason):
        with self._pending_lock:
            pending_requests = list(self._pending_requests.values())
            self._pending_requests.clear()

        for pending in pending_requests:
            pending["response"] = {"ok": False, "reason": reason}
            pending["event"].set()

    def _close_bridge_locked(self):
        if self._bridge_file is not None:
            try:
                self._bridge_file.close()
            except Exception:
                pass
            self._bridge_file = None

        if self._bridge_sock is not None:
            try:
                self._bridge_sock.close()
            except Exception:
                pass
            self._bridge_sock = None