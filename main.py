import socket
import json
import time
import threading
from maix import camera, display, app, nn, image
from gratitude.config import MODEL_PATH, OBSTACLE_WHITELIST, PRIORITY_COLORS
from gratitude.interactor import Interactor

# 虚拟网卡配置
SERVER_IP = "192.168.233.1"
SERVER_PORT = 12345

# 全局变量，用于线程间通信
target_to_find = ""
client_conn = None

def command_receiver_thread():
    """独立线程：专门负责监听树莓派发来的寻找指令"""
    global target_to_find, client_conn
    while not app.need_exit():
        if client_conn:
            try:
                # 阻塞式读取，直到收到数据或连接断开
                data = client_conn.recv(1024).decode('utf-8').strip()
                if data:
                    msg = json.loads(data)
                    if msg.get("type") == "search":
                        target_to_find = msg.get("object", "")
                        print(f"[线程反馈] 收到寻找目标指令: {target_to_find}")
            except (socket.timeout, BlockingIOError):
                time.sleep(0.1)
                continue
            except Exception as e:
                print(f"[线程反馈] 连接异常断开: {e}")
                client_conn = None
        else:
            time.sleep(1.0) # 等待新连接

def main():
    global target_to_find, client_conn
    
    # 1. 初始化视觉组件
    detector = nn.YOLO11(model=MODEL_PATH)
    interactor = Interactor(detector)
    cam = camera.Camera(detector.input_width(), detector.input_height(), detector.input_format())
    disp = display.Display()

    # 2. 建立原生 TCP 服务端 (监听 192.168.233.1)
    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind((SERVER_IP, SERVER_PORT))
    server_sock.listen(1)
    server_sock.settimeout(1.0) # 允许 accept 超时以便检查 app.need_exit()
    
    print(f"MaixCam 服务端已在 {SERVER_IP}:{SERVER_PORT} 启动 (Plan1 架构)")

    # 3. 启动指令接收线程
    recv_thread = threading.Thread(target=command_receiver_thread, daemon=True)
    recv_thread.start()

    last_send_time = time.time()

    while not app.need_exit():
        # --- 3.1 维护连接句柄 ---
        if client_conn is None:
            try:
                client_conn, addr = server_sock.accept()
                client_conn.settimeout(0.5) # 设置接收超时，防止线程死锁
                print(f"树莓派已成功握手: {addr}")
            except socket.timeout:
                pass

        # --- 3.2 视觉处理与推理 ---
        img = cam.read()
        objs = detector.detect(img, conf_th=0.4, iou_th=0.45)
        img_w, img_h = img.width(), img.height()

        highest_priority_obj = None
        min_prio = 999

        for obj in objs:
            class_name = detector.labels[obj.class_id] if detector.labels else str(obj.class_id)
            
            # 模式 B: 实时反馈 (由独立线程更新的 target_to_find 触发)
            if target_to_find and class_name == target_to_find:
                direction, v_pos, dist = interactor.get_spatial_feedback(obj, img_w, img_h)
                img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=image.COLOR_GREEN, thickness=4)
                img.draw_string(obj.x, obj.y - 20, f"FIND: {class_name}", color=image.COLOR_GREEN)

            # 模式 A: 搜索最高优先级障碍物
            if class_name in OBSTACLE_WHITELIST:
                prio = OBSTACLE_WHITELIST[class_name]
                img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=PRIORITY_COLORS.get(prio, image.COLOR_WHITE), thickness=2)
                if prio < min_prio:
                    min_prio = prio
                    highest_priority_obj = obj

        # --- 3.3 数据打包发送 (严格遵循 Plan1 格式) ---
        if client_conn and (time.time() - last_send_time > 0.4): # 约 2.5Hz
            if highest_priority_obj:
                name = detector.labels[highest_priority_obj.class_id]
                _, _, dist = interactor.get_spatial_feedback(highest_priority_obj, img_w, img_h)
                
                # 严格按照 {"type": "warning", "object": "...", "distance": ...}
                payload = {
                    "type": "warning",
                    "object": name,
                    "distance": round(dist, 1)
                }
                try:
                    client_conn.sendall((json.dumps(payload) + "\n").encode('utf-8'))
                    last_send_time = time.time()
                except Exception:
                    client_conn = None # 发生错误重置连接

        disp.show(img)

if __name__ == "__main__":
    main()
