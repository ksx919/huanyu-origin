# GPT-SoVITS 源代码版本

这是 GPT-SoVITS 项目的源代码版本，已排除所有依赖项文件。本文档详细说明了如何正确配置运行环境。

## 项目概述

GPT-SoVITS 是一个基于 PyTorch 的语音合成项目，支持多语言文本到语音转换。本版本仅包含源代码和配置文件，需要手动安装所有依赖项。

## 系统要求

### 硬件要求
- **内存**: 最少 8GB RAM，推荐 16GB 或更多
- **GPU**: 支持 CUDA 的 NVIDIA GPU（推荐 8GB+ 显存）
- **存储**: 至少 10GB 可用空间

### 软件要求
- **操作系统**: Windows 10/11, Linux (Ubuntu 18.04+), macOS 10.15+
- **Python**: 3.9 - 3.11 (推荐 3.10)
- **CUDA**: 11.8 或 12.1+ (如果使用 GPU)

## 依赖项安装步骤

### 1. Python 环境准备

```bash
# 创建虚拟环境
python -m venv gpt-sovits-env

# 激活虚拟环境
# Windows:
gpt-sovits-env\Scripts\activate
# Linux/macOS:
source gpt-sovits-env/bin/activate

# 升级 pip
python -m pip install --upgrade pip
```

### 2. PyTorch 安装

**重要**: 必须先安装 PyTorch，再安装其他依赖项。

#### CUDA 版本 (推荐)
```bash
# CUDA 11.8
pip install torch==2.1.0 torchaudio==2.1.0 --index-url https://download.pytorch.org/whl/cu118

# CUDA 12.1
pip install torch==2.1.0 torchaudio==2.1.0 --index-url https://download.pytorch.org/whl/cu121
```

#### CPU 版本
```bash
pip install torch==2.1.0 torchaudio==2.1.0 --index-url https://download.pytorch.org/whl/cpu
```

### 3. 核心依赖项安装

```bash
# 安装项目依赖
pip install -r requirements.txt
```

### 4. 特殊依赖项处理

#### OpenCC (中文繁简转换)
```bash
# 如果自动安装失败，手动安装
pip install --no-binary=opencc opencc
```

#### 平台特定依赖
```bash
# Windows 用户跳过此步骤
# Linux/macOS 用户安装 MeCab
pip install python_mecab_ko
```

## PyTorch、CUDA 和 TorchCodec 版本兼容性

### PyTorch 版本要求
- **最低版本**: PyTorch 2.1.0
- **推荐版本**: PyTorch 2.1.0 - 2.2.0
- **TorchAudio**: 与 PyTorch 版本保持一致

### CUDA 兼容性矩阵

| PyTorch 版本 | CUDA 11.8 | CUDA 12.1 | CUDA 12.4 |
|-------------|-----------|-----------|-----------|
| 2.1.0       | ✅         | ✅         | ❌         |
| 2.2.0       | ✅         | ✅         | ✅         |
| 2.3.0+      | ❌         | ✅         | ✅         |

### GPU 计算能力要求
- **最低**: Compute Capability 6.0 (GTX 1060, Tesla P100)
- **推荐**: Compute Capability 7.5+ (RTX 2080, V100)
- **FP8 量化**: Compute Capability 9.0+ (H100)

### NVCC 编译器兼容性
BigVGAN 模块需要 NVCC 编译器：
```bash
# 检查 NVCC 版本
nvcc --version

# 确保 NVCC 版本与 PyTorch CUDA 版本匹配
# CUDA 11.8: NVCC 11.8.x
# CUDA 12.1: NVCC 12.1.x
```

## 环境配置注意事项

### 1. 内存管理
```python
# 在代码中添加内存优化设置
import torch
torch.backends.cudnn.benchmark = True
torch.backends.cudnn.deterministic = False
```

### 2. CUDA 环境变量
```bash
# Windows
set CUDA_VISIBLE_DEVICES=0
set PYTORCH_CUDA_ALLOC_CONF=max_split_size_mb:128

# Linux/macOS
export CUDA_VISIBLE_DEVICES=0
export PYTORCH_CUDA_ALLOC_CONF=max_split_size_mb:128
```

### 3. 模型权重目录
确保以下目录存在：
```
GPT_weights_v2/     # GPT 模型权重
SoVITS_weights_v2/  # SoVITS 模型权重
```

### 4. 音频处理工具
项目包含 FFmpeg 可执行文件：
- `ffmpeg.exe` (Windows)
- `ffprobe.exe` (Windows)

Linux/macOS 用户需要单独安装：
```bash
# Ubuntu/Debian
sudo apt install ffmpeg

# macOS
brew install ffmpeg
```

## 版本依赖说明

### 关键版本限制
```
numpy < 2.0                    # 避免兼容性问题
librosa == 0.10.2             # 音频处理库，固定版本
transformers >= 4.43, <= 4.50 # Hugging Face 模型库
pytorch-lightning >= 2.4      # 深度学习框架
modelscope == 1.10.0          # 模型库，固定版本
torchmetrics <= 1.5           # 评估指标库
pydantic <= 2.10.6            # 数据验证库
```

### 可选依赖项
```
onnxruntime-gpu    # GPU 加速推理 (x86_64/AMD64)
onnxruntime        # CPU 推理 (ARM64)
python_mecab_ko    # 韩语分词 (非 Windows)
```

## 启动项目

### 1. API 服务器
```bash
# 启动 FastAPI 服务器
python api_v2.py

# 或使用 uvicorn
uvicorn api_v2:app --host 0.0.0.0 --port 9880
```

### 2. 批量启动
```bash
# 启动所有角色的服务
python start_all_characters.py
```

## 故障排除

### 常见问题

1. **CUDA 内存不足**
   ```bash
   # 减少批处理大小
   export PYTORCH_CUDA_ALLOC_CONF=max_split_size_mb:64
   ```

2. **BigVGAN CUDA 编译失败**
   ```bash
   # 检查 NVCC 版本匹配
   nvcc --version
   python -c "import torch; print(torch.version.cuda)"
   ```

3. **OpenCC 安装失败**
   ```bash
   # 使用预编译版本
   pip install opencc-python-reimplemented
   ```

4. **模型加载错误**
   - 确保模型权重文件存在
   - 检查 `weight.json` 配置文件
   - 验证 CUDA 可用性

### 性能优化

1. **启用混合精度训练**
   ```python
   torch.backends.cuda.matmul.allow_tf32 = True
   torch.backends.cudnn.allow_tf32 = True
   ```

2. **优化内存使用**
   ```python
   torch.cuda.empty_cache()  # 清理 GPU 缓存
   ```

## 许可证

请参考原项目的许可证文件。

## 支持

如遇到问题，请检查：
1. Python 和依赖项版本
2. CUDA 驱动和工具包版本
3. GPU 内存和计算能力
4. 模型文件完整性

---

**注意**: 本文档基于项目当前状态编写，实际使用时请根据最新版本调整配置。