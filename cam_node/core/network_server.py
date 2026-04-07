import socket
import json
import threading
import time
from maix import app

class NetworkServer:
    def __init__(self, ip, port):
        self.ip = ip
        self.port = port
        self.sock = None
        self.conn = None
        self.target_to_find = ""
        self.current_mode = "normal"  # normal / turn_ahead / arrived
        self.running = True

    def start(self):
        """建立原生 TCP 服务端"""
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((self.ip, self.port))
        self.sock.listen(1)
        self.sock.settimeout(1.0)
        
        # 启动接收线程
        recv_thread = threading.Thread(target=self._recv_loop, daemon=True)
        recv_thread.start()
        print(f"[Network] Server started at {self.ip}:{self.port}")

    def _recv_loop(self):
        """后台接收线程"""
        while not app.need_exit() and self.running:
            if self.conn:
                try:
                    data = self.conn.recv(1024).decode('utf-8').strip()
                    if data:
                        msg = json.loads(data)
                        if msg.get("type") == "search":
                            self.target_to_find = msg.get("object", "")
                            print(f"[Network] Command received: search {self.target_to_find}")
                        elif msg.get("type") == "mode":
                            self.current_mode = msg.get("mode", "normal")
                            print(f"[Network] Mode switched to: {self.current_mode}")
                except (socket.timeout, BlockingIOError):
                    continue
                except Exception as e:
                    print(f"[Network] Connection broken: {e}")
                    self.conn = None
            else:
                try:
                    self.conn, addr = self.sock.accept()
                    self.conn.settimeout(0.5)
                    print(f"[Network] Raspberry Pi connected: {addr}")
                except socket.timeout:
                    continue

    def send_warning(self, object_name, distance):
        """发送障碍物预警"""
        if self.conn:
            payload = {
                "type": "warning",
                "object": object_name,
                "distance": round(distance, 1)
            }
            try:
                self.conn.sendall((json.dumps(payload) + "\n").encode('utf-8'))
            except Exception:
                self.conn = None

    def close(self):
        self.running = False
        if self.conn: self.conn.close()
        if self.sock: self.sock.close()
