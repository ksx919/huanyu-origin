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
      <TransitionGroup appear tag="div" class="char-selector" @before-enter="beforeCharEnter" @enter="enterChar">
        <div v-for="(char, index) in characters" :key="char.id" class="char-card" @click="selectCharacter(char.id)" :data-index="index">
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

          <div v-if="showAuthWarning || !hasToken" class="auth-warning">请先登录以获取令牌</div>

          <div class="mode-switcher">
            <button :class="{ active: chatMode === 'voice' }" @click="setChatMode('voice')">实时语音</button>
            <button :class="{ active: chatMode === 'text' }" @click="setChatMode('text')">文字消息</button>
          </div>

          <div class="chat-window">
            <div v-if="chatMode === 'voice'" class="voice-avatar-container">
              <img :src="currentCharacterAvatar" class="voice-avatar" alt="Character Avatar"/>
            </div>

            <template v-if="chatMode === 'text'">
              <div v-for="(msg, index) in conversation" :key="index" :class="msg.role">
                <strong>{{ msg.role === 'user' ? '你' : getCharacterName(selectedCharacter) }}:</strong>
                {{ msg.content }}
                <button v-if="msg.role === 'ai' && msg.audioUrl" @click="playAudio(msg.audioUrl)" class="play-audio-btn">▶️</button>
              </div>
            </template>

            <div v-if="conversation.length === 0" class="empty-chat">
              {{ chatMode === 'voice' ? '正在进行实时语音通话...' : '可以开始发送消息了！' }}
            </div>
          </div>

          <div class="controls">
            <div v-if="chatMode === 'voice'" class="voice-controls">
              <button class="record-btn end-call" @click="stopRealtimeVoice">
                结束对话
              </button>
            </div>

            <div v-else class="text-controls">
              <input
                  type="text"
                  v-model="textInput"
                  @keyup.enter="sendTextMessage"
                  placeholder="输入消息..."
              />
              <button @click="sendTextMessage" class="send-btn">发送</button>
              <button @mousedown="startVoiceToText" @mouseup="stopVoiceToText" class="voice-to-text-btn" :class="{ recording: isRecording }">🎤</button>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue';
import gsap from 'gsap';
import { apiBaseURL, getAuthHeader } from '../utils/axios';

// --- 类型和状态定义 ---
interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
  timestamp?: number;
  audioUrl?: string;
}

const characters = ref([
  { id: 'Hutao', name: '胡桃', avatar: '/Hutao.jpg' },
  { id: 'Venti', name: '温迪', avatar: '/Venti.jpg' },
  { id: 'Xiaogong', name: '宵宫', avatar: '/Xiaogong.jpg' }
]);

const selectedCharacter = ref<string | null>(null);
const conversation = ref<ChatMessage[]>([]);
const connectionStatus = ref<'disconnected' | 'connecting' | 'connected' | 'error'>('disconnected');
const isRecording = ref(false); // 在语音模式下，此变量代表通话是否正在进行
const chatMode = ref<'voice' | 'text'>('voice');
const textInput = ref('');
const showAuthWarning = ref(false);
const hasToken = computed(() => !!localStorage.getItem('token'));

let socket: WebSocket | null = null;
let audioContext: AudioContext | null = null;
let mediaStream: MediaStream | null = null;
let audioWorkletNode: AudioWorkletNode | null = null;
let audioPlayer = new Audio();

// --- 计算属性 ---
const currentCharacterAvatar = computed(() => {
  return characters.value.find(c => c.id === selectedCharacter.value)?.avatar || '';
});

// --- 核心逻辑 ---
const selectCharacter = (charId: string) => {
  selectedCharacter.value = charId;
  conversation.value = [];
  // 后端将根据登录用户与角色类型维护上下文，无需前端 sessionId
  if (chatMode.value === 'voice') {
    startRealtimeVoice();
  }
};

const deselectCharacter = () => {
  selectedCharacter.value = null;
  conversation.value = [];
  disconnectWebSocket();
};

const setChatMode = (mode: 'voice' | 'text') => {
  if (chatMode.value === mode) return;

  chatMode.value = mode;
  disconnectWebSocket();
  conversation.value = [];

  if (mode === 'voice' && selectedCharacter.value) {
    startRealtimeVoice();
  }
};

const playAudio = (url: string) => {
  audioPlayer.src = url;
  audioPlayer.play();
};

//模式一：实时语音逻辑
const startRealtimeVoice = async () => {
  if (isRecording.value) return;
  console.log('实时语音模式：自动开始连接...');
  isRecording.value = true;
  connectionStatus.value = 'connected'; // 模拟
  // TODO: 在这里添加获取麦克风和发送音频流的逻辑
};

const stopRealtimeVoice = () => {
  if (!isRecording.value) return;
  console.log('实时语音模式：用户点击结束对话');
  disconnectWebSocket();
};

//模式二：文字消息逻辑
const charTypeMap: Record<string, number> = { Xiaogong: 0, Venti: 1, Hutao: 2 };
const sendTextMessage = async () => {
  if (!hasToken.value) { showAuthWarning.value = true; return; }
  if (!textInput.value.trim() || !selectedCharacter.value) return;
  const userMessage = textInput.value;
  const type = charTypeMap[selectedCharacter.value] ?? 0;
  conversation.value.push({ role: 'user', content: userMessage });
  textInput.value = '';

  // 预先插入一个空的AI消息，用于实时更新内容
  conversation.value.push({ role: 'ai', content: '' });
  const aiIndex = conversation.value.length - 1;

  try {
    const url = `/ai/chat?message=${encodeURIComponent(userMessage)}&type=${type}`;
    const auth = getAuthHeader();
    console.log('调用 /ai/chat headers:', auth, 'token:', localStorage.getItem('token'));
    const resp = await fetch(url, { headers: { ...auth } });
    if (!resp.ok || !resp.body) {
      conversation.value[aiIndex].content = '请求失败，稍后重试';
      return;
    }

    const reader = resp.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let accumulated = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      accumulated += chunk;
      conversation.value[aiIndex].content = accumulated;
    }
  } catch (e) {
    conversation.value[aiIndex].content = '网络异常，请稍后重试';
  }
};
const startVoiceToText = async () => {
  isRecording.value = true;
  console.log('文字模式：开始录音...');
  // TODO: 开始录音逻辑
};
const stopVoiceToText = () => {
  isRecording.value = false;
  console.log('文字模式：停止录音...');
  // TODO: 停止录音并发送后端识别
};

//通用和辅助函数
const disconnectWebSocket = () => {
  if (socket) socket.close();
  socket = null;

  if (mediaStream) mediaStream.getTracks().forEach(track => track.stop());
  mediaStream = null;

  if (audioContext && audioContext.state !== 'closed') audioContext.close();
  audioContext = null;

  isRecording.value = false;
  connectionStatus.value = 'disconnected';
};

const getCharacterName = (char: string | null) => characters.value.find(c => c.id === char)?.name || '未知角色';

// 动画函数
const beforeTitleEnter = (el: Element) => { (el as HTMLElement).style.opacity = '0'; (el as HTMLElement).style.transform = 'translateY(-30px)'; };
const enterTitle = (el: Element, done: () => void) => { gsap.to(el, { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out', onComplete: done }); };
const beforeCharEnter = (el: Element) => { (el as HTMLElement).style.opacity = '0'; (el as HTMLElement).style.transform = 'translateY(30px) scale(0.9)'; };
const enterChar = (el: Element, done: () => void) => {
  const index = parseInt((el as HTMLElement).dataset.index || '0');
  gsap.to(el, { opacity: 1, y: 0, scale: 1, duration: 0.6, delay: index * 0.15 + 0.5, ease: 'power2.out', onComplete: done });
};

onUnmounted(() => {
  disconnectWebSocket();
});
</script>

<style>

.main-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: var(--text-light);
}

.chat-screen {
  width: 100%;
  max-width: 750px;
  height: 80vh;
  max-height: 700px;
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

.status-light {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #6c757d;
}

.status-light.connected {
  background: #28a745;
}


.chat-window {
  display: flex;
  flex-direction: column;
  flex-grow: 1;
  overflow-y: auto;
  padding: 15px 5px;
}

.voice-avatar-container {
  flex-grow: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.voice-avatar {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);
  animation: subtle-float 4s ease-in-out infinite;
}

@keyframes subtle-float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-15px);
  }
}


.chat-window::-webkit-scrollbar { width: 6px; }
.chat-window::-webkit-scrollbar-track { background: transparent; }
.chat-window::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.3); border-radius: 3px;}

.chat-window .user, .chat-window .ai {
  padding: 10px 15px;
  border-radius: 15px;
  max-width: 80%;
  word-wrap: break-word;
}

.chat-window .user {
  background: #007bff;
  align-self: flex-end;
}

.chat-window .ai {
  background: rgba(255, 255, 255, 0.1);
  align-self: flex-start;
}

.empty-chat {
  text-align: center;
  color: rgba(255,255,255,0.6);
  margin: auto;
}

.play-audio-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  margin-left: 8px;
  font-size: 14px;
}


.mode-switcher {
  display: flex;
  justify-content: center;
  margin-bottom: 15px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  padding: 5px;
  flex-shrink: 0;
}
.mode-switcher button {
  flex: 1;
  padding: 8px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}
.mode-switcher button.active {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.controls {
  padding-top: 15px;
  text-align: center;
  flex-shrink: 0;
}

.voice-controls .record-btn.end-call {
  background: #dc3545;
  color: white;
  padding: 15px 30px;
  font-size: 18px;
  border: none;
  border-radius: 50px;
  cursor: pointer;
}

.voice-controls .record-btn.end-call:hover {
  background: #c82333;
}

.text-controls {
  display: flex;
  gap: 10px;
}
.text-controls input {
  flex-grow: 1;
  padding: 10px 15px;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(0, 0, 0, 0.2);
  color: white;
}
.send-btn, .voice-to-text-btn {
  padding: 10px 15px;
  border-radius: 20px;
  border: none;
  cursor: pointer;
}
.send-btn {
  background: #007bff;
  color: white;
}
.voice-to-text-btn.recording {
  background: #dc3545;
  color: white;
}
</style>