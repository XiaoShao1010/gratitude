from maix import image

# 模型与硬件配置
# 注意：在 MaixCam 端，我们将模型放在 cam_node/models/ 目录下，
# 或者引用系统路径 /root/models/
MODEL_PATH = "/root/models/yolo11n_320_int8.cvimodel"
FOCAL_LENGTH = 600  # 像素焦距

# 虚拟网卡配置
SERVER_IP = "192.168.233.1"
SERVER_PORT = 12345

# 障碍物白名单与优先级
OBSTACLE_WHITELIST = {
    "car": 1, "motorcycle": 1, "bus": 1, "truck": 1,
    "fire hydrant": 2, "bench": 2, "stop sign": 2,
    "person": 3, "bicycle": 3, "dog": 3,
    "traffic light": 4, "apple": 5, "bottle": 5, "cup": 5
}

# 优先级颜色映射
PRIORITY_COLORS = {
    1: image.COLOR_RED,
    2: image.COLOR_ORANGE,
    3: image.COLOR_YELLOW,
    4: image.COLOR_GREEN,
    5: image.COLOR_WHITE
}

# 物体真实高度参考 (mm)
REAL_HEIGHTS = {
    "person": 1700, "car": 1500, "bus": 2800, "truck": 3000,
    "bicycle": 1000, "motorcycle": 1000, "fire hydrant": 800,
    "apple": 80, "bottle": 250, "cup": 120, "chair": 900
}
