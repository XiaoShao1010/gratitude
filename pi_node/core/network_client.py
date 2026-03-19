import socket
import json
import threading
import time

class NetworkClient:
    def __init__(self, ip, port):
        self.ip = ip
        self.port = port
        self.client_sock = None
        self.running = True
        self.warning_callback = None # 收到警报时的回调

    def connect(self):
        """异步连接线程"""
        def _connect_loop():
            while self.running:
                try:
                    if self.client_sock is None:
                        self.client_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                        self.client_sock.connect((self.ip, self.port))
                        print(f"[Network] Connected to MaixCam @ {self.ip}:{self.port}")
                        
                        f = self.client_sock.makefile('r', encoding='utf-8')
                        while self.running:
                            line = f.readline()
                            if not line: break
                            self._handle_data(line)
                            
                except Exception as e:
                    print(f"[Network] Connection error: {e}. Retrying...")
                    self.client_sock = None
                    time.sleep(3)
                    
        t = threading.Thread(target=_connect_loop, daemon=True)
        t.start()

    def _handle_data(self, line):
        try:
            data = json.loads(line)
            if data.get("type") == "warning" and self.warning_callback:
                self.warning_callback(data["object"], data["distance"])
        except:
            pass

    def send_search(self, object_name):
        if self.client_sock:
            payload = {"type": "search", "object": object_name}
            try:
                self.client_sock.sendall((json.dumps(payload) + "\n").encode('utf-8'))
            except:
                self.client_sock = None

    def send_mode(self, mode: str):
        if self.client_sock:
            payload = {"type": "mode", "mode": mode}
            try:
                self.client_sock.sendall((json.dumps(payload) + "\n").encode('utf-8'))
            except:
                self.client_sock = None

    def close(self):
        self.running = False
        if self.client_sock: self.client_sock.close()
