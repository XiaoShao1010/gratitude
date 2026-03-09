# Gratitude - 树莓派中枢控制端 (RPi Service)

该项目是 "Gratitude" 视障人士辅助导航系统的中枢控制端。它通过 USB 虚拟网卡与 MaixCam 连接，处理来自 MaixCam 的障碍物警报并进行语音播报（TTS）。

## 核心功能
1. **协议 Master 端**: 建立标准二进制协议帧解析（含帧头、CRC16）。
2. **语音播报**: 根据警报优先级实时触发中文语音预警。
3. **寻找指令**: 接收用户在终端输入的物体名称，并下发给 MaixCam 进行精准定位。

## 部署步骤

### 1. 硬件连接
- 将 MaixCam 通过 USB 线连接至树莓派。
- 确保树莓派中 `ifconfig` 能看到 `usb0` 网卡，且能 `ping 192.168.233.1` 通。

### 2. 环境准备 (树莓派端执行)
```bash
# 更新并安装音频播放引擎
sudo apt-get update
sudo apt-get install espeak

# 进入项目目录
cd rpi_service
```

### 3. 运行程序
确保 MaixCam 上的 `main.py` 已启动，然后在树莓派上运行：
```bash
python3 main.py
```

## 数据帧格式
- **帧头**: `AA CA AC BB`
- **默认端口**: `5555` (TCP)
