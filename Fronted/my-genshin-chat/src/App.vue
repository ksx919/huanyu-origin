<template>
  <div class="container">
    <h1>原神 AI 角色扮演</h1>

    <div class="char-selector">
      <p>当前角色: {{ selectedCharacter || '未选择' }}</p>
      <button
          @click="selectCharacter('Hutao')"
          :class="{ active: selectedCharacter === 'Hutao' }"
      >
        胡桃
      </button>
      <button
          @click="selectCharacter('Venti')"
          :class="{ active: selectedCharacter === 'Venti' }"
      >
        温迪
      </button>
      <button
          @click="selectCharacter('Xiaogong')"
          :class="{ active: selectedCharacter === 'Xiaogong' }"
      >
        宵宫
      </button>
    </div>

    <div class="connection-status">
      连接状态: <span :class="connectionStatus">{{ connectionStatusText }}</span>
    </div>

    <div class="chat-window">
      <div v-for="(msg, index) in conversation" :key="index" :class="msg.role">
        <strong>{{ msg.role === 'user' ? '你' : getCharacterName(selectedCharacter) }}:</strong>
        {{ msg.content }}
      </div>
      <div v-if="conversation.length === 0" class="empty-chat">
        选择角色后开始对话吧！
      </div>
    </div>

    <div class="controls">
      <button
          class="record-btn"
          @mousedown="startRecording"
          @mouseup="stopRecording"
          @mouseleave="stopRecording"
          :disabled="!selectedCharacter || connectionStatus !== 'connected'"
          :class="{ recording: isRecording }"
      >
        {{ getRecordButtonText() }}
      </button>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';

// 类型定义
interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
  timestamp?: number;
}

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

// 计算属性和辅助函数
const connectionStatusText = computed(() => {
  const statusMap = {
    'disconnected': '未连接',
    'connecting': '连接中...',
    'connected': '已连接',
    'error': '连接错误'
  };
  return statusMap[connectionStatus.value];
});

const getCharacterName = (char: string | null) => {
  const nameMap: { [key: string]: string } = {
    'Hutao': '胡桃',
    'Venti': '温迪',
    'Xiaogong': '宵宫'
  };
  return char ? nameMap[char] : '未知角色';
};

const getRecordButtonText = () => {
  if (!selectedCharacter.value) return '请先选择角色';
  if (connectionStatus.value !== 'connected') return '等待连接...';
  if (isRecording.value) return '正在录音... (松开停止)';
  return '按住说话';
};

// 角色选择
const selectCharacter = (char: string) => {
  selectedCharacter.value = char;
  conversation.value = [];
  console.log(`已选择角色: ${char}`);

  // 选择角色后自动连接WebSocket
  if (connectionStatus.value !== 'connected') {
    connectWebSocket();
  }
};

// WebSocket 连接管理
const connectWebSocket = () => {
  if (socket && socket.readyState === WebSocket.OPEN) {
    return; // 已经连接，不需要重复连接
  }

  connectionStatus.value = 'connecting';
  const backendUrl = 'ws://localhost:8000/ws-audio';

  try {
    socket = new WebSocket(backendUrl);
    socket.binaryType = "arraybuffer"; // 重要：接收和发送二进制数据

    socket.onopen = () => {
      console.log('WebSocket 连接已建立');
      connectionStatus.value = 'connected';
    };

    socket.onmessage = (event) => {
      console.log('收到后端回复:', event.data);

      // 更新最后一条用户消息
      if (conversation.value.length > 0 &&
          conversation.value[conversation.value.length - 1].role === 'user' &&
          conversation.value[conversation.value.length - 1].content.includes('等待识别')) {
        conversation.value[conversation.value.length - 1].content = '（语音输入已识别）';
      }

      // 添加AI回复
      conversation.value.push({
        role: 'ai',
        content: event.data,
        timestamp: Date.now()
      });
    };

    socket.onerror = (error) => {
      console.error('WebSocket 错误:', error);
      connectionStatus.value = 'error';
    };

    socket.onclose = (event) => {
      console.log('WebSocket 连接已关闭', event.code, event.reason);
      connectionStatus.value = 'disconnected';

      // 如果不是主动关闭，尝试重连
      if (event.code !== 1000 && selectedCharacter.value) {
        setTimeout(() => {
          console.log('尝试重新连接...');
          connectWebSocket();
        }, 3000);
      }
    };

  } catch (error) {
    console.error('WebSocket 连接失败:', error);
    connectionStatus.value = 'error';
  }
};

// 录音功能
const startRecording = async () => {
  if (!selectedCharacter.value) {
    alert('请先选择一个角色！');
    return;
  }

  if (connectionStatus.value !== 'connected') {
    alert('WebSocket未连接，请等待连接建立');
    return;
  }

  if (isRecording.value) return;

  try {
    // 1. 获取麦克风权限
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioContext = new AudioContext();

    // 2. 加载AudioWorklet模块
    await audioContext.audioWorklet.addModule('/audio-processor.js');

    // 3. 创建音频处理图
    const source = audioContext.createMediaStreamSource(mediaStream);
    audioWorkletNode = new AudioWorkletNode(audioContext, 'audio-processor');

    // 4. 连接音频节点
    source.connect(audioWorkletNode);
    audioWorkletNode.connect(audioContext.destination);

    // 先发送角色信息
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({
        type: 'character_select',
        characterId: selectedCharacter.value
      }));
    }

    // 5. 监听AudioWorklet发送的音频数据
    audioWorkletNode.port.onmessage = (event) => {
      if (event.data.type === 'audioData' && socket && socket.readyState === WebSocket.OPEN) {
        socket.send(event.data.data); // 发送二进制 PCM 数据
      }
    };

    isRecording.value = true;
    console.log('录音器已打开，开始录音...');

    // 添加用户消息到对话记录
    conversation.value.push({
      role: 'user',
      content: '（语音输入，等待识别...）',
      timestamp: Date.now()
    });

  } catch (error) {
    console.error('录音器打开失败:', error);
    alert('无法访问麦克风，请检查权限设置');
  }
};

const stopRecording = () => {
  if (!isRecording.value) return;

  try {
    // 停止音频处理
    if (audioWorkletNode) {
      audioWorkletNode.disconnect();
      audioWorkletNode = null;
    }

    // 关闭音频上下文
    if (audioContext) {
      audioContext.close();
      audioContext = null;
    }

    // 停止媒体流
    if (mediaStream) {
      mediaStream.getTracks().forEach(track => track.stop());
      mediaStream = null;
    }

    isRecording.value = false;
    console.log('录音已停止');

  } catch (error) {
    console.error('停止录音时出错:', error);
    isRecording.value = false;
  }
};

// 生命周期管理
onMounted(() => {
  console.log('原神AI聊天应用已加载');
});

onUnmounted(() => {
  // 清理资源
  if (socket) {
    socket.close(1000, '用户离开页面');
  }

  // 停止录音相关资源
  if (audioWorkletNode) {
    audioWorkletNode.disconnect();
  }
  if (audioContext) {
    audioContext.close();
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop());
  }
});
</script>

<style scoped>
.container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  text-align: center;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

h1 {
  color: #333;
  margin-bottom: 30px;
}

.char-selector {
  margin-bottom: 20px;
}

.char-selector p {
  font-size: 18px;
  margin-bottom: 15px;
  color: #555;
}

.char-selector button {
  margin: 0 10px;
  padding: 10px 20px;
  font-size: 16px;
  border: 2px solid #ddd;
  background: #f9f9f9;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.char-selector button:hover {
  background: #e9e9e9;
  border-color: #ccc;
}

.char-selector button.active {
  background: #007bff;
  color: white;
  border-color: #007bff;
}

.connection-status {
  margin-bottom: 20px;
  font-size: 14px;
}

.connection-status .connected {
  color: #28a745;
}

.connection-status .connecting {
  color: #ffc107;
}

.connection-status .disconnected,
.connection-status .error {
  color: #dc3545;
}

.chat-window {
  border: 2px solid #e9ecef;
  border-radius: 10px;
  height: 400px;
  overflow-y: auto;
  margin: 20px 0;
  padding: 15px;
  text-align: left;
  background: #f8f9fa;
}

.chat-window .empty-chat {
  text-align: center;
  color: #6c757d;
  font-style: italic;
  margin-top: 150px;
}

.chat-window .user {
  color: #007bff;
  margin-bottom: 10px;
  padding: 8px;
  background: #e3f2fd;
  border-radius: 8px;
  border-left: 4px solid #007bff;
}

.chat-window .ai {
  color: #28a745;
  margin-bottom: 10px;
  padding: 8px;
  background: #e8f5e8;
  border-radius: 8px;
  border-left: 4px solid #28a745;
}

.controls {
  margin-top: 20px;
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
  0% {
    box-shadow: 0 0 0 0 rgba(220, 53, 69, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(220, 53, 69, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(220, 53, 69, 0);
  }
}

/* 响应式设计 */
@media (max-width: 600px) {
  .container {
    padding: 10px;
  }

  .char-selector button {
    margin: 5px;
    padding: 8px 16px;
    font-size: 14px;
  }

  .chat-window {
    height: 300px;
  }

  .record-btn {
    padding: 12px 24px;
    font-size: 16px;
    min-width: 180px;
  }
}
</style>