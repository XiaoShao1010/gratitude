import argparse
import json
import socket
import threading

from core.phone_comm import PhoneComm


class BluetoothBridgeDaemon:
    def __init__(self, phone_name, bridge_host, bridge_port):
        self.phone = PhoneComm(phone_name)
        self.bridge_host = bridge_host
        self.bridge_port = bridge_port
        self.running = True
        self._client_sock = None
        self._client_file = None
        self._client_lock = threading.Lock()
        self.phone.message_callback = self._forward_phone_message

    def run(self):
        self.phone.connect()

        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind((self.bridge_host, self.bridge_port))
        server_sock.listen(1)

        print(f"[BT] Helper bridge listening on {self.bridge_host}:{self.bridge_port}")

        try:
            while self.running:
                client_sock, client_addr = server_sock.accept()
                print(f"[BT] Bridge client connected from {client_addr[0]}:{client_addr[1]}")
                with self._client_lock:
                    self._close_client_locked()
                    self._client_sock = client_sock
                    self._client_file = client_sock.makefile("r", encoding="utf-8")

                try:
                    self._read_loop()
                finally:
                    with self._client_lock:
                        self._close_client_locked()
        finally:
            try:
                server_sock.close()
            except Exception:
                pass
            self.phone.close()

    def _read_loop(self):
        while self.running:
            with self._client_lock:
                client_file = self._client_file

            if client_file is None:
                break

            line = client_file.readline()
            if not line:
                break

            self._handle_request(line.strip())

    def _handle_request(self, line):
        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            return

        action = request.get("type")
        request_id = request.get("request_id")
        ok = False
        reason = "unsupported action"

        if action == "nav_request":
            target = request.get("target")
            if target:
                ok = self.phone.send_nav_request(target)
                reason = "sent" if ok else "phone unavailable"
            else:
                reason = "missing target"
        elif action == "stop_nav":
            ok = self.phone.send_stop_nav()
            reason = "sent" if ok else "phone unavailable"
        elif action == "get_location":
            ok = self.phone.send_location_request()
            reason = "sent" if ok else "phone unavailable"

        response = {"type": "response", "request_id": request_id, "ok": ok, "reason": reason, "action": action}
        self._send_to_client(response)

    def _forward_phone_message(self, message):
        self._send_to_client({"type": "phone_message", "data": message})

    def _send_to_client(self, payload):
        with self._client_lock:
            client_sock = self._client_sock

        if client_sock is None:
            return

        try:
            client_sock.sendall((json.dumps(payload) + "\n").encode("utf-8"))
        except Exception as exc:
            print(f"[BT] Bridge send error: {exc}")

    def _close_client_locked(self):
        if self._client_file is not None:
            try:
                self._client_file.close()
            except Exception:
                pass
            self._client_file = None

        if self._client_sock is not None:
            try:
                self._client_sock.close()
            except Exception:
                pass
            self._client_sock = None


def main():
    parser = argparse.ArgumentParser(description="Bluetooth helper bridge for BlindNav Pi")
    parser.add_argument("--bridge-host", default="127.0.0.1")
    parser.add_argument("--bridge-port", type=int, default=8765)
    parser.add_argument("--phone-name", default="BlindNav-Phone")
    args = parser.parse_args()

    daemon = BluetoothBridgeDaemon(args.phone_name, args.bridge_host, args.bridge_port)
    try:
        daemon.run()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()