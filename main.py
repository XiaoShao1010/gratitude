import json
import time
from maix import camera, display, app, nn, image, comm
from gratitude.config import MODEL_PATH, OBSTACLE_WHITELIST, PRIORITY_COLORS
from gratitude.interactor import Interactor

# 定义自定义命令 ID (根据协议 0x01-0xC8 均可)
CMD_OBSTACLE_WARNING = 0x10  # 障碍物报警指令
CMD_OBJECT_SEARCH = 0x11    # 物体搜索指令

# 全局查询目标
target_to_find = ""

def on_received_data(protocol, cmd, body):
    """协议回调：处理来自树莓派的请求"""
    global target_to_find
    if cmd == CMD_OBJECT_SEARCH:
        try:
            # 假设树莓派发来的是 UTF-8 编码的物体名称
            target_to_find = body.decode('utf-8')
            print(f"[协议] 收到查询指令: 寻找 {target_to_find}")
            # 返回一个 RESP_OK (Flags 会由库自动处理为 0xC1)
            return comm.RESP_OK
        except:
            return comm.RESP_ERR
    return comm.RESP_ERR

def main():
    global target_to_find
    
    # 1. 初始化检测
    detector = nn.YOLO11(model=MODEL_PATH)
    interactor = Interactor(detector)
    cam = camera.Camera(detector.input_width(), detector.input_height(), detector.input_format())
    disp = display.Display()

    # 2. 初始化标准通讯协议 (使用 TCP 模式，端口 5555)
    # 根据 protocol.md，MaixCam 默认开启 5555 端口监听
    # 我们使用 CommProtocol 来自动处理 帧头(AA CA AC BB) 和 CRC16
    prot = comm.CommProtocol(port="tcp://0.0.0.0:5555", is_server=True)
    prot.add_listener(on_received_data)
    print("Maix 协议服务已启动 (TCP:5555)，等待树莓派连接...")

    last_send_time = time.time()

    while not app.need_exit():
        img = cam.read()
        objs = detector.detect(img, conf_th=0.4, iou_th=0.45)
        img_w, img_h = img.width(), img.height()

        highest_priority_obj = None
        min_prio = 999

        for obj in objs:
            class_name = detector.labels[obj.class_id] if detector.labels else str(obj.class_id)
            
            # --- 模式 B: 交互定位 ---
            if target_to_find and class_name == target_to_find:
                direction, v_pos, dist = interactor.get_spatial_feedback(obj, img_w, img_h)
                feedback = interactor.format_voice_report(class_name, direction, v_pos, dist)
                img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=image.COLOR_GREEN, thickness=4)
                img.draw_string(obj.x, obj.y - 20, feedback, color=image.COLOR_GREEN)

            # --- 模式 A: 搜寻最高优先级障碍物 ---
            if class_name in OBSTACLE_WHITELIST:
                priority = OBSTACLE_WHITELIST[class_name]
                img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=PRIORITY_COLORS.get(priority, image.COLOR_WHITE), thickness=2)
                if priority < min_prio:
                    min_prio = priority
                    highest_priority_obj = obj

        # --- 通信逻辑：按照标准协议发送“主动上报” (Active Report) ---
        # 协议要求：Flags 最高位 is_resp=1，第三位 is_report=1
        if last_send_time and (time.time() - last_send_time > 0.5):
            if highest_priority_obj:
                class_name = detector.labels[highest_priority_obj.class_id]
                direction, _, dist = interactor.get_spatial_feedback(highest_priority_obj, img_w, img_h)
                
                # 构造 Body (这里我们依然可以使用 JSON 字符串作为 Body 内容，或者使用纯二进制)
                # 协议 Body 长度 < 2^32-1
                payload = {
                    "obj": class_name,
                    "prio": min_prio,
                    "dir": direction,
                    "dist": round(dist, 1)
                }
                body_data = json.dumps(payload).encode('utf-8')
                
                # 发送主动上报帧
                # report 方法会自动设置 Flags=0xE1 (is_resp=1, resp_ok=1, is_report=1, v=1)
                prot.report(CMD_OBSTACLE_WARNING, body_data)
                last_send_time = time.time()

        disp.show(img)

if __name__ == "__main__":
    main()
