阶段一：视觉与通信基座打通（预计耗时：1 周）
目标： 让 MaixCam 和树莓派成功“握手”，建立稳定且低延迟的双向数据流。
1. 硬件连接与网络配置：
• 用 USB 线连接 MaixCam 与树莓派。
• 在树莓派系统（Raspberry Pi OS）中配置虚拟网卡（通常为 usb0），确保能 ping 通 MaixCam 的默认 IP（如 192.168.233.1）。
2. 重构 MaixCam 端代码 (Socket Client)：
• 在现有的 main.py 中引入 socket 模块。
• 发送链路： 每帧或每 0.5 秒，将视野中“优先级最高”的危险障碍物打包成 JSON（如 {"type": "warning", "object": "car", "distance": 1.2}）发送出去。
• 接收链路： 开启一个非阻塞的接收线程，随时监听树莓派发来的寻找指令，动态更新 target_to_find 变量。
3. 编写树莓派中枢骨架 (Socket Server)：
• 在树莓派上用 Python 建立一个 TCP Server 监听端口。
• 编写基础的语音合成（TTS）函数（如使用 pyttsx3 或边沿端语音引擎）。收到 MaixCam 的警告 JSON 时，立刻触发语音播报：“注意，前方 1.2 米有汽车”。
• 里程碑 A： 拿着连接着树莓派的 MaixCam 走动，能听到树莓派实时播报危险障碍物。