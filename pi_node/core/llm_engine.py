import torch
# 假设 model_minimind.py 在 pi_node/models/ 目录下
# from pi_node.models.model_minimind import MiniMind

class LLMEngine:
    def __init__(self, model_path):
        self.model_path = model_path
        print(f"[LLM] Loading Intent Brain: {model_path}")
        # TODO: 实际加载 MiniMind 推理逻辑
        # self.model = MiniMind(...)
        # self.model.load_state_dict(torch.load(model_path))

    def get_intent(self, text):
        """意图识别推理"""
        # --- 示例映射逻辑 (占位符) ---
        if "哪里" in text or "找" in text:
            return "search"
        if "前面" in text or "有什么" in text:
            return "describe"
        return "chat"

    def extract_object(self, text):
        """从语音文本中提取核心查询目标"""
        # 简单示例逻辑，实际由 MiniMind 完成
        targets = ["苹果", "汽车", "人", "公交站"]
        for t in targets:
            if t in text: return t
        return ""
