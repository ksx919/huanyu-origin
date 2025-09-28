<template>
  <div class="profile-page">
    <div class="profile-card">
      <div class="avatar-section">
        <img :src="displayAvatarUrl" alt="User Avatar" class="profile-avatar" @click="triggerAvatarUpload">
        <input type="file" ref="fileInput" @change="handleAvatarChange" style="display: none" accept="image/*">
        <div class="avatar-hint">点击头像更换（支持 JPG/PNG ≤10MB）</div>
      </div>

      <div class="info-section">
        <div class="info-item">
          <label class="info-label">昵称</label>
          <div v-if="editingField !== 'nickname'" class="info-display" @click="editField('nickname')">
            <span class="info-text">{{ authState.nickname }}</span>
            <span class="edit-icon">✏️</span>
          </div>
          <div v-else class="edit-group">
            <input type="text" v-model="newNickname" class="edit-input" ref="nicknameInput" @keyup.enter="saveNickname" placeholder="输入新昵称">
            <button @click="saveNickname" class="confirm-btn" :disabled="saving">保存</button>
          </div>
        </div>

        <div class="info-item">
          <label class="info-label">邮箱</label>
          <div class="info-display disabled">
            <span class="info-text">{{ authState.email }}</span>
          </div>
        </div>

        <div class="password-actions-container">
          <transition name="form-expand" mode="out-in">
            <div v-if="!showPasswordForm" key="change-password-link" class="info-display">
              <button class="change-password-link" @click="showPasswordForm = true">修改密码</button>
            </div>
            <div v-else key="password-form" class="password-form">
              <input type="password" v-model="oldPassword" class="edit-input" placeholder="当前密码">
              <input type="password" v-model="newPassword" class="edit-input" placeholder="新密码">
              <input type="password" v-model="confirmPassword" class="edit-input" placeholder="确认新密码" @keyup.enter="savePassword">
              <div class="password-actions">
                <button class="confirm-btn" @click="savePassword" :disabled="saving">确认修改</button>
                <button class="cancel-btn" @click="resetPasswordForm" :disabled="saving">取消</button>
              </div>
            </div>
          </transition>
        </div>
      </div>

      <div v-if="message" class="message-banner" :class="{ error: messageType==='error', success: messageType==='success' }">
        {{ message }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue';
import { authState } from '../store/auth';
import http from '../utils/axios';

// 编辑状态
const editingField = ref<'none' | 'nickname'>('none');
const saving = ref(false);

// 昵称表单
const newNickname = ref('');
const nicknameInput = ref<HTMLInputElement | null>(null);

// 密码表单
const showPasswordForm = ref(false);
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');

// 头像上传
const fileInput = ref<HTMLInputElement | null>(null);
const userAvatarUrl = ref<string | null>(null); // 私有链接
const displayAvatarUrl = computed(() => userAvatarUrl.value || authState.avatar || '/default-avatar.png');

// 消息提示
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
let messageTimer: number | null = null;

const showMessage = (text: string, type: 'success' | 'error' = 'success') => {
  message.value = text;
  messageType.value = type;
  if (messageTimer) window.clearTimeout(messageTimer);
  messageTimer = window.setTimeout(() => { message.value = ''; }, 3000);
};

// 更新本地token
const rotateTokenIfPresent = (token?: string | null) => {
  if (token) {
    authState.token = token;
    localStorage.setItem('token', token);
  }
};

// 获取用户头像私有链接（默认10分钟）
const fetchUserAvatarPrivateUrl = async (expires: number = 600) => {
  try {
    const resp = await http.get('/file/private-url', { params: { expires } });
    if (resp.data && resp.data.success) {
      const url = resp.data.data;
      if (typeof url === 'string' && url) {
        userAvatarUrl.value = url;
      }
    }
  } catch (e) {
    console.warn('获取头像私链失败，将回退使用头像存储路径', e);
    userAvatarUrl.value = null;
  }
};

onMounted(() => {
  newNickname.value = authState.nickname || '';
  fetchUserAvatarPrivateUrl();
});

// 进入编辑昵称
const editField = async (field: 'nickname') => {
  editingField.value = field;
  await nextTick();
  if (field === 'nickname' && nicknameInput.value) {
    nicknameInput.value.focus();
  }
};

// 保存昵称（POST /user/update/nickname，@RequestParam nickname）
const saveNickname = async () => {
  if (!newNickname.value.trim() || newNickname.value === authState.nickname) {
    editingField.value = 'none';
    return;
  }
  saving.value = true;
  try {
    const resp = await http.post('/user/update/nickname', null, {
      params: { nickname: newNickname.value }
    });
    if (resp.data && resp.data.success) {
      const token = resp.data.data?.token;
      rotateTokenIfPresent(token);
      authState.updateProfile(newNickname.value, null);
      showMessage('昵称已更新');
    } else {
      showMessage(resp.data?.message || '更新昵称失败', 'error');
    }
  } catch (e) {
    console.error('更新昵称失败:', e);
    showMessage('更新昵称失败', 'error');
    newNickname.value = authState.nickname || '';
  } finally {
    saving.value = false;
    editingField.value = 'none';
  }
};

// 打开文件选择框
const triggerAvatarUpload = () => {
  fileInput.value?.click();
};

// 上传头像并刷新私链（POST /file/upload/avatar，form-data: file）
const handleAvatarChange = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (!target.files || target.files.length === 0) return;
  const file = target.files[0];
  const formData = new FormData();
  formData.append('file', file);
  saving.value = true;
  try {
    const resp = await http.post('/file/upload/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    if (resp.data && resp.data.success) {
      const newAvatarStoragePath = resp.data.data?.avatarUrl;
      const token = resp.data.data?.token;
      rotateTokenIfPresent(token);
      authState.updateProfile(null, newAvatarStoragePath || null);
      await fetchUserAvatarPrivateUrl();
      showMessage('头像已更新');
    } else {
      showMessage(resp.data?.message || '上传头像失败', 'error');
    }
  } catch (e) {
    console.error('上传头像失败:', e);
    showMessage('上传头像失败', 'error');
  } finally {
    saving.value = false;
    // 清空文件选择
    if (fileInput.value) fileInput.value.value = '' as any;
  }
};

// 保存密码（POST /user/update/password，JSON {oldPassword,newPassword}）
const savePassword = async () => {
  if (!oldPassword.value || !newPassword.value) {
    showMessage('请输入当前密码和新密码', 'error');
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    showMessage('两次输入的新密码不一致', 'error');
    return;
  }
  saving.value = true;
  try {
    const resp = await http.post('/user/update/password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    });
    if (resp.data && resp.data.success) {
      const token = resp.data.data?.token;
      rotateTokenIfPresent(token);
      showMessage('密码已更新，请妥善保管');
      resetPasswordForm();
    } else {
      showMessage(resp.data?.message || '更新密码失败', 'error');
    }
  } catch (e) {
    console.error('更新密码失败:', e);
    showMessage('更新密码失败', 'error');
  } finally {
    saving.value = false;
  }
};

const resetPasswordForm = () => {
  showPasswordForm.value = false;
  oldPassword.value = '';
  newPassword.value = '';
  confirmPassword.value = '';
};
</script>

<style scoped>
.profile-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.profile-card {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 30px;
  width: 100%;
  max-width: 500px;
  padding: 24px;
  border-radius: 16px;
  background: var(--bg-dark-glass);
  backdrop-filter: blur(6px);
  align-items: center;
}

.avatar-section {
  text-align: center;
}

.profile-avatar {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.5);
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
}
.profile-avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 0 25px rgba(255, 255, 255, 0.3);
}

.avatar-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 8px;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.info-display {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 10px;
  background-color: rgba(255, 255, 255, 0.06);
}

.info-display.disabled {
  cursor: default;
}

.info-text {
  font-size: 18px;
  color: white;
}

.edit-icon {
  margin-left: 15px;
  opacity: 0.6;
}

.edit-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.edit-input {
  flex: 1;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(255, 255, 255, 0.08);
  color: white;
  font-size: 16px;
  box-sizing: border-box;
}

.password-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.password-actions {
  grid-column: span 2;
  display: flex;
  gap: 10px;
}

.confirm-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  border: none;
  background-color: #007bff;
  color: white;
  cursor: pointer;
}
.confirm-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.cancel-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(255, 255, 255, 0.08);
  color: white;
  cursor: pointer;
}

.change-password-link {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  font-size: 14px;
  text-decoration: underline;
}

.message-banner {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 14px;
}
.message-banner.success {
  background: rgba(0, 128, 0, 0.2);
  color: #b0ffb0;
}
.message-banner.error {
  background: rgba(128, 0, 0, 0.2);
  color: #ffb0b0;
}
.password-actions-container {
  margin-top: auto;
  align-self: flex-end;
}

.password-actions-container .password-form {
  align-items: flex-end;
}

.edit-input::placeholder {
  color: white;
  opacity: 1;
}

.form-expand-enter-active,
.form-expand-leave-active {
  transition: all 0.4s ease-in-out;
  overflow: hidden;
}

.form-expand-enter-from,
.form-expand-leave-to {
  max-height: 0;
  opacity: 0;
  margin-top: 0;
  margin-bottom: 0;
  padding-top: 0;
  padding-bottom: 0;
  transform: translateY(-10px);
}

.form-expand-enter-to,
.form-expand-leave-from {
  max-height: 300px;
  opacity: 1;
  transform: translateY(0);
}


.confirm-btn,
.cancel-btn,
.change-password-link {
  transition: all 0.25s ease;
}


.confirm-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 123, 255, 0.5);
}

.cancel-btn:hover:not(:disabled) {
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.7);
}

.change-password-link:hover {
  color: #38b6ff;
  text-shadow: 0 0 8px rgba(56, 182, 255, 0.8);
}
</style>