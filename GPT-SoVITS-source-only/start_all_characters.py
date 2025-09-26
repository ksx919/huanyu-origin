#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
原神角色语音 API 服务启动脚本
Python 版本 - 提供更高级的进程管理功能
"""

import subprocess
import time
import sys
import os
import signal
from typing import List, Dict, Optional

class GenshinVoiceAPIManager:
    def __init__(self):
        self.processes: List[subprocess.Popen] = []
        self.characters = [
            {
                "name": "Yoimiya",
                "display_name": "宵宫",
                "port": 5000,
                "sovits_path": "SoVITS_weights_v2/Yoimiya_e8_s96.pth",
                "gpt_path": "GPT_weights_v2/Yoimiya-e15.ckpt",
                "ref_audio_path": "yoimiya/reference_audios/中文/emotions/【默认】哇，你做点心的手艺很不一般啊！去祭典上摆摊的话，肯定会成为最热门的那一个吧！.wav",
                "ref_text": "哇，你做点心的手艺很不一般啊！去祭典上摆摊的话，肯定会成为最热门的那一个吧！"
            },
            {
                "name": "Venti",
                "display_name": "温迪",
                "port": 5001,
                "sovits_path": "SoVITS_weights_v2/Venti_e8_s96.pth",
                "gpt_path": "GPT_weights_v2/Venti-e15.ckpt",
                "ref_audio_path": "venti/reference_audios/中文/emotions/【默认】至少她没有否认——大教堂里收藏着天空之琴。.wav",
                "ref_text": "至少她没有否认——大教堂里收藏着天空之琴。"
            },
            {
                "name": "HuTao",
                "display_name": "胡桃",
                "port": 5002,
                "sovits_path": "SoVITS_weights_v2/Hutao_e8_s120.pth",
                "gpt_path": "GPT_weights_v2/HuTao-e15.ckpt",
                "ref_audio_path": "hutao/reference_audios/中文/emotions/【默认】嘿嘿，毕竟找活人不是我擅长的事嘛，如果让我找的是「边界」另一边的人….wav",
                "ref_text": "嘿嘿，毕竟找活人不是我擅长的事嘛，如果让我找的是「边界」另一边的人…."
            }
        ]
    
    def print_banner(self):
        """打印启动横幅"""
        print("=" * 50)
        print("🎮 原神角色语音 API 服务管理器")
        print("=" * 50)
        print()
    
    def build_command(self, character: Dict) -> List[str]:
        """构建启动命令"""
        return [
            "python", "api.py",
            "-s", character["sovits_path"],
            "-g", character["gpt_path"],
            "-dr", character["ref_audio_path"],
            "-dt", character["ref_text"],
            "-dl", "zh",
            "-d", "cuda",
            "-a", "127.0.0.1",
            "-p", str(character["port"])
        ]
    
    def start_character_service(self, character: Dict) -> Optional[subprocess.Popen]:
        """启动单个角色的 API 服务"""
        print(f"🚀 正在启动 {character['display_name']} ({character['name']}) API 服务 (端口: {character['port']})...")
        
        try:
            command = self.build_command(character)
            process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                universal_newlines=True
            )
            
            # 等待一小段时间检查进程是否成功启动
            time.sleep(2)
            if process.poll() is None:
                print(f"✅ {character['display_name']} 服务启动成功！")
                return process
            else:
                print(f"❌ {character['display_name']} 服务启动失败！")
                return None
                
        except Exception as e:
            print(f"❌ 启动 {character['display_name']} 服务时出错: {e}")
            return None
    
    def start_all_services(self):
        """启动所有角色的 API 服务"""
        self.print_banner()
        
        for character in self.characters:
            process = self.start_character_service(character)
            if process:
                self.processes.append(process)
            
            # 在启动下一个服务前等待
            time.sleep(3)
        
        if self.processes:
            self.print_service_info()
            self.monitor_services()
        else:
            print("❌ 没有成功启动任何服务！")
    
    def print_service_info(self):
        """打印服务信息"""
        print()
        print("=" * 50)
        print("🎉 所有服务已启动完成！")
        print("=" * 50)
        print()
        
        print("📡 服务端口信息：")
        for character in self.characters:
            print(f"  - {character['display_name']} ({character['name']}): http://127.0.0.1:{character['port']}")
        
        print()
        print("🎵 流式音频端点：")
        for character in self.characters:
            print(f"  - {character['display_name']}: http://127.0.0.1:{character['port']}/stream")
        
        print()
        print("💡 使用说明：")
        print("  - 按 Ctrl+C 停止所有服务")
        print("  - 服务日志将显示在下方")
        print("=" * 50)
        print()
    
    def monitor_services(self):
        """监控服务状态"""
        try:
            print("🔍 正在监控服务状态... (按 Ctrl+C 停止)")
            while True:
                # 检查所有进程是否还在运行
                running_processes = []
                for i, process in enumerate(self.processes):
                    if process.poll() is None:
                        running_processes.append(process)
                    else:
                        character = self.characters[i]
                        print(f"⚠️  {character['display_name']} 服务已停止")
                
                self.processes = running_processes
                
                if not self.processes:
                    print("❌ 所有服务都已停止")
                    break
                
                time.sleep(5)
                
        except KeyboardInterrupt:
            print("\n🛑 收到停止信号，正在关闭所有服务...")
            self.stop_all_services()
    
    def stop_all_services(self):
        """停止所有服务"""
        for i, process in enumerate(self.processes):
            if process.poll() is None:
                character = self.characters[i]
                print(f"🔄 正在停止 {character['display_name']} 服务...")
                
                try:
                    process.terminate()
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    print(f"⚡ 强制终止 {character['display_name']} 服务...")
                    process.kill()
                except Exception as e:
                    print(f"❌ 停止 {character['display_name']} 服务时出错: {e}")
        
        print("✅ 所有服务已停止")

def main():
    """主函数"""
    # 检查是否在正确的目录
    if not os.path.exists("api.py"):
        print("❌ 错误：找不到 api.py 文件")
        print("请确保在 GPT-SoVITS 项目根目录下运行此脚本")
        sys.exit(1)
    
    # 创建管理器并启动服务
    manager = GenshinVoiceAPIManager()
    
    # 设置信号处理器
    def signal_handler(signum, frame):
        print("\n🛑 收到停止信号...")
        manager.stop_all_services()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # 启动所有服务
    manager.start_all_services()

if __name__ == "__main__":
    main()