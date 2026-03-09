from maix import camera, display, app, nn, image
import socket
import select
from gratitude.config import MODEL_PATH, OBSTACLE_WHITELIST, PRIORITY_COLORS, PI_IP, PI_PORT
from gratitude.interactor import Interactor

def init_network():
    """初始化非阻塞的 TCP 客户端"""
    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client.setblocking(False) # 开启非阻塞模式，防止网络卡顿拖死 NPU 帧率
    try:
        client.connect((PI_IP, PI_PORT))
    except BlockingIOError:
        pass # 非阻塞模式下 connect 立即返回是正常现象
    except Exception as e:
        print(f"网络连接失败: {e}")
        return None
    return client

def main():
    detector = nn.YOLO11(model=MODEL_PATH)
    interactor = Interactor(detector)
    cam = camera.Camera(detector.input_width(), detector.input_height(), detector.input_format())
    disp = display.Display()
    client = init_network()

    print("项目 Gratitude (边缘节点) 已启动...")
    
    # 默认不寻找任何特定物体，直到树莓派下发指令
    target_to_find = None 

    while not app.need_exit():
        # --- 1. 网络接收：异步读取树莓派的指令 ---
        if client:
            try:
                # 使用 select 监听，超时设为极小的 0.005 秒，绝不阻塞主循环
                readable, _, _ = select.select([client], [], [], 0.005)
                if readable:
                    data = client.recv(1024).decode().strip()
                    if data:
                        target_to_find = data
                        print(f"[网络] 收到树莓派指令，开始寻找: {target_to_find}")
            except Exception:
                pass

        # --- 2. 视觉推理 ---
        img = cam.read()
        objs = detector.detect(img, conf_th=0.4, iou_th=0.45)
        img_w, img_h = img.width(), img.height()

        highest_priority = 999
        primary_warning = None

        for obj in objs:
            class_name = detector.labels[obj.class_id] if detector.labels else str(obj.class_id)
            
            if class_name in OBSTACLE_WHITELIST:
                priority = OBSTACLE_WHITELIST[class_name]
                box_color = PRIORITY_COLORS.get(priority, image.COLOR_WHITE)
                
                # --- 模式 B: 交互定位 (优先级最高) ---
                if target_to_find and class_name == target_to_find:
                    direction, v_pos, dist = interactor.get_spatial_feedback(obj, img_w, img_h)
                    
                    # 画面渲染
                    img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=image.COLOR_GREEN, thickness=4)
                    img.draw_string(obj.x, obj.y - 20, f"Target: {dist:.1f}m", color=image.COLOR_GREEN)
                    
                    # 将寻找结果发回树莓派 (格式: FIND,apple,正前方,1.5)
                    if client:
                        msg = f"FIND,{class_name},{direction},{dist:.1f}\n"
                        try: client.send(msg.encode())
                        except: pass
                
                # --- 模式 A: 安全检测 ---
                else:
                    img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=box_color, thickness=2)
                    img.draw_string(obj.x, obj.y - 15, class_name, color=box_color)
                    
                    # 记录当前视野中最危险的物体
                    if priority < highest_priority:
                        highest_priority = priority
                        direction, _, dist = interactor.get_spatial_feedback(obj, img_w, img_h)
                        # 只有当高危物体距离小于 2.5 米时，才生成警告数据
                        if dist < 2.5: 
                            primary_warning = f"WARN,{class_name},{direction},{dist:.1f}\n"

        # --- 3. 网络发送：将最高危险预警发给树莓派 ---
        if primary_warning and client:
            try: client.send(primary_warning.encode())
            except: pass

        disp.show(img)

if __name__ == "__main__":
    main()