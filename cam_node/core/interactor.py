from config import REAL_HEIGHTS, FOCAL_LENGTH

class Interactor:
    def __init__(self, detector):
        self.detector = detector

    def get_spatial_feedback(self, obj, img_width, img_height):
        """计算物体在空间中的方位和距离"""
        center_x = obj.x + obj.w / 2
        relative_x = center_x / img_width
        
        # 1. 判定水平方位
        if relative_x < 0.33:
            direction = "左边"
        elif relative_x > 0.66:
            direction = "右边"
        else:
            direction = "正前方"
            
        # 2. 判定垂直方位
        center_y = obj.y + obj.h / 2
        height_ratio = center_y / img_height 
        v_pos = "高处" if height_ratio < 0.3 else ("低处" if height_ratio > 0.7 else "")

        # 3. 估算距离
        class_name = self.detector.labels[obj.class_id]
        real_h = REAL_HEIGHTS.get(class_name, 1000)
        distance = (FOCAL_LENGTH * real_h) / obj.h / 1000.0 # 米
        
        return direction, v_pos, distance

    def format_voice_report(self, class_name, direction, v_pos, dist):
        return f"{class_name}在{v_pos}{direction}，约{dist:.1f}米"
