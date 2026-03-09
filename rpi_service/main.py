import socket
import json
import os
import time

MAIX_CAM_IP = "192.168.233.1"
MAIX_CAM_PORT = 12345

def speak(text):
    """异步调用 espeak 播报语音"""
    print(f"[TTS] 播报: {text}")
    # 树莓派需安装 sudo apt-get install espeak
    os.system(f'espeak -v zh "{text}" &')

def main():
    while True:
        try:
            print(f"正在尝试连接 MaixCam @ {MAIX_CAM_IP}:{MAIX_CAM_PORT}...")
            client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            client.connect((MAIX_CAM_IP, MAIX_CAM_PORT))
            print("连接成功！接收视觉警报中...")
            
            # 使用 makefile 简化按行读取 JSON
            f = client.makefile('r', encoding='utf-8')
            
            while True:
                line = f.readline()
                if not line: break
                
                try:
                    # 按照 Plan1 格式解析
                    data = json.loads(line)
                    if data.get("type") == "warning":
                        distance = data.get("distance", 0)
                        obj_name = data.get("object", "障碍物")
                        
                        # 安全阈值设定：2.5米内报警
                        if distance < 2.5:
                            msg = f"注意，前方{distance}米有{obj_name}"
                            speak(msg)
                except json.JSONDecodeError:
                    continue
                    
        except Exception as e:
            print(f"连接出错: {e}. 3秒后重试...")
            time.sleep(3)

if __name__ == "__main__":
    main()
