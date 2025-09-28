#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GPT-SoVITS Docker 部署测试脚本
用于验证 Docker 部署是否成功并测试基本功能
"""

import requests
import json
import time
import sys
import os
from typing import Dict, Any, Optional

class DeploymentTester:
    def __init__(self, base_url: str = "http://localhost:9880"):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        self.session.timeout = 30
        
    def test_health_check(self) -> bool:
        """测试健康检查接口"""
        print("🔍 测试健康检查接口...")
        try:
            response = self.session.get(f"{self.base_url}/health")
            if response.status_code == 200:
                print("✅ 健康检查通过")
                return True
            else:
                print(f"❌ 健康检查失败: HTTP {response.status_code}")
                return False
        except requests.exceptions.RequestException as e:
            print(f"❌ 健康检查连接失败: {e}")
            return False
    
    def test_api_info(self) -> bool:
        """测试 API 信息接口"""
        print("🔍 测试 API 信息接口...")
        try:
            response = self.session.get(f"{self.base_url}/")
            if response.status_code == 200:
                print("✅ API 信息接口正常")
                return True
            else:
                print(f"❌ API 信息接口失败: HTTP {response.status_code}")
                return False
        except requests.exceptions.RequestException as e:
            print(f"❌ API 信息接口连接失败: {e}")
            return False
    
    def test_character_list(self) -> bool:
        """测试角色列表接口"""
        print("🔍 测试角色列表接口...")
        try:
            response = self.session.get(f"{self.base_url}/characters")
            if response.status_code == 200:
                data = response.json()
                if isinstance(data, list) and len(data) > 0:
                    print(f"✅ 角色列表获取成功，共 {len(data)} 个角色")
                    for char in data[:3]:  # 显示前3个角色
                        print(f"   - {char}")
                    return True
                else:
                    print("⚠️ 角色列表为空")
                    return False
            else:
                print(f"❌ 角色列表获取失败: HTTP {response.status_code}")
                return False
        except requests.exceptions.RequestException as e:
            print(f"❌ 角色列表接口连接失败: {e}")
            return False
        except json.JSONDecodeError:
            print("❌ 角色列表响应格式错误")
            return False
    
    def test_tts_generation(self, character: str = "hutao") -> bool:
        """测试 TTS 生成功能"""
        print(f"🔍 测试 TTS 生成功能 (角色: {character})...")
        
        test_data = {
            "text": "你好，这是一个测试文本。",
            "character": character,
            "language": "zh",
            "speed": 1.0
        }
        
        try:
            print("   发送 TTS 请求...")
            response = self.session.post(
                f"{self.base_url}/tts",
                json=test_data,
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code == 200:
                # 检查响应是否为音频数据
                content_type = response.headers.get('content-type', '')
                if 'audio' in content_type or len(response.content) > 1000:
                    print(f"✅ TTS 生成成功，音频大小: {len(response.content)} 字节")
                    
                    # 保存测试音频文件
                    test_file = "test_output.wav"
                    with open(test_file, 'wb') as f:
                        f.write(response.content)
                    print(f"   测试音频已保存为: {test_file}")
                    return True
                else:
                    print(f"❌ TTS 响应格式错误: {content_type}")
                    print(f"   响应内容: {response.text[:200]}...")
                    return False
            else:
                print(f"❌ TTS 生成失败: HTTP {response.status_code}")
                try:
                    error_data = response.json()
                    print(f"   错误信息: {error_data}")
                except:
                    print(f"   错误信息: {response.text[:200]}...")
                return False
                
        except requests.exceptions.RequestException as e:
            print(f"❌ TTS 请求失败: {e}")
            return False
    
    def test_performance(self) -> Dict[str, Any]:
        """测试性能指标"""
        print("🔍 测试性能指标...")
        
        test_texts = [
            "短文本测试",
            "这是一个中等长度的文本测试，用于评估系统的处理能力。",
            "这是一个较长的文本测试，包含更多的字符和复杂的语句结构，用于评估系统在处理长文本时的性能表现和稳定性。"
        ]
        
        results = []
        
        for i, text in enumerate(test_texts, 1):
            print(f"   测试 {i}/3: {len(text)} 字符")
            
            start_time = time.time()
            try:
                response = self.session.post(
                    f"{self.base_url}/tts",
                    json={
                        "text": text,
                        "character": "hutao",
                        "language": "zh"
                    },
                    timeout=60
                )
                
                end_time = time.time()
                duration = end_time - start_time
                
                if response.status_code == 200:
                    audio_size = len(response.content)
                    results.append({
                        "text_length": len(text),
                        "duration": duration,
                        "audio_size": audio_size,
                        "success": True
                    })
                    print(f"   ✅ 耗时: {duration:.2f}s, 音频: {audio_size} 字节")
                else:
                    results.append({
                        "text_length": len(text),
                        "duration": duration,
                        "success": False,
                        "error": f"HTTP {response.status_code}"
                    })
                    print(f"   ❌ 失败: HTTP {response.status_code}")
                    
            except requests.exceptions.Timeout:
                results.append({
                    "text_length": len(text),
                    "success": False,
                    "error": "Timeout"
                })
                print(f"   ❌ 超时")
            except Exception as e:
                results.append({
                    "text_length": len(text),
                    "success": False,
                    "error": str(e)
                })
                print(f"   ❌ 错误: {e}")
        
        # 计算平均性能
        successful_results = [r for r in results if r.get('success')]
        if successful_results:
            avg_duration = sum(r['duration'] for r in successful_results) / len(successful_results)
            print(f"✅ 平均响应时间: {avg_duration:.2f}s")
        else:
            print("❌ 所有性能测试均失败")
        
        return {
            "results": results,
            "success_rate": len(successful_results) / len(results) * 100,
            "avg_duration": avg_duration if successful_results else None
        }
    
    def wait_for_service(self, max_wait: int = 300) -> bool:
        """等待服务启动"""
        print(f"⏳ 等待服务启动 (最多等待 {max_wait} 秒)...")
        
        start_time = time.time()
        while time.time() - start_time < max_wait:
            try:
                response = self.session.get(f"{self.base_url}/health", timeout=5)
                if response.status_code == 200:
                    elapsed = time.time() - start_time
                    print(f"✅ 服务已启动 (耗时 {elapsed:.1f}s)")
                    return True
            except:
                pass
            
            print(".", end="", flush=True)
            time.sleep(5)
        
        print(f"\n❌ 服务启动超时 ({max_wait}s)")
        return False
    
    def run_full_test(self) -> bool:
        """运行完整测试套件"""
        print("=" * 50)
        print("🚀 GPT-SoVITS Docker 部署测试")
        print("=" * 50)
        
        # 等待服务启动
        if not self.wait_for_service():
            return False
        
        tests = [
            ("健康检查", self.test_health_check),
            ("API 信息", self.test_api_info),
            ("角色列表", self.test_character_list),
            ("TTS 生成", self.test_tts_generation),
        ]
        
        passed = 0
        total = len(tests)
        
        print("\n📋 开始功能测试...")
        for test_name, test_func in tests:
            print(f"\n--- {test_name} ---")
            try:
                if test_func():
                    passed += 1
                else:
                    print(f"❌ {test_name} 测试失败")
            except Exception as e:
                print(f"❌ {test_name} 测试异常: {e}")
        
        # 性能测试
        print(f"\n--- 性能测试 ---")
        try:
            perf_results = self.test_performance()
            if perf_results['success_rate'] > 0:
                passed += 0.5  # 性能测试算半分
        except Exception as e:
            print(f"❌ 性能测试异常: {e}")
        
        # 测试结果
        print("\n" + "=" * 50)
        print("📊 测试结果汇总")
        print("=" * 50)
        
        success_rate = (passed / total) * 100
        if success_rate >= 80:
            print(f"✅ 测试通过: {passed}/{total} ({success_rate:.1f}%)")
            print("🎉 部署成功！服务运行正常")
            return True
        else:
            print(f"❌ 测试失败: {passed}/{total} ({success_rate:.1f}%)")
            print("⚠️ 部署可能存在问题，请检查日志")
            return False

def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description="GPT-SoVITS Docker 部署测试")
    parser.add_argument("--url", default="http://localhost:9880", 
                       help="服务 URL (默认: http://localhost:9880)")
    parser.add_argument("--wait", type=int, default=300,
                       help="等待服务启动的最大时间 (秒)")
    
    args = parser.parse_args()
    
    tester = DeploymentTester(args.url)
    
    try:
        success = tester.run_full_test()
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\n\n⚠️ 测试被用户中断")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ 测试过程中发生未知错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()