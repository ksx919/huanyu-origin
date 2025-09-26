<template>
  <div class="background-container">
    <div class="background-image"></div>
    <div class="particles">
      <div class="particle" v-for="n in 30" :key="n"></div>
    </div>
  </div>

  <div class="main-container">

    <div v-if="!selectedCharacter" class="selection-screen">
      <transition appear @before-enter="beforeTitleEnter" @enter="enterTitle">
        <h1>选择你的对话对象</h1>
      </transition>

      <TransitionGroup
          appear
          tag="div"
          class="char-selector"
          @before-enter="beforeCharEnter"
          @enter="enterChar"
      >
        <div
            v-for="(char, index) in characters"
            :key="char.id"
            class="char-card"
            @click="selectCharacter(char.id)"
            :data-index="index"
        >
          <img :src="char.avatar" :alt="char.name" class="char-avatar">
          <span class="char-name">{{ char.name }}</span>
        </div>
      </TransitionGroup>
    </div>

    <div v-else class="chat-screen">
      <Transition name="chatbox-transform" appear>
        <div class="chat-wrapper">

          <div class="chat-header">
            <button @click="deselectCharacter" class="back-button">&lt; 返回</button>
            <h3>正在与 {{ getCharacterName(selectedCharacter) }} 对话</h3>
            <div class="status-light" :class="connectionStatus"></div>
          </div>

          <div class="chat-window">
            <div v-for="(msg, index) in conversation" :key="index" :class="msg.role">
              <strong>{{ msg.role === 'user' ? '你' : getCharacterName(selectedCharacter) }}:</strong>
              {{ msg.content }}
            </div>
            <div v-if="conversation.length === 0" class="empty-chat">
              可以开始对话了！
            </div>
          </div>

          <div class="controls">
            <button
                class="record-btn"
                @mousedown="startRecording"
                @mouseup="stopRecording"
                @mouseleave="stopRecording"
                :disabled="!selectedCharacter || isRecording"
                :class="{ recording: isRecording }"
            >
              {{ getRecordButtonText() }}
            </button>
          </div>

        </div>
      </Transition>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue';
import gsap from 'gsap';

// 类型定义
interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
  timestamp?: number;
}

const characters = ref([
  { id: 'Hutao', name: '胡桃', avatar: '/Hutao.jpg' },
  { id: 'Venti', name: '温迪', avatar: '/Venti.jpg' },
  { id: 'Xiaogong', name: '宵宫', avatar: '/Xiaogong.jpg' }
]);

// 响应式数据
const selectedCharacter = ref<string | null>(null);
const isRecording = ref(false);
const conversation = ref<ChatMessage[]>([]);
const connectionStatus = ref<'disconnected' | 'connecting' | 'connected' | 'error'>('disconnected');

// WebSocket 和录音相关变量
let socket: WebSocket | null = null;
let audioContext: AudioContext | null = null;
let mediaStream: MediaStream | null = null;
let audioWorkletNode: AudioWorkletNode | null = null;

// WebSocket 连接函数 (返回 Promise)
const connectWebSocket = () => {
  return new Promise<void>((resolve, reject) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      resolve();
      return;
    }

    connectionStatus.value = 'connecting';
    const backendUrl = 'ws://localhost:8000/ws-audio';

    try {
      socket = new WebSocket(backendUrl);
      socket.binaryType = "arraybuffer";

      socket.onopen = () => {
        console.log('WebSocket 连接已建立');
        connectionStatus.value = 'connected';

        if (selectedCharacter.value) {
          socket!.send(JSON.stringify({
            type: 'character_select',
            characterId: selectedCharacter.value
          }));
        }
        resolve();
      };

      socket.onmessage = (event) => {
        if (conversation.value.length > 0 && conversation.value[conversation.value.length - 1].role === 'user') {
          if (conversation.value[conversation.value.length - 1].content === '（语音输入，等待识别...）') {
            conversation.value[conversation.value.length - 1].content = '（语音输入已识别）';
          }
        }
        conversation.value.push({
          role: 'ai',
          content: event.data,
          timestamp: Date.now()
        });
      };

      socket.onerror = (error) => {
        console.error('WebSocket 错误:', error);
        connectionStatus.value = 'error';
        reject(error);
      };

      socket.onclose = (event) => {
        console.log('WebSocket 连接已关闭', event.code, event.reason);
        if (connectionStatus.value === 'connecting') {
          reject(new Error(`连接关闭，请检查后端服务是否开启。 Code: ${event.code}`));
        }
        connectionStatus.value = 'disconnected';
        socket = null;
      };
    } catch (error) {
      console.error('创建 WebSocket 失败:', error);
      connectionStatus.value = 'error';
      reject(error);
    }
  });
};

// 角色选择逻辑
const selectCharacter = (charId: string) => {
  selectedCharacter.value = charId;
  conversation.value = [];
};

const deselectCharacter = () => {
  selectedCharacter.value = null;
  conversation.value = [];
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.close(1000, '用户返回选择界面');
  }
};

const startRecording = async () => {
  if (isRecording.value) return;

  try {
    await connectWebSocket();

    isRecording.value = true;

    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 16000 });
    await audioContext.audioWorklet.addModule('/audio-processor.js');
    audioWorkletNode = new AudioWorkletNode(audioContext, 'audio-processor');
    const source = audioContext.createMediaStreamSource(mediaStream);
    source.connect(audioWorkletNode);
    audioWorkletNode.connect(audioContext.destination);

    audioWorkletNode.port.onmessage = (event) => {
      if (event.data.type === 'audioData' && socket && socket.readyState === WebSocket.OPEN) {
        socket.send(event.data.data);
      }
    };

    conversation.value.push({
      role: 'user',
      content: '（语音输入，等待识别...）',
      timestamp: Date.now()
    });
    console.log('录音已开始');

  } catch (error) {
    console.error('录音或连接失败:', error);
    alert('无法访问麦克风或连接服务器，请检查权限和网络');
    stopRecording();
  }
};

const stopRecording = () => {
  if (!isRecording.value && !mediaStream) return;

  isRecording.value = false;

  try {
    if (audioWorkletNode) { audioWorkletNode.disconnect(); audioWorkletNode = null; }
    if (mediaStream) { mediaStream.getTracks().forEach(track => track.stop()); mediaStream = null; }
    if (audioContext && audioContext.state !== 'closed') { audioContext.close(); audioContext = null; }

    console.log('录音资源已清理');


    if (socket && socket.readyState === WebSocket.OPEN) {
      setTimeout(() => {
        if(socket) socket.close(1000, '录音结束');
      }, 100);
    }
  } catch (error) {
    console.error('停止录音时出错:', error);
  }
};


const beforeTitleEnter = (el: Element) => { (el as HTMLElement).style.opacity = '0'; (el as HTMLElement).style.transform = 'translateY(-30px)'; };
const enterTitle = (el: Element, done: () => void) => { gsap.to(el, { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out', onComplete: done }); };
const beforeCharEnter = (el: Element) => { (el as HTMLElement).style.opacity = '0'; (el as HTMLElement).style.transform = 'translateY(30px) scale(0.9)'; };
const enterChar = (el: Element, done: () => void) => {
  const index = parseInt((el as HTMLElement).dataset.index || '0');
  gsap.to(el, { opacity: 1, y: 0, scale: 1, duration: 0.6, delay: index * 0.15 + 0.5, ease: 'power2.out', onComplete: done });
};

// 计算属性和辅助函数
const getCharacterName = (char: string | null) => {
  return characters.value.find(c => c.id === char)?.name || '未知角色';
};

const getRecordButtonText = () => {
  if (connectionStatus.value === 'connecting') return '正在连接...';
  if (connectionStatus.value === 'error') return '连接失败, 点击重试';
  if (isRecording.value) return '正在录音... (松开停止)';
  return '按住说话';
};

// 生命周期管理
onUnmounted(() => {
  stopRecording();
  if (socket) {
    socket.close(1000, '用户离开页面');
  }
});
</script>

<style>
:root {
  --char-hutao-primary: #c96567;
  --char-venti-primary: #68b0ab;
  --char-xiaogong-primary: #e6a759;
  --text-light: #f4f4f4;
  --bg-dark-glass: rgba(0, 0, 0, 0.3);
}

body {
  margin: 0;
  font-family: 'Helvetica Neue', 'Hiragino Sans GB', 'WenQuanYi Micro Hei', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
}

.background-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: -1;
}

.background-image {
  width: 100%;
  height: 100%;
  background-image: url('/background1.jpg');
  background-size: cover;
  background-position: center;
  filter: blur(3px) brightness(0.7);
  transform: scale(1.1);
}

.particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.particle {
  position: absolute;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 50%;
  animation: float 25s infinite linear;
}

@keyframes float {
  0% { transform: translateY(100vh) scale(0); opacity: 1; }
  100% { transform: translateY(-10vh) scale(1); opacity: 0; }
}

.particle:nth-child(1) { top: 20%; left: 10%; width: 5px; height: 5px; animation-delay: 0s; animation-duration: 15s; }
.particle:nth-child(2) { top: 80%; left: 30%; width: 8px; height: 8px; animation-delay: 2s; animation-duration: 20s; }
.particle:nth-child(30) { top: 50%; left: 90%; width: 6px; height: 6px; animation-delay: 24s; animation-duration: 28s; }

.main-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: var(--text-light);
}

.selection-screen {
  text-align: center;
}

.selection-screen h1 {
  font-size: 3rem;
  font-weight: 300;
  text-shadow: 0 0 15px rgba(0,0,0,0.5);
  margin-bottom: 50px;
}

.char-selector {
  display: flex;
  justify-content: center;
  gap: 40px;
}

.char-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.char-card:hover {
  transform: scale(1.1);
}

.char-avatar {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 5px 20px rgba(0,0,0,0.4);
  margin-bottom: 15px;
  object-fit: cover;
}

.char-name {
  font-size: 1.5rem;
  font-weight: 500;
  text-shadow: 0 0 10px rgba(0,0,0,0.5);
}

.chat-screen {
  width: 100%;
  max-width: 600px;
  height: 80vh;
  max-height: 700px;
}

.chat-wrapper {
  width: 100%;
  height: 100%;
  background: var(--bg-dark-glass);
  backdrop-filter: blur(15px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  display: flex;
  flex-direction: column;
  padding: 20px;
  box-sizing: border-box;
}

.chatbox-transform-enter-active,
.chatbox-transform-leave-active {
  transition: all 0.7s cubic-bezier(0.25, 0.8, 0.25, 1);
}
.chatbox-transform-enter-from,
.chatbox-transform-leave-to {
  opacity: 0;
  transform: scale(0.7) translateY(50px);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 15px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
}

.back-button {
  background: none;
  border: none;
  color: var(--text-light);
  font-size: 1rem;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}
.back-button:hover {
  opacity: 1;
}

.status-light {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #6c757d;
  transition: background 0.3s;
}
.status-light.connecting { background: #ffc107; }
.status-light.connected { background: #28a745; }
.status-light.error { background: #dc3545; }

.chat-window {
  flex-grow: 1;
  overflow-y: auto;
  padding: 15px 5px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-window::-webkit-scrollbar { width: 6px; }
.chat-window::-webkit-scrollbar-track { background: transparent; }
.chat-window::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.3); border-radius: 3px;}

.chat-window .empty-chat {
  text-align: center;
  color: rgba(255,255,255,0.6);
  margin: auto;
}

.chat-window .user, .chat-window .ai {
  padding: 10px 15px;
  border-radius: 15px;
  max-width: 80%;
  word-wrap: break-word;
}

.chat-window .user {
  background: #007bff;
  align-self: flex-end;
  border-bottom-right-radius: 3px;
  margin-left: auto;
}

.chat-window .ai {
  background: rgba(255, 255, 255, 0.1);
  align-self: flex-start;
  border-bottom-left-radius: 3px;
  margin-right: auto;
}

.controls {
  padding-top: 15px;
  text-align: center;
  flex-shrink: 0;
}

.record-btn {
  padding: 15px 30px;
  font-size: 18px;
  border: none;
  border-radius: 50px;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 200px;
}

.record-btn:not(:disabled) {
  background: #007bff;
  color: white;
}

.record-btn:not(:disabled):hover {
  background: #0056b3;
  transform: translateY(-2px);
}

.record-btn.recording {
  background: #dc3545 !important;
  animation: pulse 1.5s infinite;
}

.record-btn:disabled {
  background: #6c757d;
  color: #fff;
  cursor: not-allowed;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(220, 53, 69, 0.7); }
  70% { box-shadow: 0 0 0 10px rgba(220, 53, 69, 0); }
  100% { box-shadow: 0 0 0 0 rgba(220, 53, 69, 0); }
}
</style>