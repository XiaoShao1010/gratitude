import socket
import json
import time

# 指定 MaixCam 的局域网 IP
SERVER_IP = "10.0.51.99"
PORT = 12345

def run_client():
    print(f"=== [树莓派 局域网测试客户端] ===")
    print(f"正在尝试连接 MaixCam 局域网 IP: {SERVER_IP}:{PORT}")
    
    while True:
        try:
            client_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            client_sock.connect((SERVER_IP, PORT))
            print("连接成功！正在解析来自 MaixCam 的视觉模拟数据...")
            
            # 使用文件句柄按行处理
            f = client_sock.makefile('r', encoding='utf-8')
            
            while True:
                line = f.readline()
                if not line: break
                
                try:
                    data = json.loads(line)
                    print(f"[{data['time']}] 收到障碍物: {data['obj']}, 距离: {data['dist']:.2f}m")
                except json.JSONDecodeError:
                    print(f"数据异常: {line.strip()}")
            
        except Exception as e:
            print(f"连接失败: {e}. 3秒后尝试重新连接...")
            time.sleep(3)

if __name__ == "__main__":
    run_client()
