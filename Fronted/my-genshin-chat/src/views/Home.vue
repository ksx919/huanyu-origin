<template>
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



        <div class="chat-window" ref="chatWindowRef">
          <div v-if="chatMode === 'voice'" class="voice-avatar-container">
            <img :src="currentCharacterAvatar" class="voice-avatar" alt="Character Avatar"/>
          </div>

          <template v-if="chatMode === 'text'">
            <div
                v-for="(msg, index) in conversation"
                :key="index"
                class="message-row"
                :class="msg.role === 'user' ? 'right' : 'left'"
            >
              <img
                  class="avatar"
                  :src="msg.role === 'user' ? userAvatarUrl : currentCharacterAvatar"
                  :alt="msg.role === 'user' ? '用户头像' : '角色头像'"
                  @error="onAvatarError(msg.role)"
              />
              <div class="bubble">
                <button
                    v-if="msg.role === 'ai' && msg.content"
                    @click="playMessageAudio(msg, index)"
                    class="play-audio-btn"
                    :aria-label="isPlaying(index) ? '终止播放' : '播放语音'"
                    :title="isPlaying(index) ? '终止播放' : '播放语音'"
                >{{ isPlaying(index) ? '⏹' : '▶' }}</button>
                {{ msg.content }}
              </div>
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
            <button @click="openCall" class="call-btn" :title="canOpenCall ? '语音通话' : '请先发送消息后再申请通话'">📞</button>
          </div>
        </div>

        <!-- Toast 弹窗容器 -->
        <transition name="toast-fade">
          <div v-if="toastVisible" class="toast">{{ toastText }}</div>
        </transition>

        <!-- 通话资格提示弹窗 -->
        <transition name="call-fade">
          <div v-if="showCallHint" class="modal-overlay">
            <div class="modal-card">
              <div class="modal-title">提示</div>
              <div class="modal-content">请先向该角色发送一条消息，再申请通话</div>
              <button class="modal-ok" @click="showCallHint = false">我知道了</button>
            </div>
          </div>
        </transition>

        <!-- 语音识别加载遮罩 -->
        <transition name="call-fade">
          <div v-if="isTranscribing" class="transcribe-overlay">
            <div class="spinner"></div>
            <div class="transcribe-text">正在识别...</div>
          </div>
        </transition>
      </div>
    </Transition>
  </div>

  <!-- 语音通话弹窗 -->
  <transition name="call-fade">
    <div v-if="isCalling" class="call-overlay">
      <div class="call-card">
        <img :src="currentCharacterAvatar" class="call-avatar" alt="角色头像" />
        <div class="call-name">{{ getCharacterName(selectedCharacter) }}</div>
        <div class="call-status">{{ callStatusText }}</div>
        <div class="call-actions">
          <button class="mute-btn" @click="toggleMute">{{ isMuted ? '取消静音' : '静音' }}</button>
          <button class="end-call-btn" @click="endCall">挂断</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, nextTick } from 'vue';
import gsap from 'gsap';
import { apiBaseURL, getAuthHeader } from '../utils/axios';

// --- 类型和状态定义 ---
interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
  timestamp?: number;
  audioUrl?: string;
  id?: string;
  status?: string;
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
// 语音通话弹窗与状态
const isCalling = ref(false);
const isMuted = ref(false);
const isTranscribing = ref(false);
const showCallHint = ref(false);
const callStatusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connecting': return '正在连接...';
    case 'connected': return '通话中';
    case 'error': return '连接失败';
    default: return '';
  }
});
const textInput = ref('');
const showAuthWarning = ref(false);
const hasToken = computed(() => !!localStorage.getItem('token'));

let socket: WebSocket | null = null;
let audioContext: AudioContext | null = null;
let mediaStream: MediaStream | null = null;
let audioWorkletNode: AudioWorkletNode | null = null;
let audioPlayer = new Audio();
let recordBuffers: Float32Array[] = [];
let recordStopTimer: ReturnType<typeof setTimeout> | null = null;
let scriptNode: ScriptProcessorNode | null = null;
let mediaSource: MediaStreamAudioSourceNode | null = null;

// 当前正在播放的消息索引（仅文字模式）
const playingIndex = ref<number | null>(null);

// 播放结束时重置状态
audioPlayer.addEventListener('ended', () => {
  playingIndex.value = null;
});

// 判断某条消息是否正在播放
const isPlaying = (idx: number) => {
  return playingIndex.value === idx && !audioPlayer.paused;
};

// 确保互斥播放：开始播放前先暂停任何正在播放的音频
const ensurePlay = (url: string, idx: number) => {
  try {
    if (!audioPlayer.paused) audioPlayer.pause();
    audioPlayer.currentTime = 0; // 终止上一个播放
  } catch (e) {}
  audioPlayer.src = url;
  audioPlayer.play();
  playingIndex.value = idx;
};

// Toast 弹窗状态
const toastText = ref('');
const toastVisible = ref(false);
let toastTimer: ReturnType<typeof setTimeout> | null = null;

const showToast = (text: string, duration = 2000) => {
  toastText.value = text;
  toastVisible.value = true;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toastVisible.value = false; }, duration);
};

// 滚动定位到最下方
const chatWindowRef = ref<HTMLElement | null>(null);
const scrollToBottom = () => {
  const el = chatWindowRef.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
};

// 用户头像（私有空间签名URL）与过期刷新
const userAvatarUrl = ref<string>('');
let userAvatarExpireAt: number | null = null;
let userAvatarTimer: ReturnType<typeof setTimeout> | null = null;

const scheduleAvatarRefresh = (expiresSec: number) => {
  // 预留5秒冗余
  const ttl = Math.max(1, expiresSec - 5);
  if (userAvatarTimer) clearTimeout(userAvatarTimer);
  userAvatarExpireAt = Date.now() + ttl * 1000;
  userAvatarTimer = setTimeout(() => {
    refreshUserAvatar(expiresSec).catch(() => {});
  }, ttl * 1000);
};

const refreshUserAvatar = async (expiresSec: number = 600) => {
  try {
    if (!hasToken.value) { return; }
    const auth = getAuthHeader();
    const url = `/file/private-url?expires=${expiresSec}`;
    const resp = await fetch(url, { headers: { ...auth } });
    if (!resp.ok) return;
    const data = await resp.json();
    const privUrl = data?.data || data?.content || '';
    if (typeof privUrl === 'string' && privUrl) {
      userAvatarUrl.value = privUrl;
      scheduleAvatarRefresh(expiresSec);
    }
  } catch (e) {
    // 静默失败，稍后可重试
  }
};

const ensureUserAvatarFresh = async () => {
  const now = Date.now();
  if (!userAvatarUrl.value || (userAvatarExpireAt !== null && now >= userAvatarExpireAt - 10_000)) {
    await refreshUserAvatar(600);
  }
};

const onAvatarError = (role: 'user' | 'ai') => {
  if (role === 'user') {
    // 加急刷新用户头像链接
    refreshUserAvatar(600).catch(() => {});
  }
};

// --- 计算属性 ---
const currentCharacterAvatar = computed(() => {
  return characters.value.find(c => c.id === selectedCharacter.value)?.avatar || '';
});

// 只有与当前角色发送过至少一条消息，才能申请语音通话
const canOpenCall = computed(() => {
  if (!selectedCharacter.value) return false;
  return conversation.value.some(m => m.role === 'user');
});

// --- 核心逻辑 ---
const selectCharacter = (charId: string) => {
  selectedCharacter.value = charId;
  conversation.value = [];
  // 每次进入聊天界面，先展示历史记录
  chatMode.value = 'text';
  ensureUserAvatarFresh();
  loadHistory();
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
    // 旧的“实时语音模式”入口不再使用，改为点击📞触发
    // 保留兼容：如被调用，仍启动实时语音
    startRealtimeVoice();
  } else if (mode === 'text' && selectedCharacter.value) {
    ensureUserAvatarFresh();
    loadHistory();
  }
};

const playAudio = (url: string) => {
  audioPlayer.src = url;
  audioPlayer.play();
};

// 按消息播放对应语音（仅文字模式）
const playMessageAudio = async (msg: ChatMessage, index: number) => {
  try {
    if (!hasToken.value) { showAuthWarning.value = true; showToast('请先登录以获取令牌'); return; }
    if (!selectedCharacter.value) { showToast('请先选择角色'); return; }
    if (!msg.content || !msg.content.trim()) { showToast('文本为空，无法播放'); return; }

    // 若当前消息正在播放，则改为暂停（切换为▶）
    if (playingIndex.value === index && !audioPlayer.paused) {
      audioPlayer.pause();
      audioPlayer.currentTime = 0; // 终止播放并归零
      playingIndex.value = null;
      showToast('已终止播放');
      return;
    }

    const type = charTypeMap[selectedCharacter.value] ?? 0;
    const auth = getAuthHeader();

    // 已有语音链接则直接播放
    if (msg.audioUrl) { ensurePlay(msg.audioUrl, index); showToast('开始播放语音'); return; }

    // 计算消息ID（与后端一致：SHA-1(content)）
    const sha1 = msg.id || await sha1Hex(msg.content.trim());
    msg.id = sha1;
    const fileKey = `huanyu/audio/${type}-${sha1}.wav`;

    const existsResp = await fetch(`/file/exists?fileName=${encodeURIComponent(fileKey)}`, { headers: { ...auth } });
    if (!existsResp.ok) { showToast('检查语音失败'); return; }
    const existsData = await existsResp.json();

    if (existsData?.data === true) {
      const privResp = await fetch(`/file/private-url-by-key?fileName=${encodeURIComponent(fileKey)}`, { headers: { ...auth } });
      if (!privResp.ok) { showToast('获取语音链接失败'); return; }
      const privData = await privResp.json();
      const audioUrl = privData?.data || privData?.content;
      if (typeof audioUrl === 'string' && audioUrl) { msg.audioUrl = audioUrl; ensurePlay(audioUrl, index); showToast('开始播放语音'); }
      else { showToast('语音未生成或已过期'); }
    } else { showToast('语音未生成或已过期'); }
  } catch (e) {
    console.warn('按消息播放语音异常：', e);
    showToast('网络异常，请稍后重试');
  }
};

// 计算文本的 SHA-1 十六进制摘要（与后端一致）
async function sha1Hex(str: string): Promise<string> {
  const enc = new TextEncoder();
  const buf = enc.encode(str);
  const hash = await crypto.subtle.digest('SHA-1', buf);
  const arr = new Uint8Array(hash);
  let hex = '';
  for (let i = 0; i < arr.length; i++) {
    hex += arr[i].toString(16).padStart(2, '0');
  }
  return hex;
}

//模式一：实时语音逻辑
const startRealtimeVoice = async () => {
  if (isRecording.value) return;
  console.log('实时语音模式：自动开始连接...');
  isRecording.value = true;
  connectionStatus.value = 'connecting';
  isCalling.value = true;

  try {
    await connectWebSocket();
    await setupMicCapture();
    connectionStatus.value = 'connected';
    showToast('通话已连接');
  } catch (err) {
    console.warn('建立实时语音通话失败：', err);
    connectionStatus.value = 'error';
    isRecording.value = false;
    isCalling.value = false;
    showToast('连接失败，请稍后重试');
  }
};

const stopRealtimeVoice = () => {
  if (!isRecording.value && !isCalling.value) return;
  console.log('实时语音模式：用户点击结束对话');
  disconnectWebSocket();
  isRecording.value = false;
  isCalling.value = false;
  connectionStatus.value = 'disconnected';
};

// 语音通话弹窗控制
const openCall = () => {
  if (!hasToken.value) { showAuthWarning.value = true; showToast('请先登录以获取令牌'); return; }
  if (!selectedCharacter.value) { showToast('请先选择角色'); return; }
  if (!canOpenCall.value) { showCallHint.value = true; return; }
  if (isCalling.value) { showToast('已在通话中'); return; }
  startRealtimeVoice();
};

const endCall = () => {
  stopRealtimeVoice();
  showToast('已结束通话');
};

const toggleMute = () => {
  isMuted.value = !isMuted.value;
  // 触发打断：如果正在播放AI音频或有音频积压，通知后端并本地停止播放
  if (socket && socket.readyState === WebSocket.OPEN) {
    try { socket.send(JSON.stringify({ type: 'interrupt' })); } catch {}
  }
  stopIncomingPlayback();
  showToast(isMuted.value ? '已静音（打断AI语音）' : '已取消静音');
};

//模式二：文字消息逻辑
const charTypeMap: Record<string, number> = { Xiaogong: 0, Venti: 1, Hutao: 2 };

// 拉取历史聊天记录并渲染到会话
const loadHistory = async () => {
  try {
    if (!hasToken.value) { showAuthWarning.value = true; return; }
    if (!selectedCharacter.value) return;
    const type = charTypeMap[selectedCharacter.value] ?? 0;
    const auth = getAuthHeader();
    const url = `/ai/get-chat?type=${type}`;
    const resp = await fetch(url, { headers: { ...auth } });
    if (!resp.ok) {
      console.warn('获取历史聊天记录失败，HTTP状态：', resp.status);
      return;
    }
    const list = await resp.json();
    if (!Array.isArray(list)) {
      console.warn('历史聊天记录格式异常：期望数组，实际：', list);
      return;
    }
    // 后端 ChatMessageResp: { messageType: 'USER'|'ASSISTANT', content: string }
    const mapped: ChatMessage[] = list
        .filter((m: any) => m && typeof m.content === 'string' && typeof m.messageType === 'string')
        .map((m: any) => ({
          role: m.messageType === 'USER' ? 'user' : 'ai',
          content: m.content,
        } as ChatMessage));
    conversation.value = mapped;
    await nextTick();
    scrollToBottom();

    // 异步为AI消息计算ID，便于按消息检索语音
    conversation.value.forEach(async (msg) => {
      if (msg.role === 'ai' && msg.content) {
        msg.id = await sha1Hex(msg.content.trim());
      }
    });
  } catch (e) {
    console.warn('拉取历史聊天记录异常：', e);
  }
};
const sendTextMessage = async () => {
  if (!hasToken.value) { showAuthWarning.value = true; return; }
  if (!textInput.value.trim() || !selectedCharacter.value) return;
  const userMessage = textInput.value;
  const type = charTypeMap[selectedCharacter.value] ?? 0;
  conversation.value.push({ role: 'user', content: userMessage });
  textInput.value = '';

  await nextTick();
  scrollToBottom();

  // 预先插入一个空的AI消息，用于实时更新内容
  conversation.value.push({ role: 'ai', content: '' });
  const aiIndex = conversation.value.length - 1;

  await nextTick();
  scrollToBottom();

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
      await nextTick();
      scrollToBottom();
    }

    // 流式文本完成后：根据固定命名规则检查七牛云是否存在音频，存在则取私有链接用于播放
    try {
      const text = accumulated.trim();
      if (text) {
        const sha1 = await sha1Hex(text);
        conversation.value[aiIndex].id = sha1;
        const fileKey = `huanyu/audio/${type}-${sha1}.wav`;
        const existsResp = await fetch(`/file/exists?fileName=${encodeURIComponent(fileKey)}`, { headers: { ...auth } });
        if (existsResp.ok) {
          const existsData = await existsResp.json();
          if (existsData?.data === true) {
            const privResp = await fetch(`/file/private-url-by-key?fileName=${encodeURIComponent(fileKey)}`, { headers: { ...auth } });
            if (privResp.ok) {
              const privData = await privResp.json();
              const url = privData?.data || privData?.content;
              if (url) conversation.value[aiIndex].audioUrl = url;
            }
          }
        }
      }
    } catch (err) {
      console.warn('检查/生成私有链接失败: ', err);
    }
  } catch (e) {
    conversation.value[aiIndex].content = '网络异常，请稍后重试';
  }
};
const startVoiceToText = async () => {
  if (isRecording.value) return;
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    recordBuffers = [];
    mediaSource = audioContext.createMediaStreamSource(mediaStream);
    scriptNode = audioContext.createScriptProcessor(4096, 1, 1);
    scriptNode.onaudioprocess = (e) => {
      const input = e.inputBuffer.getChannelData(0);
      recordBuffers.push(new Float32Array(input));
    };
    mediaSource.connect(scriptNode);
    scriptNode.connect(audioContext.destination);
    isRecording.value = true;
    showToast('开始录音（最多10秒）');
    if (recordStopTimer) clearTimeout(recordStopTimer);
    recordStopTimer = setTimeout(() => { stopVoiceToText(); }, 10000);
  } catch (err) {
    isRecording.value = false;
    showToast('无法访问麦克风');
  }
};
const stopVoiceToText = () => {
  if (!isRecording.value) return;
  isRecording.value = false;
  if (recordStopTimer) { clearTimeout(recordStopTimer); recordStopTimer = null; }
  try {
    if (scriptNode) { scriptNode.disconnect(); scriptNode.onaudioprocess = null; }
    if (mediaSource) mediaSource.disconnect();
    if (mediaStream) mediaStream.getTracks().forEach(t => t.stop());
    if (audioContext && audioContext.state !== 'closed') audioContext.close();
  } catch {}
  scriptNode = null;
  mediaSource = null;
  mediaStream = null;
  audioContext = null;

  if (!recordBuffers.length) { showToast('未录到音频'); return; }

  const merged = mergeFloat32(recordBuffers);
  const wav = encodeWAV(merged, 16000);

  const auth = getAuthHeader();
  isTranscribing.value = true;
  fetch('/audio/upload-raw', {
    method: 'POST',
    headers: { ...auth, 'Content-Type': 'audio/wav' },
    body: wav,
  }).then(async (resp) => {
    if (!resp.ok) { showToast('语音识别失败'); return; }
    const data = await resp.json().catch(() => null);
    if (!data || data.success !== true) {
      showToast((data && data.message) ? data.message : '语音识别失败');
      return;
    }
    const text = typeof data.data === 'string' ? data.data : (typeof data.content === 'string' ? data.content : '');
    textInput.value = text || '';
    showToast('已转文字');
  }).catch(() => {
    showToast('网络异常');
  }).finally(() => { isTranscribing.value = false; });
};

//通用和辅助函数
const disconnectWebSocket = () => {
  if (socket) socket.close();
  socket = null;

  if (mediaStream) mediaStream.getTracks().forEach(track => track.stop());
  mediaStream = null;

  if (audioContext && audioContext.state !== 'closed') audioContext.close();
  audioContext = null;

  // 重置流式播放状态
  resetIncomingPlaybackState();

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
  if (toastTimer) clearTimeout(toastTimer);
});

function mergeFloat32(chunks: Float32Array[]): Float32Array {
  let len = 0; for (const c of chunks) len += c.length;
  const result = new Float32Array(len);
  let offset = 0;
  for (const c of chunks) { result.set(c, offset); offset += c.length; }
  return result;
}

function encodeWAV(float32: Float32Array, outSampleRate: number): ArrayBuffer {
  const inSampleRate = (audioContext && audioContext.sampleRate) || 44100;
  const resampled = resampleLinear(float32, inSampleRate, outSampleRate);
  const buffer = new ArrayBuffer(44 + resampled.length * 2);
  const view = new DataView(buffer);
  writeString(view, 0, 'RIFF');
  view.setUint32(4, 36 + resampled.length * 2, true);
  writeString(view, 8, 'WAVE');
  writeString(view, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, outSampleRate, true);
  view.setUint32(28, outSampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeString(view, 36, 'data');
  view.setUint32(40, resampled.length * 2, true);
  floatTo16BitPCM(view, 44, resampled);
  return buffer;
}

function resampleLinear(data: Float32Array, inRate: number, outRate: number): Int16Array {
  if (inRate === outRate) {
    const out = new Int16Array(data.length);
    for (let i = 0; i < data.length; i++) out[i] = to16(data[i]);
    return out;
  }
  const ratio = inRate / outRate;
  const outLen = Math.floor(data.length / ratio);
  const out = new Int16Array(outLen);
  let pos = 0;
  for (let i = 0; i < outLen; i++) {
    const idx = i * ratio;
    const i0 = Math.floor(idx);
    const i1 = Math.min(i0 + 1, data.length - 1);
    const frac = idx - i0;
    const sample = data[i0] * (1 - frac) + data[i1] * frac;
    out[pos++] = to16(sample);
  }
  return out;
}

function to16(sample: number): number { return Math.max(-1, Math.min(1, sample)) * 0x7FFF | 0; }
function writeString(view: DataView, offset: number, str: string) { for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i)); }
function floatTo16BitPCM(view: DataView, offset: number, data: Int16Array) { for (let i = 0; i < data.length; i++, offset += 2) view.setInt16(offset, data[i], true); }

// ====== WebSocket 语音通话核心逻辑 ======
let wsPlayhead = 0;
let wavHeaderParsed = false;
let wavSampleRate = 16000;
let wavChannels = 1;
let wavBits = 16;
let pendingSources: AudioBufferSourceNode[] = [];
let headerBuffer: Uint8Array | null = null;
let wsAiIndex: number | null = null;

function getToken(): string | null { return localStorage.getItem('token'); }

async function connectWebSocket(): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const token = getToken();
    if (!token) { reject(new Error('缺少令牌')); return; }
    const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    const wsUrl = `${proto}${location.host}/ws-audio?token=${encodeURIComponent(token)}`;
    try {
      socket = new WebSocket(wsUrl);
      // 使用 ArrayBuffer 简化二进制处理
      try { socket.binaryType = 'arraybuffer'; } catch {}
    } catch (e) {
      return reject(e);
    }

    socket.onopen = () => {
      // 首包发送角色信息，后端据此决定AI角色与TTS端口
      try {
        socket!.send(JSON.stringify({ characterId: selectedCharacter.value || 'Xiaogong' }));
      } catch {}
      resolve();
    };

    socket.onmessage = async (evt) => {
      const data = evt.data;
      if (typeof data === 'string') {
        try { handleWsText(JSON.parse(data)); } catch {}
      } else if (data instanceof ArrayBuffer) {
        handleWsBinary(new Uint8Array(data));
      } else if (data instanceof Blob) {
        const buf = new Uint8Array(await data.arrayBuffer());
        handleWsBinary(buf);
      }
    };

    socket.onerror = (e) => {
      console.warn('WebSocket 错误：', e);
    };

    socket.onclose = () => {
      connectionStatus.value = 'disconnected';
    };
  });
}

async function setupMicCapture(): Promise<void> {
  mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
  mediaSource = audioContext.createMediaStreamSource(mediaStream);
  // 减小缓冲区以更快推送语音上行数据，降低识别延迟
  scriptNode = audioContext.createScriptProcessor(2048, 1, 1);
  scriptNode.onaudioprocess = (e) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    if (isMuted.value) return; // 静音时不发送音频
    const input = e.inputBuffer.getChannelData(0);
    // 将输入帧转换为PCM16k并发送
    try {
      const pcm16 = resampleLinear(input, audioContext!.sampleRate, 16000);
      const ab = new ArrayBuffer(pcm16.length * 2);
      const dv = new DataView(ab);
      for (let i = 0; i < pcm16.length; i++) dv.setInt16(i * 2, pcm16[i], true);
      socket!.send(ab);
    } catch (err) {
      console.warn('发送PCM失败：', err);
    }
  };
  mediaSource.connect(scriptNode);
  scriptNode.connect(audioContext.destination);
  try { await audioContext.resume(); } catch {}
}

function handleWsText(msg: any) {
  const t = msg?.type;
  if (t === 'ai_text') {
    // 语音通话期间不在前端会话区展示AI文本分片（仍然会写入Redis供上下文使用）
    if (isCalling.value) return;
    if (wsAiIndex == null) { conversation.value.push({ role: 'ai', content: '' }); wsAiIndex = conversation.value.length - 1; }
    const chunk = typeof msg.chunk === 'string' ? msg.chunk : '';
    conversation.value[wsAiIndex].content += chunk;
    nextTick().then(scrollToBottom);
  } else if (t === 'ai_error') {
    showToast('AI生成失败：' + (msg?.message || '未知错误'));
  } else if (t === 'audio_start') {
    prepareIncomingPlayback();
  } else if (t === 'audio_end') {
    finalizeIncomingPlayback();
    wsAiIndex = null;
  } else if (t === 'audio_error') {
    showToast('音频流错误：' + (msg?.message || '未知错误'));
    stopIncomingPlayback();
    wsAiIndex = null;
  }
}

function prepareIncomingPlayback() {
  // 开始新一段音频：仅重置头解析状态，不打断已调度播放，保持串行队列
  wavHeaderParsed = false;
  headerBuffer = null;
  // 若当前没有已调度的buffer，则将播放指针设为当前时间；否则保持指针，保证新段在上一段之后播放
  if (pendingSources.length === 0) {
    wsPlayhead = audioContext ? audioContext.currentTime : 0;
  }
  try { audioContext?.resume(); } catch {}
}

function resetIncomingPlaybackState() {
  wavHeaderParsed = false;
  headerBuffer = null;
  wsPlayhead = 0;
  // 停止所有已调度的buffer
  for (const src of pendingSources) { try { src.stop(); } catch {} }
  pendingSources = [];
}

function stopIncomingPlayback() {
  resetIncomingPlaybackState();
}

function finalizeIncomingPlayback() {
  // 不需要额外处理，已按块调度
}

function handleWsBinary(chunk: Uint8Array) {
  if (!audioContext) return;
  // 解析WAV头：更健壮地遍历chunk，定位fmt与data段
  if (!wavHeaderParsed) {
    // 追加缓冲
    if (!headerBuffer) headerBuffer = chunk;
    else {
      const merged = new Uint8Array(headerBuffer.length + chunk.length);
      merged.set(headerBuffer, 0);
      merged.set(chunk, headerBuffer.length);
      headerBuffer = merged;
    }
    const parsed = tryParseWavHeader(headerBuffer);
    if (!parsed) return; // 头部尚不完整，继续累积
    wavChannels = parsed.channels;
    wavSampleRate = parsed.sampleRate;
    wavBits = parsed.bitsPerSample;
    wavHeaderParsed = true;
    // 将头部后的首段数据投递播放
    const firstData = headerBuffer.subarray(parsed.dataOffset);
    headerBuffer = null;
    if (firstData.length) pushPcmToPlay(firstData);
    return;
  }
  // 正常数据块
  pushPcmToPlay(chunk);
}

function pushPcmToPlay(pcmBytes: Uint8Array) {
  try {
    const bytesPerSample = wavBits / 8;
    if (wavBits !== 16) {
      // 仅支持16位深度
      console.warn('不支持的WAV位深：', wavBits);
      return;
    }
    const frameCount = pcmBytes.length / (bytesPerSample * wavChannels);
    if (frameCount <= 0) return;
    const dv = new DataView(pcmBytes.buffer, pcmBytes.byteOffset, pcmBytes.byteLength);
    const buf = audioContext!.createBuffer(Math.min(wavChannels, 2), frameCount, wavSampleRate);

    if (wavChannels === 1) {
      const ch0 = new Float32Array(frameCount);
      for (let i = 0; i < frameCount; i++) {
        const s = dv.getInt16(i * 2, true);
        ch0[i] = s / 0x7FFF;
      }
      buf.copyToChannel(ch0, 0);
    } else {
      // 仅支持前两声道；更多声道时丢弃其余
      const ch0 = new Float32Array(frameCount);
      const ch1 = new Float32Array(frameCount);
      for (let i = 0; i < frameCount; i++) {
        const base = i * 2 * bytesPerSample;
        const l = dv.getInt16(base, true);
        const r = dv.getInt16(base + 2, true);
        ch0[i] = l / 0x7FFF;
        ch1[i] = r / 0x7FFF;
      }
      buf.copyToChannel(ch0, 0);
      buf.copyToChannel(ch1, 1);
    }
    const src = audioContext!.createBufferSource();
    src.buffer = buf;
    src.connect(audioContext!.destination);
    const startAt = Math.max(audioContext!.currentTime, wsPlayhead);
    src.start(startAt);
    wsPlayhead = startAt + buf.duration;
    pendingSources.push(src);
    src.onended = () => { const idx = pendingSources.indexOf(src); if (idx >= 0) pendingSources.splice(idx, 1); };
  } catch (e) {
    console.warn('播放PCM失败：', e);
  }
}

function readFourCC(view: DataView, offset: number): string {
  return String.fromCharCode(view.getUint8(offset)) +
      String.fromCharCode(view.getUint8(offset + 1)) +
      String.fromCharCode(view.getUint8(offset + 2)) +
      String.fromCharCode(view.getUint8(offset + 3));
}

function tryParseWavHeader(buf: Uint8Array): { sampleRate: number; channels: number; bitsPerSample: number; dataOffset: number } | null {
  if (buf.length < 12) return null;
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const riff = readFourCC(view, 0);
  const wave = readFourCC(view, 8);
  if (riff !== 'RIFF' || wave !== 'WAVE') {
    // 若非WAV，则当作原始PCM处理
    return { sampleRate: wavSampleRate, channels: wavChannels, bitsPerSample: wavBits, dataOffset: 0 };
  }
  let offset = 12;
  let channels = 1;
  let sampleRate = 16000;
  let bits = 16;
  let haveFmt = false;
  while (offset + 8 <= buf.length) {
    const chunkId = readFourCC(view, offset);
    const chunkSize = view.getUint32(offset + 4, true);
    const next = offset + 8 + chunkSize;
    if (next > buf.length) return null; // 还没收全头
    if (chunkId === 'fmt ') {
      if (chunkSize < 16) return null;
      const base = offset + 8;
      const audioFormat = view.getUint16(base, true);
      channels = view.getUint16(base + 2, true);
      sampleRate = view.getUint32(base + 4, true);
      bits = view.getUint16(base + 14, true);
      // 仅支持PCM格式(1)
      if (audioFormat !== 1) {
        console.warn('不支持的WAV编码格式：', audioFormat);
      }
      haveFmt = true;
    } else if (chunkId === 'data') {
      const dataOffset = offset + 8;
      if (!haveFmt) {
        // 有些文件先出现data再fmt是不规范的，仍尝试继续
      }
      return { sampleRate, channels, bitsPerSample: bits, dataOffset };
    }
    // chunk对齐到偶数字节
    offset = next + (chunkSize % 2);
  }
  return null;
}
</script>

<style scoped>

.chat-screen {
  width: 750px;
  height: 700px;
}


.selection-screen {
  text-align: center;
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.selection-screen h1 {
  position: absolute;
  width: 100%;
  left: 0;
  text-align: center;
  top: 50%;
  margin-top: -250px;
  font-size: 3rem;
  font-weight: 300;
  text-shadow: 0 0 15px rgba(0,0,0,0.5);
  margin-bottom: 0;
}

.char-selector {
  display: flex;
  justify-content: center;
  gap: 80px;
}

.char-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.char-card:hover {
  transform: translateY(-10px);
}

.char-avatar {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 5px 20px rgba(0,0,0,0.4);
  margin-bottom: 15px;
  object-fit: cover;
  transition: all 0.3s ease-out;
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
  position: relative;
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

/* 语音识别加载遮罩与动画 */
.transcribe-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-right: 12px;
}

.transcribe-text {
  color: #fff;
  font-size: 1rem;
}

@keyframes spin { to { transform: rotate(360deg); } }

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

.message-row {
  display: flex;
  align-items: flex-start;
  margin: 8px 0;
}
.message-row.left {
  justify-content: flex-start;
}
.message-row.right {
  justify-content: flex-end;
  flex-direction: row;
}
.message-row.right .bubble { order: 1; }
.message-row.right .avatar { order: 2; }
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(0,0,0,0.4);
}
.message-row.left .avatar {
  margin-right: 8px;
}
.message-row.right .avatar {
  margin-left: 8px;
}
.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 14px;
  word-wrap: break-word;
  background: rgba(255, 255, 255, 0.1);
  text-align: left;
}
.message-row.right .bubble {
  background: #007bff;
}

.empty-chat {
  text-align: left;
  color: rgba(255,255,255,0.6);
  margin: auto;
}

.play-audio-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  margin-right: 4px;
  font-size: 12px;
  line-height: 1;
  padding: 0 2px;
}

/* 语音通话弹窗样式 */
.call-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.call-card {
  width: 320px;
  background: rgba(30, 30, 45, 0.6);
  backdrop-filter: blur(15px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 12px 30px rgba(0,0,0,0.3);
  padding: 32px 24px;
  text-align: center;
  color: #f0f0f0;
}

.call-avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  object-fit: cover;
  margin: 0 auto 12px;
  box-shadow: 0 6px 12px rgba(0,0,0,0.2);
}

.call-name {
  font-size: 22px;
  font-weight: 500;
  color: #ffffff;
  margin-top: 16px;
}
.call-status {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

.call-actions {
  display: flex;
  gap: 16px;
  margin-top: 24px;
  justify-content: center;
}

.mute-btn,
.end-call-btn {
  border: none;
  padding: 12px 24px;
  border-radius: 50px;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
  transition: all 0.25s ease-out;
}

.mute-btn {
  background: rgba(255, 255, 255, 0.15);
  color: #f0f0f0;
}

.end-call-btn {
  background: #e63946;
  color: #ffffff;
}

.mute-btn:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-2px);
}

.end-call-btn:hover {
  background: #d62828;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(230, 57, 70, 0.4);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-card {
  width: 320px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(0,0,0,0.25);
  padding: 20px;
  text-align: center;
}
.modal-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}
.modal-content {
  font-size: 14px;
  color: #555;
  margin-bottom: 14px;
}
.modal-ok {
  background: #007bff;
  color: #fff;
  border: none;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
}

.call-btn {
  margin-left: 8px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  color: #096dd9;
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
}

.call-fade-enter-active, .call-fade-leave-active {
  transition: opacity 0.2s ease;
}
.call-fade-enter-from, .call-fade-leave-to {
  opacity: 0;
}

/* Toast 弹窗 */
.toast {
  position: fixed;
  left: 50%;
  bottom: 60px;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 10px 16px;
  border-radius: 8px;
  box-shadow: 0 8px 20px rgba(0,0,0,0.35);
  z-index: 9999;
}
.toast-fade-enter-active, .toast-fade-leave-active { transition: opacity .25s ease, transform .25s ease; }
.toast-fade-enter-from, .toast-fade-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }


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
  text-align: left;
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
  padding: 10px 15px;
  border-radius: 20px;
  border: none;
  cursor: pointer;
  transition: all 0.25s ease;
}
.voice-to-text-btn.recording {
  background: #dc3545;
  color: white;
}

.char-card:hover .char-avatar {
  transform: scale(1.1);

  box-shadow:
      0 0 15px rgba(255, 255, 255, 0.6),
      0 0 30px rgba(180, 220, 255, 0.4),
      0 8px 25px rgba(0,0,0,0.5);
}

.chat-header h3 {
  flex-grow: 1;
  text-align: center;
  margin: 0 10px;
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 123, 255, 0.5);
}

.voice-to-text-btn,
.call-btn {
  transition: all 0.25s ease-out;
}

.voice-to-text-btn:hover:not(.recording),
.call-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.4);
}
</style>