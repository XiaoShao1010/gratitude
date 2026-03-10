from maix import nn, camera, display, app, image
from cam_node.config import MODEL_PATH, OBSTACLE_WHITELIST, PRIORITY_COLORS

class VisionDetector:
    def __init__(self):
        # 1. 初始化 YOLO 模型
        print(f"[Vision] Loading model: {MODEL_PATH}")
        self.detector = nn.YOLO11(model=MODEL_PATH)
        self.cam = camera.Camera(self.detector.input_width(), self.detector.input_height(), self.detector.input_format())
        self.disp = display.Display()

    def run_frame(self, target_to_find, interactor, network_server):
        """运行单帧推理与显示"""
        img = self.cam.read()
        objs = self.detector.detect(img, conf_th=0.4, iou_th=0.45)
        img_w, img_h = img.width(), img.height()

        highest_priority_obj = None
        min_prio = 999

        for obj in objs:
            class_name = self.detector.labels[obj.class_id] if self.detector.labels else str(obj.class_id)
            
            # --- 模式 B: 交互定位 ---
            if target_to_find and class_name == target_to_find:
                direction, v_pos, dist = interactor.get_spatial_feedback(obj, img_w, img_h)
                img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=image.COLOR_GREEN, thickness=4)
                img.draw_string(obj.x, obj.y - 20, f"FINDING: {class_name}", color=image.COLOR_GREEN)

            # --- 模式 A: 搜索最高优先级障碍物 ---
            if class_name in OBSTACLE_WHITELIST:
                priority = OBSTACLE_WHITELIST[class_name]
                img.draw_rect(obj.x, obj.y, obj.w, obj.h, color=PRIORITY_COLORS.get(priority, image.COLOR_WHITE), thickness=2)
                if priority < min_prio:
                    min_prio = priority
                    highest_priority_obj = obj

        self.disp.show(img)
        return highest_priority_obj, min_prio
