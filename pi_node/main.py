import time
from core.network_client import NetworkClient
from core.audio_engine import AudioEngine
from core.llm_engine import LLMEngine

# ✅ 修正 1：换成真实打通的 IP 和端口
MAIX_CAM_IP = "10.43.210.1" 
MAIX_CAM_PORT = 8080
LLM_MODEL_PATH = "./models/intent_brain_26m_512.pth"

def main():
    audio = AudioEngine()
    llm = LLMEngine(LLM_MODEL_PATH)
    network = NetworkClient(MAIX_CAM_IP, MAIX_CAM_PORT)

    def on_warning(obj, dist):
        if dist < 2.5:
            msg = f"注意，前方{dist}米有{obj}"
            audio.speak(msg)

    network.warning_callback = on_warning
    network.connect()

    print("\n[Raspberry Pi Node] Started. Listening for voice commands...")
    
    try:
        while True:
            user_text = input("\n>> 用户语音输入模拟: ").strip()
            
            if user_text:
                # ✅ 修正 2：调用真实大模型，直接拿到双重结果
                intent, target = llm.parse_command(user_text)
                
                if intent == "find_object" and target and target != "null":
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
        print("[Raspberry Pi Node] Stopped.")

if __name__ == "__main__":
    main()