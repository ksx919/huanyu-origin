<template>
  <div class="form-wrapper">
    <div v-if="!isChangePasswordVisible">
      <h1 class="form-title">个人中心</h1>

      <div class="avatar-container">
        <img :src="authState.avatar || '/default-avatar.png'" alt="User Avatar" class="avatar">
      </div>

      <div class="form-group">
        <label for="nickname">昵称</label>
        <input type="text" id="nickname" v-model="newNickname">
      </div>

      <div class="form-group">
        <label for="email">邮箱</label>
        <input type="email" id="email" v-model="newEmail">
      </div>

      <button @click="handleUpdateProfile" class="submit-btn">保存更改</button>
      <button @click="toggleChangePasswordView" class="secondary-btn">修改密码</button>
    </div>

    <div v-else>
      <h1 class="form-title">修改密码</h1>

      <div class="form-group">
        <label for="new-password">新密码</label>
        <input type="password" id="new-password" v-model="newPassword" placeholder="请输入新密码">
      </div>

      <div class="form-group">
        <label for="confirm-password">确认新密码</label>
        <input type="password" id="confirm-password" v-model="confirmPassword" placeholder="请再次输入新密码">
      </div>

      <button @click="handleChangePassword" class="submit-btn">确认修改</button>
      <button @click="toggleChangePasswordView" class="secondary-btn">取消</button>
    </div>

    <p v-if="message" :class="isError ? 'error-message' : 'success-message'">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { authState } from '../store/auth';
import http from '../utils/axios';

// 控制显示哪个视图
const isChangePasswordVisible = ref(false);

// 表单数据
const newNickname = ref('');
const newEmail = ref('');
const newPassword = ref('');
const confirmPassword = ref('');

// 消息提示
const message = ref('');
const isError = ref(false);

// 组件加载时，用 store 中的当前用户信息填充表单
onMounted(() => {
  newNickname.value = authState.nickname || '';
  // 假设后端不允许修改邮箱，可以禁用输入框
  // newEmail.value = authState.email || '';
});

// 切换视图
const toggleChangePasswordView = () => {
  isChangePasswordVisible.value = !isChangePasswordVisible.value;
  message.value = ''; // 切换时清空消息
};

// 更新昵称和邮箱
const handleUpdateProfile = async () => {
  message.value = '';
  try {
    const response = await http.post('/user/profile/update', { // TODO: 和后端确认API地址
      nickname: newNickname.value,
      // email: newEmail.value, // 如果支持修改邮箱
    });

    if (response.data.success) {
      isError.value = false;
      message.value = '个人信息更新成功！';
      // 更新成功后，同步更新 store 中的状态
      authState.updateProfile(newNickname.value, null); // 假设只更新昵称
    } else {
      isError.value = true;
      message.value = response.data.message || '更新失败';
    }
  } catch (error: any) {
    isError.value = true;
    message.value = error.response?.data?.message || '请求失败';
  }
};

// 修改密码
const handleChangePassword = async () => {
  message.value = '';
  if (newPassword.value !== confirmPassword.value) {
    isError.value = true;
    message.value = '两次输入的密码不一致！';
    return;
  }
  if (!newPassword.value) {
    isError.value = true;
    message.value = '密码不能为空！';
    return;
  }

  try {
    const response = await http.post('/user/password/update', { // TODO: 和后端确认API地址
      newPassword: newPassword.value,
    });

    if (response.data.success) {
      isError.value = false;
      message.value = '密码修改成功！';
      // 密码修改成功后，清空输入框并切回主视图
      newPassword.value = '';
      confirmPassword.value = '';
      setTimeout(() => {
        toggleChangePasswordView();
      }, 1500);
    } else {
      isError.value = true;
      message.value = response.data.message || '修改失败';
    }
  } catch (error: any) {
    isError.value = true;
    message.value = error.response?.data?.message || '请求失败';
  }
};
</script>

<style scoped>
/* 复用登录页的样式，并添加新样式 */
.form-wrapper {
  width: 100%;
  max-width: 500px; /* 可以比登录页稍宽一些 */
  background: var(--bg-dark-glass);
  backdrop-filter: blur(15px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  padding: 40px;
  box-sizing: border-box;
  color: var(--text-light);
}

.form-title {
  text-align: center;
  margin-bottom: 30px;
  font-weight: 300;
  font-size: 24px;
}

.avatar-container {
  text-align: center;
  margin-bottom: 30px;
}

.avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.5);
  object-fit: cover;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: rgba(255, 255, 255, 0.8);
}

input {
  width: 100%;
  height: 45px;
  padding: 0 15px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(0, 0, 0, 0.2);
  color: var(--text-light);
  font-size: 16px;
  box-sizing: border-box;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 8px;
  background-color: #007bff;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin-top: 10px;
}

.secondary-btn {
  width: 100%;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  background-color: transparent;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin-top: 15px;
}

.submit-btn:hover {
  background-color: #0056b3;
}
.secondary-btn:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.error-message, .success-message {
  text-align: center;
  margin-top: 15px;
  height: 20px;
}
.error-message {
  color: #ff6b6b;
}
.success-message {
  color: #28a745;
}
</style>