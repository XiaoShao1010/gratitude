import bluetooth
import json
import threading
import time

BT_UUID = "00001101-0000-1000-8000-00805F9B34FB"
BT_CHANNEL = 1

class PhoneComm:
    """Bluetooth RFCOMM client for Pi → Phone navigation commands.
    
    Protocol:
    - Send: {"action": "START_NAV", "target": "文三路地铁站"}
    - Receive: {"status": "NAV_ACTIVE", "event": "TURN_LEFT", "distance": 15}
    - Receive: {"status": "ARRIVED"}
    """
    
    def __init__(self, phone_name):
        self.phone_name = phone_name
        self.sock = None
        self.running = True
        self.nav_status_callback = None
        self.arrived_callback = None
        self._connect_lock = threading.Lock()

    def connect(self):
        t = threading.Thread(target=self._connect_loop, daemon=True)
        t.start()

    def _connect_loop(self):
        while self.running:
            try:
                with self._connect_lock:
                    if self.sock:
                        try:
                            self.sock.close()
                        except:
                            pass
                        self.sock = None
                    
                    print(f"[BT] Searching for {self.phone_name}...")
                    devices = bluetooth.discover_devices(duration=8, lookup_names=True)
                    target_addr = None
                    for addr, name in devices:
                        if name == self.phone_name:
                            target_addr = addr
                            break
                    if not target_addr:
                        print(f"[BT] Device '{self.phone_name}' not found, retrying...")
                        time.sleep(3)
                        continue
                    
                    self.sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
                    self.sock.connect((target_addr, BT_CHANNEL))
                    print(f"[BT] Connected to {self.phone_name} ({target_addr})")
                    
                    f = self.sock.makefile('r', encoding='utf-8')
                    while self.running:
                        line = f.readline()
                        if not line:
                            break
                        self._handle_response(line.strip())
                        
            except Exception as e:
                print(f"[BT] Connection error: {e}. Retrying in 3s...")
                time.sleep(3)

    def _handle_response(self, line):
        try:
            data = json.loads(line)
            status = data.get("status")
            
            if status == "ARRIVED":
                if self.arrived_callback:
                    self.arrived_callback()
            elif status in ("NAV_ACTIVE", "NAV_PAUSED", "NAV_RESUMED"):
                event = data.get("event", "")
                distance = data.get("distance", 0)
                if self.nav_status_callback:
                    self.nav_status_callback(status, event, distance)
        except json.JSONDecodeError:
            pass

    def send_nav_request(self, destination: str) -> bool:
        payload = {"action": "START_NAV", "target": destination}
        return self._send_json(payload)

    def _send_json(self, payload: dict) -> bool:
        with self._connect_lock:
            if not self.sock:
                print("[BT] Not connected, cannot send")
                return False
            try:
                self.sock.send((json.dumps(payload) + "\n").encode('utf-8'))
                return True
            except Exception as e:
                print(f"[BT] Send error: {e}")
                return False

    def close(self):
        self.running = False
        with self._connect_lock:
            if self.sock:
                try:
                    self.sock.close()
                except:
                    pass
                self.sock = None
