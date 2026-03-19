# Phase 3: 宏观导航与手机代理协同

**日期:** 2026-03-19
**状态:** ✅ 完成

---

## 1. 系统架构

采用**主从协同架构**，树莓派作为感知与决策中枢，手机作为高算力导航执行引擎：

```
┌─────────────┐     蓝牙 RFCOMM     ┌─────────────┐
│   手机 App   │ ◄───────────────── │  树莓派 Pi   │
│  (Server)   │  UUID: 00001101... │  (Client)   │
│             │                    │             │
│  高德导航    │ ─────────────────► │  MiniMind   │
│  onNaviInfo │  {"status":"TURN_  │  决策分发    │
└─────────────┘       LEFT",...}    └─────────────┘
                                           │
                                           │ USB TCP
                                           ▼
                                      ┌─────────┐
                                      │ MaixCam │
                                      └─────────┘
```

---

## 2. 新增文件

### 树莓派端 (`pi_node/`)

| 文件 | 说明 |
|------|------|
| `core/phone_comm.py` | 蓝牙 RFCOMM 客户端 — 向手机发送导航指令，接收导航状态回调 |
| `core/navigator.py` | 导航编排器 — `extract_intent()`, `send_nav_request_to_phone()`, `listen_phone_status()`, `adjust_maixcam_mode()` |
| `core/network_client.py` | 新增 `send_mode()` 方法 — 向 MaixCam 发送模式切换指令 |
| `requirements.txt` | 新增 `pybluez` 依赖 |

### 手机代理端 (`phone_agent/`)

| 文件 | 说明 |
|------|------|
| `gradle/wrapper/` | Gradle 8.4 wrapper 配置 |
| `settings.gradle` / `build.gradle` | Android 项目配置 |
| `app/build.gradle` | Android SDK 34，高德导航 SDK 依赖 |
| `AndroidManifest.xml` | 蓝牙权限、后台服务、位置权限 |
| `app/src/main/java/.../MainActivity.java` | 入口 Activity |
| `app/src/main/java/.../navi/SocketServerService.java` | 蓝牙 RFCOMM Server，接收 Pi 请求并触发导航 |
| `app/src/main/java/.../navi/GaodeNaviManager.java` | 高德地图步行导航 + GeocodeSearch 地理编码 |
| `app/src/main/res/layout/activity_main.xml` | 布局文件 |
| `app/src/main/res/values/` | 字符串、主题资源 |

### 知识库 (`AGENTS.md`)

| 文件 | 说明 |
|------|------|
| `AGENTS.md` | 根级项目知识库 |
| `cam_node/AGENTS.md` | MaixCam 节点知识库 |
| `pi_node/AGENTS.md` | 树莓派节点知识库 |

---

## 3. 通信协议 (Pi ↔ Phone)

### Request (Pi → Phone)
```json
{"action": "START_NAV", "target": "文三路地铁站"}
```

### Response (Phone → Pi)
```json
{"status": "NAV_ACTIVE", "event": "TURN_LEFT", "distance": 15}
{"status": "ARRIVED"}
```

---

## 4. 核心 API

### 树莓派端

```python
# 意图解析
def extract_intent(user_voice_text: str) -> dict:
    # 调用本地大模型，输出 {"intent": "navigate", "target": "地铁站"}

# 发送导航请求
def send_nav_request_to_phone(destination: str) -> bool:
    # 蓝牙发送 JSON，请求手机开始导航

# 监听手机状态
def listen_phone_status():
    # 收到 TURN_LEFT/TURN_RIGHT/AHEAD 时，调用 adjust_maixcam_mode()
    # 收到 ARRIVED 时，语音播报"已到达目的地"

# MaixCam 模式控制
def adjust_maixcam_mode(nav_status: str):
    # normal / turn_ahead / arrived
```

### 手机代理端

```java
// 地理编码：将文本地址转为经纬度
void geocodeAndNavigate(String address)

// 核心算路与导航启动
void calculateWalkRoute(double endLon, double endLat)

// 高德 SDK 回调：状态回传给树莓派
void onNaviInfoUpdate(NaviInfo naviInfo)
```

---

## 5. 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| 传输层 | 蓝牙 RFCOMM | 无需 IP/配网，移动场景更稳定，功耗低 |
| 手机导航 | 高德地图步行导航 SDK | 国内导航数据准确，API 成熟 |
| 树莓派蓝牙 | pybluez (BlueZ) | Linux 原生蓝牙栈，Python 绑定完善 |
| UUID | `00001101-0000-1000-8000-00805F9B34FB` | 标准串口服务 UUID，跨设备通用 |

---

## 6. 待配置项

- [ ] 树莓派：`pip install pybluez`
- [ ] 手机蓝牙名设为 `BlindNav-Phone`，或修改 `pi_node/main.py` 中的 `PHONE_NAME`
- [ ] 高德 SDK Key：替换 `phone_agent/app/build.gradle` 中的 `[latestVersion]`
- [ ] 首次使用需在手机上完成蓝牙配对

---

## 7. 下一步

- Phase 4：MaixCam 根据导航状态动态调整检测策略（升维/降维）
