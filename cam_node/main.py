import time
from maix import app
from cam_node.config import SERVER_IP, SERVER_PORT
from cam_node.core.vision_detector import VisionDetector
from cam_node.core.network_server import NetworkServer
from cam_node.core.interactor import Interactor

def main():
    # 1. 初始化各模块
    detector = VisionDetector()
    server = NetworkServer(SERVER_IP, SERVER_PORT)
    interactor = Interactor(detector.detector)
    
    # 2. 启动通讯服务
    server.start()
    
    print("[MaixCam Node] Running...")
    last_report_time = time.time()

    try:
        while not app.need_exit():
            # 3. 运行一帧检测与显示
            # 获取当前最高优先级物体
            highest_obj, min_prio = detector.run_frame(
                server.target_to_find, 
                interactor, 
                server
            )

            # 4. 异步上报危险障碍物 (约 2Hz)
            if highest_obj and (time.time() - last_report_time > 0.5):
                # 获取物体名称与距离
                name = detector.detector.labels[highest_obj.class_id]
                _, _, dist = interactor.get_spatial_feedback(
                    highest_obj, 
                    detector.cam.width(), 
                    detector.cam.height()
                )
                
                # 上报至树莓派
                server.send_warning(name, dist)
                last_report_time = time.time()

    except KeyboardInterrupt:
        pass
    finally:
        server.close()
        print("[MaixCam Node] Stopped.")

if __name__ == "__main__":
    main()
