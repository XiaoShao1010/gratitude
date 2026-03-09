import socket
import json
import time

# MaixCam 监听所有可用网卡
HOST = "0.0.0.0" 
PORT = 12345

def run_server():
    print(f"=== [MaixCam 局域网测试服务端] ===")
    print(f"正在监听端口 {PORT}，请确保树莓派连接到 10.0.51.99")
    
    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind((HOST, PORT))
    server_sock.listen(1)
    
    conn, addr = server_sock.accept()
    print(f"树莓派已接入: {addr}")

    try:
        count = 1
        while True:
            # 模拟 YOLO 的 JSON 数据
            payload = {
                "id": count,
                "msg": "LAN Test from MaixCam",
                "obj": "person",
                "dist": 1.5 + (count % 10) / 10.0,
                "time": time.strftime("%H:%M:%S")
            }
            data_str = json.dumps(payload) + "\n"
            conn.sendall(data_str.encode('utf-8'))
            print(f"已发送 [{count}]: {data_str.strip()}")
            count += 1
            time.sleep(1)
    except Exception as e:
        print(f"连接中断: {e}")
    finally:
        conn.close()
        server_sock.close()

if __name__ == "__main__":
    run_server()
