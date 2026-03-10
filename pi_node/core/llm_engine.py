import torch
import json
import time
from transformers import AutoTokenizer
from models.model_minimind import MiniMindConfig, MiniMindForCausalLM

class LLMEngine:
    def __init__(self, model_path):
        self.device = "cpu"
        self.tokenizer_path = "./models/MiniMind2"
        
        print(f"[LLM] ⏳ 正在唤醒 26M 意图大脑 ({model_path})...")
        self.tokenizer = AutoTokenizer.from_pretrained(self.tokenizer_path)
        
        config = MiniMindConfig(hidden_size=512, num_hidden_layers=8, vocab_size=self.tokenizer.vocab_size)
        self.model = MiniMindForCausalLM(config)
        
        self.model.load_state_dict(torch.load(model_path, map_location=self.device), strict=False)
        self.model.to(self.device).eval()
        torch.set_num_threads(4) 
        print("[LLM] ✅ 大脑完全就绪！")

    def parse_command(self, text):
        """真正的 26M 大模型推理逻辑"""
        system_msg = (
            "你是一个专为视障人士设计的智能导航助手。你的任务是从用户的语音指令中，"
            "提取出核心的'intent(意图)'和'target(寻找目标)'，并以严格的JSON格式输出。\n"
            "支持的意图：find_object(寻找物体), navigate(导航), chat(日常闲聊)。\n"
            "如果目标不在列表内，target输出'null'。"
        )
        user_content = f"{system_msg}\n用户指令：{text}\nJSON输出："
        messages = [{"role": "user", "content": user_content}]
        
        input_str = self.tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        input_ids = self.tokenizer(input_str, return_tensors="pt").input_ids.to(self.device)
        
        print(f"[LLM] 思考中...")
        t1 = time.time()
        with torch.no_grad():
            outputs = self.model.generate(
                input_ids, max_new_tokens=64, do_sample=False,
                pad_token_id=self.tokenizer.pad_token_id, eos_token_id=self.tokenizer.eos_token_id
            )
        t2 = time.time()
        
        response_str = self.tokenizer.decode(outputs[0][len(input_ids[0]):], skip_special_tokens=True)
        print(f"[LLM] 推理耗时: {t2-t1:.2f}s | 输出: {response_str}")
        
        try:
            data = json.loads(response_str)
            return data.get("intent"), data.get("target")
        except:
            print("[LLM] JSON 解析失败，格式异常！")
            return "chat", "null"