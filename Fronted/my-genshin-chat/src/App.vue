<template>
  <div class="background-container">
    <div class="background-image" :style="backgroundStyle"></div>
    <div class="background-overlay"></div>
    <div class="particles">
      <div v-for="i in 20" :key="i" class="particle"
           :style="{
                         left: Math.random() * 100 + '%',
                         animationDelay: Math.random() * 15 + 's',
                         animationDuration: (15 + Math.random() * 10) + 's'
                     }"></div>
    </div>
  </div>

  <div class="main-container">
    <h1 class="title" v-show="!chatMode">原神 AI 角色扮演</h1>

    <transition name="fade-slide">
      <div v-if="!chatMode" class="character-selector-container">
        <div v-for="char in characters"
             :key="char.id"
             class="character-card"
             @click="selectCharacter(char)">
          <div class="character-avatar">{{ char.emoji }}</div>
          <div class="character-name">{{ char.name }}</div>
          <div class="character-desc">{{ char.description }}</div>
        </div>
      </div>
    </transition>

    <div v-if="chatMode" class="chat-interface" :class="{ show: chatMode }">
      <button class="back-button" @click="backToSelection">
        ← 返回选择
      </button>

      <div class="chat-header">
        <div class="chat-character-info">
          <div class="chat-character-avatar">
            {{ currentCharacter?.emoji }}
          </div>
          <div class="chat-character-name">
            {{ currentCharacter?.name }}
          </div>
        </div>
        <div class="connection-badge" :class="connectionStatus">
          {{ connectionStatusText }}
        </div>
      </div>

      <div class="chat-messages">
        <div v-if="messages.length === 0" class="empty-chat">
          <div class="empty-chat-icon">💬</div>
          <div>按住下方按钮开始对话</div>
        </div>
        <div v-for="(msg, index) in messages"
             :key="index"
             class="message"
             :class="msg.role">
          <div class="message-sender">
            {{ msg.role === 'user' ? '你' : currentCharacter?.name }}
          </div>
          <div>{{ msg.content }}</div>
        </div>
      </div>

      <div class="chat-controls">
        <button class="record-button"
                :class="{ recording: isRecording }"
                :disabled="connectionStatus !== 'connected'"
                @mousedown="startRecording"
                @mouseup="stopRecording"
                @mouseleave="stopRecording"
                @touchstart="startRecording"
                @touchend="stopRecording">
          {{ isRecording ? '🔴' : '🎤' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue';

// 角色数据
const characters = ref([
  {
    id: 'Hutao',
    name: '胡桃',
    emoji: '🦋',
    description: '往生堂第77代堂主',
    background: 'url(https://via.placeholder.com/1920x1080/8B4513/FFFFFF?text=Hutao)' // 替换为实际图片
  },
  {
    id: 'Venti',
    name: '温迪',
    emoji: '🍃',
    description: '自由的吟游诗人',
    background: 'url(https://via.placeholder.com/1920x1080/00CED1/FFFFFF?text=Venti)' // 替换为实际图片
  },
  {
    id: 'Xiaogong',
    name: '宵宫',
    emoji: '🎆',
    description: '长野原烟花店店主',
    background: 'url(https://via.placeholder.com/1920x1080/FF6347/FFFFFF?text=Xiaogong)' // 替换为实际图片
  }
]);

// 状态管理
const chatMode = ref(false);
const currentCharacter = ref(null);
const isRecording = ref(false);
const messages = ref([]);
const connectionStatus = ref('disconnected');

// WebSocket和音频相关
let socket = null;
let audioContext = null;
let mediaStream = null;
let audioWorkletNode = null;

// 计算属性
const connectionStatusText = computed(() => {
  const statusMap = {
    'connected': '已连接',
    'connecting': '连接中...',
    'disconnected': '未连接',
    'error': '连接错误'
  };
  return statusMap[connectionStatus.value];
});

const backgroundStyle = computed(() => {
  if (currentCharacter.value) {
    return {
      backgroundImage: currentCharacter.value.background
    };
  }
  return {
    backgroundImage: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  };
});

// 方法
const selectCharacter = (char) => {
  currentCharacter.value = char;
  chatMode.value = true;
  messages.value = [];
  connectWebSocket();
};

const backToSelection = () => {
  chatMode.value = false;
  currentCharacter.value = null;
  messages.value = [];
  if (socket) {
    socket.close(1000, '返回角色选择');
  }
};

const connectWebSocket = () => {
  if (socket && socket.readyState === WebSocket.OPEN) return;

  connectionStatus.value = 'connecting';

  try {
    socket = new WebSocket('ws://localhost:8000/ws-audio');
    socket.binaryType = "arraybuffer";

    socket.onopen = () => {
      connectionStatus.value = 'connected';
      console.log('WebSocket连接成功');
    };

    socket.onmessage = (event) => {
      // 更新最后一条用户消息
      if (messages.value.length > 0 &&
          messages.value[messages.value.length - 1].role === 'user' &&
          messages.value[messages.value.length - 1].content.includes('等待识别')) {
        messages.value[messages.value.length - 1].content = '（语音输入已识别）';
      }

      // 添加AI回复
      messages.value.push({
        role: 'ai',
        content: event.data
      });
    };

    socket.onerror = (error) => {
      console.error('WebSocket错误:', error);
      connectionStatus.value = 'error';
    };

    socket.onclose = () => {
      connectionStatus.value = 'disconnected';
    };
  } catch (error) {
    console.error('WebSocket连接失败:', error);
    connectionStatus.value = 'error';
  }
};

const startRecording = async () => {
  if (!currentCharacter.value || connectionStatus.value !== 'connected' || isRecording.value) {
    return;
  }

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioContext = new AudioContext();

    // 注意：这里需要后端提供audio-processor.js文件
    await audioContext.audioWorklet.addModule('/audio-processor.js');

    const source = audioContext.createMediaStreamSource(mediaStream);
    audioWorkletNode = new AudioWorkletNode(audioContext, 'audio-processor');

    source.connect(audioWorkletNode);
    audioWorkletNode.connect(audioContext.destination);

    // 发送角色信息
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({
        type: 'character_select',
        characterId: currentCharacter.value.id
      }));
    }

    audioWorkletNode.port.onmessage = (event) => {
      if (event.data.type === 'audioData' && socket && socket.readyState === WebSocket.OPEN) {
        socket.send(event.data.data);
      }
    };

    isRecording.value = true;

    messages.value.push({
      role: 'user',
      content: '（语音输入，等待识别...）'
    });

  } catch (error) {
    console.error('录音失败:', error);
    alert('无法访问麦克风，请检查权限设置');
  }
};

const stopRecording = () => {
  if (!isRecording.value) return;

  if (audioWorkletNode) {
    audioWorkletNode.disconnect();
    audioWorkletNode = null;
  }

  if (audioContext) {
    audioContext.close();
    audioContext = null;
  }

  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop());
    mediaStream = null;
  }

  isRecording.value = false;
};

onUnmounted(() => {
  if (socket) socket.close();
  stopRecording();
});
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
  background: #000;
}

/* 背景相关样式 */
.background-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
}

.background-image {
  position: absolute;
  width: 110%;
  height: 110%;
  top: -5%;
  left: -5%;
  background-size: cover;
  background-position: center;
  filter: blur(8px);
  opacity: 0.6;
  transition: all 1s ease;
}

/* 覆盖渐变层 */
.background-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg,
  rgba(30, 0, 50, 0.8) 0%,
  rgba(10, 20, 80, 0.6) 50%,
  rgba(50, 0, 100, 0.8) 100%);
  animation: gradientShift 10s ease infinite;
}

@keyframes gradientShift {
  0%, 100% { opacity: 0.8; }
  50% { opacity: 0.6; }
}

/* 浮动粒子效果 */
.particles {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.particle {
  position: absolute;
  width: 4px;
  height: 4px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  animation: float 15s infinite linear;
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.5);
}

@keyframes float {
  0% {
    transform: translateY(100vh) translateX(0);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateY(-100vh) translateX(100px);
    opacity: 0;
  }
}

/* 主容器 */
.main-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 标题动画 */
.title {
  position: absolute;
  top: 50px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 3em;
  color: #fff;
  text-shadow: 0 0 30px rgba(255, 255, 255, 0.5),
  0 0 60px rgba(100, 150, 255, 0.5);
  animation: titleGlow 3s ease infinite alternate;
  opacity: 0;
  animation: titleAppear 1s ease forwards;
}

@keyframes titleAppear {
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@keyframes titleGlow {
  from {
    text-shadow: 0 0 30px rgba(255, 255, 255, 0.5),
    0 0 60px rgba(100, 150, 255, 0.5);
  }
  to {
    text-shadow: 0 0 40px rgba(255, 255, 255, 0.8),
    0 0 80px rgba(150, 200, 255, 0.8);
  }
}

/* 角色选择器容器 */
.character-selector-container {
  display: flex;
  gap: 50px;
  transition: all 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 角色卡片 */
.character-card {
  width: 200px;
  height: 280px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.05));
  backdrop-filter: blur(10px);
  border-radius: 20px;
  border: 2px solid rgba(255, 255, 255, 0.2);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  opacity: 0;
  transform: translateY(50px);
  animation: cardAppear 0.8s ease forwards;
}

.character-card:nth-child(1) { animation-delay: 0.2s; }
.character-card:nth-child(2) { animation-delay: 0.4s; }
.character-card:nth-child(3) { animation-delay: 0.6s; }

@keyframes cardAppear {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.character-card:hover {
  transform: translateY(-10px) scale(1.05);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3),
  0 0 60px rgba(100, 150, 255, 0.4);
}

.character-card::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(45deg,
  transparent,
  rgba(255, 255, 255, 0.1),
  transparent);
  transform: rotate(45deg);
  transition: all 0.5s;
  opacity: 0;
}

.character-card:hover::before {
  animation: shimmer 0.5s ease;
}

@keyframes shimmer {
  0% {
    transform: rotate(45deg) translateY(-100%);
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
  100% {
    transform: rotate(45deg) translateY(100%);
    opacity: 0;
  }
}

.character-avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 3em;
  color: white;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

.character-name {
  font-size: 1.5em;
  color: #fff;
  margin-bottom: 10px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
}

.character-desc {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.9em;
  text-align: center;
  padding: 0 20px;
}

/* 聊天界面样式 */
.chat-interface {
  position: absolute;
  width: 90%;
  max-width: 900px;
  height: 80vh;
  background: linear-gradient(135deg,
  rgba(20, 20, 40, 0.95),
  rgba(30, 30, 60, 0.9));
  backdrop-filter: blur(20px);
  border-radius: 30px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  opacity: 0;
  transform: scale(0.8) translateY(50px);
  transition: all 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-interface.show {
  opacity: 1;
  transform: scale(1) translateY(0);
}

/* 聊天头部 */
.chat-header {
  padding: 25px 30px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 30px 30px 0 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.chat-character-info {
  display: flex;
  align-items: center;
  gap: 15px;
}

.chat-character-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.5em;
}

.chat-character-name {
  color: #fff;
  font-size: 1.3em;
  font-weight: bold;
}

.connection-badge {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 0.9em;
  font-weight: bold;
  animation: pulse 2s infinite;
}

.connection-badge.connected {
  background: rgba(40, 167, 69, 0.2);
  color: #40ff69;
  border: 1px solid #40ff69;
}

.connection-badge.connecting {
  background: rgba(255, 193, 7, 0.2);
  color: #ffc107;
  border: 1px solid #ffc107;
}

.connection-badge.disconnected {
  background: rgba(220, 53, 69, 0.2);
  color: #ff4069;
  border: 1px solid #ff4069;
}

/* 聊天消息区域 */
.chat-messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.chat-messages::-webkit-scrollbar {
  width: 8px;
}

.chat-messages::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 10px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
}

.message {
  padding: 15px 20px;
  border-radius: 20px;
  max-width: 70%;
  animation: messageAppear 0.3s ease;
  position: relative;
}

@keyframes messageAppear {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  align-self: flex-end;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.message.ai {
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.message-sender {
  font-size: 0.85em;
  opacity: 0.8;
  margin-bottom: 5px;
  font-weight: bold;
}

/* 输入控制区 */
.chat-controls {
  padding: 20px 30px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 0 0 30px 30px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
}

.record-button {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: white;
  font-size: 2em;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
}

.record-button:hover:not(:disabled) {
  transform: scale(1.1);
  box-shadow: 0 15px 40px rgba(102, 126, 234, 0.6);
}

.record-button.recording {
  background: linear-gradient(135deg, #ff4069 0%, #ff1744 100%);
  animation: recordPulse 1.5s infinite;
}

@keyframes recordPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 64, 105, 0.7);
  }
  70% {
    box-shadow: 0 0 0 20px rgba(255, 64, 105, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 64, 105, 0);
  }
}

.record-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.back-button {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: white;
  border-radius: 25px;
  cursor: pointer;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
}

.back-button:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: translateX(-5px);
}

/* 空状态 */
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(255, 255, 255, 0.5);
  font-size: 1.2em;
}

.empty-chat-icon {
  font-size: 4em;
  margin-bottom: 20px;
  opacity: 0.3;
}

/* 过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.5s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(30px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-30px);
}

/* 响应式 */
@media (max-width: 768px) {
  .title {
    font-size: 2em;
  }

  .character-selector-container {
    flex-direction: column;
    gap: 20px;
  }

  .character-card {
    width: 160px;
    height: 220px;
  }

  .chat-interface {
    width: 95%;
    height: 90vh;
  }
}
</style>