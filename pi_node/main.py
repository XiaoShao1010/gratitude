import time
from core.network_client import NetworkClient
from core.audio_engine import AudioEngine
from core.llm_engine import LLMEngine

MAIX_CAM_IP = "192.168.233.1"
MAIX_CAM_PORT = 12345
LLM_MODEL_PATH = "./models/intent_brain_26m_512.pth"

def main():
    # 1. 初始化引擎
    audio = AudioEngine()
    llm = LLMEngine(LLM_MODEL_PATH)
    network = NetworkClient(MAIX_CAM_IP, MAIX_CAM_PORT)

    # 2. 定义收到视觉警报时的回调函数
    def on_warning(obj, dist):
        if dist < 2.5: # 距离阈值判定
            msg = f"注意，前方{dist}米有{obj}"
            audio.speak(msg)

    network.warning_callback = on_warning
    
    # 3. 启动后台通讯
    network.connect()

    print("[Raspberry Pi Node] Started. Listening for voice commands...")
    
    try:
        while True:
            # 模拟语音监听过程 (此处将来接 ASR)
            # user_text = audio.listen()
            user_text = input(">> 用户语音输入模拟: ").strip() # 临时使用终端输入模拟
            
            if user_text:
                # 4. 意图识别 (LLM 推理)
                intent = llm.get_intent(user_text)
                print(f"[LLM] Detected Intent: {intent}")
                
                if intent == "search":
                    # 5. 提取并下发搜索指令至 MaixCam
                    target = llm.extract_object(user_text)
                    if target:
                        network.send_search(target)
                        audio.speak(f"好的，正在为您寻找{target}")
                
                elif intent == "describe":
                    audio.speak("前方视野内有行人和车辆，请小心行走")

            time.sleep(0.1)

    except KeyboardInterrupt:
        pass
    finally:
        network.close()
        print("[Raspberry Pi Node] Stopped.")

if __name__ == "__main__":
    main()
