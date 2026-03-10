import socket

# 监听所有网卡接口的 8080 端口
HOST = '0.0.0.0'
PORT = 8080

def main():
    # 创建 TCP Socket
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # 防止端口被系统保留报错
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    server.bind((HOST, PORT))
    server.listen(1) # 只接受一个测试连接

    print(f"🟢 MaixCAM 测试服务端已启动！")
    print(f"等待树莓派连接 (端口: {PORT})...")

    while True:
        try:
            # 阻塞等待树莓派连接
            conn, addr = server.accept()
            print(f"\n🎉 成功建立连接！树莓派的 IP 地址是: {addr[0]}")
            
            # 接收树莓派发来的数据
            data = conn.recv(1024).decode('utf-8')
            print(f"📥 收到树莓派的消息: {data}")
            
            if data:
                # 构造一条回复发回去
                reply_msg = f"MaixCAM 已收到你的消息！(你发送的是: {data})"
                conn.sendall(reply_msg.encode('utf-8'))
                print("📤 已向树莓派发送确认回执。")
                
        except Exception as e:
            print(f"❌ 通信出错: {e}")
        finally:
            conn.close()

if __name__ == "__main__":
    main()