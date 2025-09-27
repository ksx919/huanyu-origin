<template>
  <div class="profile-container">

    <div class="avatar-section">
      <img :src="authState.avatar || '/default-avatar.png'" alt="User Avatar" class="profile-avatar" @click="triggerAvatarUpload">
      <input type="file" ref="fileInput" @change="handleAvatarChange" style="display: none" accept="image/*">
      <div class="avatar-hint">点击头像更换</div>
    </div>

    <div class="info-section">
      <div class="info-item">
        <div v-if="editingField !== 'nickname'" class="info-display" @click="editField('nickname')">
          <span class="info-text">{{ authState.nickname }}</span>
          <span class="edit-icon">✏️</span>
        </div>
        <div v-else class="edit-group">
          <input type="text" v-model="newNickname" class="edit-input" ref="nicknameInput" @keyup.enter="saveNickname">
          <button @click="saveNickname" class="confirm-btn">确认</button>
        </div>
      </div>

      <div class="info-item">
        <div class="info-display disabled">
          <span class="info-text">{{ authState.email }}</span>
        </div>
      </div>

      <div class="info-item">
        <button class="change-password-link">修改密码</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { authState } from '../store/auth';
import http from '../utils/axios';

// 2. 新增状态，用于控制哪个字段正在被编辑
const editingField = ref<'none' | 'nickname' | 'email'>('none');

// 表单数据
const newNickname = ref('');
// const newEmail = ref(''); // 如果邮箱可修改，则取消注释

// 用于自动聚焦输入框
const nicknameInput = ref<HTMLInputElement | null>(null);

// 用于触发隐藏的文件上传框
const fileInput = ref<HTMLInputElement | null>(null);

onMounted(() => {
  // 组件加载时，用当前用户信息初始化
  newNickname.value = authState.nickname || '';
});

// 3. 进入编辑模式
const editField = async (field: 'nickname' | 'email') => {
  editingField.value = field;
  // 使用 nextTick 确保 input 元素已被渲染，然后自动聚焦
  await nextTick();
  if (field === 'nickname' && nicknameInput.value) {
    nicknameInput.value.focus();
  }
};

// 4. 保存昵称
const saveNickname = async () => {
  if (!newNickname.value.trim() || newNickname.value === authState.nickname) {
    editingField.value = 'none'; // 如果没变化，直接退出编辑模式
    return;
  }
  try {
    const response = await http.post('/user/profile/update', {
      nickname: newNickname.value,
    });
    if (response.data.success) {
      // 更新 store 和 localStorage
      authState.updateProfile(newNickname.value, null);
    }
  } catch (error) {
    console.error("更新昵称失败:", error);
    newNickname.value = authState.nickname || ''; // 失败时恢复原值
  } finally {
    editingField.value = 'none'; // 无论成功失败，都退出编辑模式
  }
};

// 5. 头像上传逻辑
const triggerAvatarUpload = () => {
  fileInput.value?.click();
};

const handleAvatarChange = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (!target.files || target.files.length === 0) return;

  const file = target.files[0];
  const formData = new FormData();
  formData.append('avatarFile', file); // 'avatarFile' 是和后端约定的字段名

  try {
    const response = await http.post('/user/avatar/upload', formData, { // TODO: 和后端确认API地址
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    if (response.data.success) {
      const newAvatarUrl = response.data.data.avatarUrl;
      // 更新 store 和 localStorage
      authState.updateProfile(null, newAvatarUrl);
    }
  } catch (error) {
    console.error("上传头像失败:", error);
    alert('头像上传失败！');
  }
};
</script>

<style scoped>
/* 6. 全新的样式，实现你的设计 */
.profile-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 50px;
  width: 100%;
  max-width: 600px;
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
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 10px;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 25px;
}

.info-item {
  height: 45px; /* 固定高度防止切换时跳动 */
}

.info-display {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 10px 5px;
}

.info-display.disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.info-text {
  font-size: 22px;
  color: white;
}

.edit-icon {
  margin-left: 15px;
  opacity: 0;
  transition: opacity 0.3s;
}

.info-display:hover .edit-icon {
  opacity: 1;
}

.edit-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.edit-input {
  width: 200px;
  height: 45px;
  padding: 0 15px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background-color: rgba(0, 0, 0, 0.2);
  color: white;
  font-size: 18px;
  box-sizing: border-box;
}

.confirm-btn {
  height: 45px;
  padding: 0 15px;
  border-radius: 8px;
  border: none;
  background-color: #007bff;
  color: white;
  cursor: pointer;
}

.change-password-link {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  font-size: 16px;
  text-decoration: underline;
}
</style>