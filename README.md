# 如何运行程序的说明

本项目包含三个主要子系统：
- `d:\HuanyuOrigin\GPT-SoVITS-TTS`：原神角色语音合成与流式输出（Python）
- `d:\HuanyuOrigin\Huanyu`：业务后端（Java / Spring Boot）
- `d:\HuanyuOrigin\Fronted\my-genshin-chat`：前端（Vite + Vue3）

建议的启动顺序：先启动语音服务(GPT-SoVITS-TTS) → 启动后端(Java) → 启动前端(Vue)。

---

## 环境准备

- 操作系统：Windows 10/11（其他系统需按需调整命令）
- Python：建议 3.9/3.10（64 位）
- Node.js：建议 18+，包含 npm
- Java：JDK 21，Maven 3.6+（建议 3.8+）
- CUDA 加速（可选但推荐）：安装与显卡匹配的 CUDA Toolkit 与 cuDNN，并安装对应版本的 PyTorch（CUDA 版）
  - 例如（按你的 CUDA 版本选择其一）：
    - CUDA 11.8：`pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118`
    - CUDA 12.1：`pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121`
  - 若无独显或不使用 CUDA，可安装 CPU 版：`pip install torch torchvision torchaudio`

---

## 一、启动 GPT-SoVITS-TTS 语音服务（Python）

1) 安装依赖

```powershell
cd d:\HuanyuOrigin\GPT-SoVITS-TTS
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
# 根据你的 CUDA 环境安装匹配版本的 torch（见上方环境准备）
```

2) 启动单个角色服务（按需选择）//其中-d ""为指定设备，cuda为GPU，cpu为CPU

- 启动宵宫服务（端口 5000）：

```powershell
python api.py -s "SoVITS_weights_v2/Yoimiya_e8_s96.pth" -g "GPT_weights_v2/Yoimiya-e15.ckpt" -dr "yoimiya/reference_audios/中文/emotions/【默认】哇，你做点心的手艺很不一般啊！去祭典上摆摊的话，肯定会成为最热门的那一个吧！.wav" -dt "哇，你做点心的手艺很不一般啊！去祭典上摆摊的话，肯定会成为最热门的那一个吧！" -dl "zh" -d "cuda" -a "127.0.0.1" -p 5000
```

- 启动温迪服务（端口 5001）：

```powershell
python api.py -s "SoVITS_weights_v2/Venti_e8_s96.pth" -g "GPT_weights_v2/Venti-e15.ckpt" -dr "venti/reference_audios/中文/emotions/【默认】至少她没有否认——大教堂里收藏着天空之琴。.wav" -dt "至少她没有否认——大教堂里收藏着天空之琴。" -dl "zh" -d "cuda" -a "127.0.0.1" -p 5001
```

- 启动胡桃服务（端口 5002）：

```powershell
python api.py -s "SoVITS_weights_v2/Hutao_e8_s120.pth" -g "GPT_weights_v2/HuTao-e15.ckpt" -dr "hutao/reference_audios/中文/emotions/【默认】嘿嘿，毕竟找活人不是我擅长的事嘛，如果让我找的是「边界」另一边的人….wav" -dt "嘿嘿，毕竟找活人不是我擅长的事嘛，如果让我找的是「边界」另一边的人…." -dl "zh" -d "cuda" -a "127.0.0.1" -p 5002
```

3) 一键启动全部角色（可选）

```powershell
python d:\HuanyuOrigin\GPT-SoVITS-TTS\start_all_characters.py
```

- 说明：该脚本会依次启动所有角色服务并在一个终端聚合日志输出，无法分端口查看到更细致的独立日志。

---

## 二、启动后端（Java / Spring Boot）

1) 安装依赖并运行

```powershell
cd d:\HuanyuOrigin\Huanyu
mvn clean package -DskipTests
mvn spring-boot:run
```

- 如需以可执行 jar 运行：

```powershell
# 构建完成后，目标 jar 名称以实际生成为准
java -jar .\target\Huanyu-*.jar
```

2) 必要配置

- 若涉及语音识别（ASR）与热词、自定义词表，需在 `src/main/resources` 的配置文件（如 `application.yml` / `application.properties`）或环境变量中提供相应的云服务凭证与参数（例如阿里云 NLS 的 `AccessKeyId`、`AccessKeySecret`、`AppKey`、`Token` 等）。
- 如果前端通过接口访问后端，请确认后端实际运行端口（配置文件中为 `8000`），并在前端配置中设置正确的后端地址。

---

### 后端配置详解（application.yml）

后端核心配置位于 `d:\HuanyuOrigin\Huanyu\src\main\resources\application.yml`，关键项说明如下（已存在的内容无需重复设置，按需调整即可）：

- 服务器端口
  - `server.port: 8000`（前端调用时请指向 `http://127.0.0.1:8000`）

- 数据库（MySQL）
  - `spring.datasource.url` 示例：`jdbc:mysql://IP:3306/huanyu?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true`
  - 本地开发建议改为：`jdbc:mysql://127.0.0.1:3306/huanyu?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true`
  - `spring.datasource.username: root`
  - `spring.datasource.password: ${MYSQL_PASSWORD}`（从环境变量注入，见下方环境变量设置）
  - 连接池：`com.alibaba.druid.pool.DruidDataSource`，已配置基础池参数

- Redis 缓存
  - `spring.data.redis.host: 120.26.29.90`（本地开发建议改为 `127.0.0.1`）
  - `spring.data.redis.port: 6379`
  - `spring.data.redis.password: ${REDIS_PASSWORD}`（从环境变量注入）
  - 已配置连接超时与 lettuce 连接池参数

- 邮件（QQ SMTP）
  - `spring.mail.host: smtp.qq.com`
  - `spring.mail.port: 587`
  - `spring.mail.username: ${MAIL_USERNAME}`
  - `spring.mail.password: ${MAIL_PASSWORD}`（为 QQ 邮箱的 SMTP 授权码）
  - 已启用 `starttls`

- 接口文档（springdoc + knife4j）
  - Swagger UI：`/swagger-ui.html`
  - OpenAPI JSON：`/v3/api-docs`
  - 包扫描：`com.tanxian.controller`

- 日志级别
  - 已对 `dev.langchain4j`、`com.tanxian.mapper`、`org.springframework.data.redis` 开启 debug 便于调试

- MyBatis-Plus
  - 开启驼峰命名、二级缓存、SQL 打印
  - 逻辑删除字段：`deleted`（1 为删除，0 为未删除）
  - `mapper-locations: classpath*:mapper/**/*.xml`
  - `type-aliases-package: com.tanxian.entity`

- 七牛云存储
  - `qiniu.access-key: ${QINIU_ACCESS_KEY}`
  - `qiniu.secret-key: ${QINIU_SECRET_KEY}`
  - `qiniu.bucket-name: ${QINIU_BUCKET_NAME:huanyu-origin}`（默认 `huanyu-origin`）
  - `qiniu.domain: ${QINIU_DOMAIN}`（你的 CDN 绑定域名）
  - `qiniu.path-prefix: huanyu/avatar/`
  - `qiniu.max-file-size: 104857600`（100MB）

- 阿里云智能语音识别（NLS）
  - `aliyun.nls.appKey: ${ALIYUN_NLS_APP_KEY}`
  - `aliyun.nls.accessKeyId: ${ALIYUN_NLS_ACCESS_KEY_ID}`
  - `aliyun.nls.accessKeySecret: ${ALIYUN_NLS_ACCESS_KEY_SECRET}`
  - 网关地址：`aliyun.nls.gatewayUrl: ${NLS_GATEWAY_URL:wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1}`

---

### 数据库与缓存安装与初始化（Windows）

1) 安装 MySQL 并导入结构

- 安装：下载并安装 MySQL Community Server（Windows），确保服务启动。
- 创建数据库：

```powershell
# 使用命令行客户端或图形工具（如 MySQL Workbench）连接后执行：
CREATE DATABASE huanyu CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

- 导入初始化 SQL（表结构与必要数据）：

```powershell
mysql -u root -p huanyu < d:\HuanyuOrigin\Huanyu\sql\huanyu.sql
```

- 在 `application.yml` 中将数据库地址指向本地：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/huanyu?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true
    username: root
    password: ${MYSQL_PASSWORD}
```

2) 安装 Redis（任选其一）

- 方案 A：WSL + Ubuntu

```powershell
# 安装 WSL（首次安装可能需重启）
wsl --install
# 打开 Ubuntu 终端：安装并启动 Redis
sudo apt update
sudo apt install -y redis-server
sudo service redis-server start
```

- 方案 B：Docker（推荐）

```powershell
# 安装 Docker Desktop 后运行：
docker run -d --name redis -p 6379:6379 redis:7
```

- 方案 C：Memurai（Redis for Windows 的商业/社区实现）
  - 下载并安装 Memurai 后，默认监听 `localhost:6379`

- 在 `application.yml` 中将 Redis 指向本地：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${REDIS_PASSWORD}
```

3) 环境变量设置（PowerShell）

- 会话内临时设置（仅当前终端有效）：

```powershell
$env:MYSQL_PASSWORD = 'your_mysql_password'
$env:REDIS_PASSWORD = 'your_redis_password'
$env:MAIL_USERNAME = 'your@qq.com'
$env:MAIL_PASSWORD = 'your_smtp_app_password'
$env:OPENAI_API_KEY = 'your_dashscope_key'
$env:QINIU_ACCESS_KEY = 'your_qiniu_ak'
$env:QINIU_SECRET_KEY = 'your_qiniu_sk'
$env:QINIU_BUCKET_NAME = 'huanyu-origin'
$env:QINIU_DOMAIN = 'https://your-cdn-domain'
$env:ALIYUN_NLS_APP_KEY = 'your_app_key'
$env:ALIYUN_NLS_ACCESS_KEY_ID = 'your_akid'
$env:ALIYUN_NLS_ACCESS_KEY_SECRET = 'your_aksecret'
$env:NLS_GATEWAY_URL = 'wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1'
```

- 永久写入（新开终端生效）：

```powershell
setx MYSQL_PASSWORD "your_mysql_password"
setx REDIS_PASSWORD "your_redis_password"
setx MAIL_USERNAME "your@qq.com"
setx MAIL_PASSWORD "your_smtp_app_password"
setx OPENAI_API_KEY "your_dashscope_key"
setx QINIU_ACCESS_KEY "your_qiniu_ak"
setx QINIU_SECRET_KEY "your_qiniu_sk"
setx QINIU_BUCKET_NAME "huanyu-origin"
setx QINIU_DOMAIN "https://your-cdn-domain"
setx ALIYUN_NLS_APP_KEY "your_app_key"
setx ALIYUN_NLS_ACCESS_KEY_ID "your_akid"
setx ALIYUN_NLS_ACCESS_KEY_SECRET "your_aksecret"
setx NLS_GATEWAY_URL "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
```

设置完成后，重新启动后端服务即可读取到这些环境变量。

---

## 三、启动前端（Vite + Vue3）

1) 安装依赖并运行开发服务器

```powershell
cd d:\HuanyuOrigin\Fronted\my-genshin-chat
npm install
npm run dev
```

- 控制台会显示本地访问地址，例如：`http://localhost:5173/`（如端口占用，会自动切换到其他端口）。

2) 前端后端地址配置

- 如需修改后端 API 地址，请在前端项目的 `.env.development` 或相关配置（例如 `src/utils/axios`）中设置正确的后端基地址（如 `http://127.0.0.1:8000`）。
- 登录鉴权令牌通常保存在浏览器 `localStorage`，请确保登录流程正常或手动设置令牌以调用需要鉴权的接口。

---

## 四、联调与验证

- 先在浏览器打开前端页面，选择角色并发送一条消息，随后即可点击“电话”按钮发起实时通话（若未发送消息，点击电话会弹出提示）。
- 语音识别（STT）功能依赖麦克风权限与后端 ASR 服务，请确保浏览器允许麦克风权限、前端采集与上传逻辑正常，以及后端已配置云 ASR 参数。
- 若遇到前端实时语音或上传识别为空的情况，请确认：
  - 已在开始录音前恢复 `AudioContext`（前端已优化相关逻辑）。
  - 上传使用 `Blob` 格式，后端收到的字节长度非零（可在后端日志中打印 `audioBytes.length` 以确认）。

---

## 常见问题排查

- 端口占用：
  - 前端 dev server 会自动切换端口（例如从 5173 切到 5174）。
  - 后端与语音服务端口固定，建议在启动前确认未被占用。
- CUDA / Torch 版本不匹配：
  - 若启动时报错与 CUDA 相关，请重新安装与本机 GPU/驱动匹配的 torch 版本。
- WebSocket 连接失败：
  - 检查后端地址、端口、网络与防火墙设置，确认前端配置指向正确的后端。
- 前端访问私有音频链接失败：
  - 确保已登录并持有有效令牌，按需刷新私有链接。

---

## 目录速览与关键路径

- 语音服务根目录：`d:\HuanyuOrigin\GPT-SoVITS-TTS`
- 后端根目录：`d:\HuanyuOrigin\Huanyu`
- 前端根目录：`d:\HuanyuOrigin\Fronted\my-genshin-chat`

如需将三者部署到同一台机器，请确保：
- 语音服务按 5000/5001/5002 端口运行；
- 后端正确访问语音服务端点（如存在服务间调用）；
- 前端正确指向后端地址（与登录鉴权一致）。

---

如需进一步自动化或增加进程守护、日志分流等功能，可在当前基础上新增启动脚本或使用进程管理器（如 `pm2`、`supervisor`、Windows 任务计划等）。