import time
from core.network_client import NetworkClient
from core.audio_engine import AudioEngine
from core.llm_engine import LLMEngine
from core.phone_bridge import PhoneBridge
from core.navigator import Navigator

# 蓝牙配置：对端手机蓝牙名称（仅用于日志提示）
PHONE_BT_NAME = "BlindNav-Phone"
MAIX_CAM_IP = "10.43.210.1"
MAIX_CAM_PORT = 8080
LLM_MODEL_PATH = "./models/intent_brain_26m_512.pth"

def main():
    audio = AudioEngine()
    llm = LLMEngine(LLM_MODEL_PATH)
    network = NetworkClient(MAIX_CAM_IP, MAIX_CAM_PORT)
    phone = PhoneBridge(PHONE_BT_NAME)
    navigator = Navigator(llm, phone, network, audio)

    def on_warning(obj, dist):
        if dist < 2.5:
            msg = f"注意，前方{dist}米有{obj}"
            audio.speak(msg)

    network.warning_callback = on_warning
    network.connect()
    phone.connect()
    navigator.listen_phone_status()

    print("\n[Raspberry Pi Node] Started. Listening for voice commands...")
    
    try:
        while True:
            user_text = input("\n>> 用户语音输入模拟: ").strip()
            
            if user_text:
                result = navigator.extract_intent(user_text)
                intent = result["intent"]
                target = result["target"]
                
                if intent == "navigate" and target and target != "null":
                    navigator.send_nav_request_to_phone(target)
                elif intent == "find_object" and target and target != "null":
                    audio.speak(f"好的，正在为您寻找{target}")
                    network.send_search(target)
                elif intent == "chat":
                    audio.speak("我在这里，随时听候您的吩咐。")
                else:
                    audio.speak("没听清具体目标，能再说一次吗？")

            time.sleep(0.1)

    except KeyboardInterrupt:
        pass
    finally:
        network.close()
        phone.close()
        print("[Raspberry Pi Node] Stopped.")

if __name__ == "__main__":
    main()