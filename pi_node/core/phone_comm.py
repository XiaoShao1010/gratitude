import json
import shutil
import socket
import subprocess
import threading
import time
import uuid

try:
    import bluetooth as pybluez
except Exception:
    pybluez = None

BT_UUID = "00001101-0000-1000-8000-00805F9B34FB"
          #00001101-0000-1000-8000-00805F9B34FB
SERVICE_NAME = "BlindNavPi"
RFCOMM_CHANNEL = 1


class PhoneComm:
    """Bluetooth RFCOMM server for Pi -> Phone navigation commands.

    Phone connects to this server, then Pi sends:
    - {"action": "START_NAV", "target": "文三路地铁站"}
    - {"action": "STOP_NAV"}
    - {"action": "GET_LOCATION"}

    Phone replies with navigation state updates such as:
    - {"status": "NAV_ACTIVE", "event": "TURN_LEFT", "distance": 15}
    - {"status": "ARRIVED"}
    """

    def __init__(self, phone_name):
        self.phone_name = phone_name
        self.server_sock = None
        self.client_sock = None
        self.client_file = None
        self.bt_backend = None
        self.running = True
        self.service_registered = False
        self.nav_status_callback = None
        self.arrived_callback = None
        self.location_callback = None
        self.message_callback = None
        self._connect_lock = threading.Lock()

    def connect(self):
        t = threading.Thread(target=self._server_loop, daemon=True)
        t.start()

    def _server_loop(self):
        while self.running:
            try:
                self._ensure_server_socket()
                print(f"[BT] Using {self.bt_backend} backend")
                print(f"[BT] Waiting for {self.phone_name} to connect on {SERVICE_NAME}...")

                client_sock, client_info = self.server_sock.accept()
                with self._connect_lock:
                    self._close_client_locked()
                    self.client_sock = client_sock
                    # self.client_file = client_sock.makefile('r', encoding='utf-8')
                    self.client_file = client_sock.makefile('r')

                print(f"[BT] Connected from {client_info[0]}:{client_info[1]}")
                self._read_loop()
            except RuntimeError as e:
                if self.running:
                    print(f"[BT] {e}")
                self.running = False
                break
            except Exception as e:
                if self.running:
                    print(f"[BT] Server error: {e}. Retrying in 3s...")
                    time.sleep(3)
            finally:
                with self._connect_lock:
                    self._close_client_locked()

        with self._connect_lock:
            self._close_server_locked()

    def _ensure_server_socket(self):
        if self.server_sock:
            return

        if pybluez is not None:
            try:
                server_sock = pybluez.BluetoothSocket(pybluez.RFCOMM)
                self.bt_backend = "PyBluez"
            except Exception as e:
                print(f"[BT] PyBluez backend unavailable: {e}")
                server_sock = None
            else:
                server_sock.bind(("", RFCOMM_CHANNEL))
                server_sock.listen(1)
                self.server_sock = server_sock
                self._register_sdp_service()
                return

        if hasattr(socket, "AF_BLUETOOTH") and hasattr(socket, "BTPROTO_RFCOMM"):
            self.bt_backend = "stdlib socket"
            server_sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
            server_sock.bind(("", RFCOMM_CHANNEL))
            server_sock.listen(1)
            self.server_sock = server_sock
            self._register_sdp_service()
            return

        raise RuntimeError(
            "当前 Python 解释器没有 RFCOMM 支持。请改用 Raspberry Pi 系统 Python (/usr/bin/python3)，"
            "或者安装带蓝牙支持的 Python 包（例如 conda-forge 的 pybluez / Debian 的 python3-bluez）。"
        )

    def _register_sdp_service(self):
        if self.service_registered:
            return

        if pybluez is not None and hasattr(pybluez, "advertise_service"):
            try:
                pybluez.advertise_service(
                    self.server_sock,
                    SERVICE_NAME,
                    service_id=uuid.UUID(BT_UUID),
                    service_classes=[uuid.UUID(BT_UUID), pybluez.SERIAL_PORT_CLASS],
                    profiles=[pybluez.SERIAL_PORT_PROFILE],
                )
                self.service_registered = True
                print(f"[BT] PyBluez service advertised on channel {RFCOMM_CHANNEL}")
                return
            except Exception as e:
                print(f"[BT] PyBluez service advertisement failed: {e}")

        if shutil.which("sdptool") is None:
            print("[BT] sdptool not found; Android UUID discovery may fail")
            return

        command = ["sdptool", "add", f"--channel={RFCOMM_CHANNEL}", "SP"]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        if result.returncode != 0:
            message = result.stderr.strip() or result.stdout.strip() or "unknown error"
            print(f"[BT] SDP registration failed: {message}")
            return

        self.service_registered = True
        print(f"[BT] SDP service registered on channel {RFCOMM_CHANNEL}")

    def _read_loop(self):
        while self.running:
            with self._connect_lock:
                client_file = self.client_file

            if client_file is None:
                break

            line = client_file.readline()
            if not line:
                break
            self._handle_response(line.strip())

    def _handle_response(self, line):
        try:
            data = json.loads(line)
            if self.message_callback:
                try:
                    self.message_callback(data)
                except Exception:
                    pass

            status = data.get("status")

            if status == "ARRIVED":
                if self.arrived_callback:
                    try:
                        self.arrived_callback()
                    except Exception:
                        pass
            elif status in ("NAV_ACTIVE", "NAV_PAUSED", "NAV_RESUMED"):
                event = data.get("event", "")
                distance = data.get("distance", 0)
                if self.nav_status_callback:
                    try:
                        self.nav_status_callback(status, event, distance)
                    except Exception:
                        pass
            elif status in ("OK", "ERROR") and data.get("event") == "CURRENT_LOCATION":
                if self.location_callback:
                    try:
                        self.location_callback(data)
                    except Exception:
                        pass
        except json.JSONDecodeError:
            pass

    def send_nav_request(self, destination: str) -> bool:
        return self.send_action("START_NAV", destination)

    def send_stop_nav(self) -> bool:
        return self.send_action("STOP_NAV")

    def send_location_request(self) -> bool:
        return self.send_action("GET_LOCATION")

    def send_action(self, action: str, target: str = None) -> bool:
        payload = {"action": action}
        if target is not None:
            payload["target"] = target
        return self._send_json(payload)

    def _send_json(self, payload: dict) -> bool:
        with self._connect_lock:
            if not self.client_sock:
                print("[BT] Not connected, cannot send")
                return False
            try:
                self.client_sock.sendall((json.dumps(payload) + "\n").encode('utf-8'))
                return True
            except Exception as e:
                print(f"[BT] Send error: {e}")
                return False

    def _close_client_locked(self):
        if self.client_file:
            try:
                self.client_file.close()
            except:
                pass
            self.client_file = None

        if self.client_sock:
            try:
                self.client_sock.close()
            except:
                pass
            self.client_sock = None

    def _close_server_locked(self):
        if self.server_sock:
            try:
                self.server_sock.close()
            except:
                pass
            self.server_sock = None

    def close(self):
        self.running = False
        with self._connect_lock:
            self._close_client_locked()
            self._close_server_locked()
