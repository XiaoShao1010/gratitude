from maix import camera, display, app, nn, image
from gratitude.config import MODEL_PATH, OBSTACLE_WHITELIST, PRIORITY_COLORS
from gratitude.interactor import Interactor

def main():
    # 1. 初始化
    detector = nn.YOLO11(model=MODEL_PATH)
    interactor = Interactor(detector)
    cam = camera.Camera(detector.input_width(), detector.input_height(), detector.input_format())
    disp = display.Display()

    print("项目 Gratitude 已启动 (根目录入口)...")

    # 模拟用户查询 (可改为语音输入)
    target_to_find = "apple" 

    while not app.need_exit():
        img = cam.read()
        objs = detector.detect(img, conf_th=0.4, iou_th=0.45)
        img_w = img.width()

        for obj in objs:
            class_name = detector.labels[obj.class_id] if detector.labels else str(obj.class_id)
            
            # --- 模式 A: 安全检测 ---
            if class_name in OBSTACLE_WHITELIST:
                priority = OBSTACLE_WHITELIST[class_name]
                box_color = PRIORITY_COLORS.get(priority, image.COLOR_WHITE)
                
                # --- 模式 B: 交互定位 ---
                if class_name == target_to_find:
                    direction, v_pos, dist = interactor.get_spatial_feedback(obj, img_w)
                    feedback = interactor.format_voice_report(class_name, direction, v_pos, dist)
                    print(f"[VOICE]: {feedback}")
                    
                    img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=image.COLOR_GREEN, thickness=4)
                    img.draw_string(obj.x, obj.y - 20, feedback, color=image.COLOR_GREEN)
                else:
                    img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=box_color, thickness=2)
                    img.draw_string(obj.x, obj.y - 15, f"{class_name}", color=box_color)

        disp.show(img)

if __name__ == "__main__":
    main()
