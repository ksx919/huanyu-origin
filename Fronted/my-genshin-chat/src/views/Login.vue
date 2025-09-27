<template>
  <div class="form-wrapper">
    <h1>登录 / 注册</h1>

    <div class="form-group">
      <input type="email" v-model="email" placeholder="邮箱地址">
    </div>

    <div class="form-group captcha-group">
      <input type="text" v-model="graphicCaptchaCode" placeholder="图形验证码">
      <div class="captcha-image" @click="getGraphicCaptcha">
        <img v-if="graphicCaptchaImage" :src="graphicCaptchaImage" alt="图形验证码">
        <span v-else>加载中...</span>
      </div>
    </div>

    <div class="form-group captcha-group">
      <input type="text" v-model="emailCode" placeholder="邮箱验证码">
      <button @click="sendEmailCode" :disabled="cooldown > 0" class="email-code-btn">
        {{ cooldown > 0 ? `${cooldown}s` : '获取' }}
      </button>
    </div>

    <div class="form-group">
      <input type="password" v-model="password" placeholder="密码">
    </div>

    <button @click="handleSubmit" class="submit-btn">进入对话</button>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
// <script> 部分的代码和上次一样，这里保持不变
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';
import { authState } from '../store/auth';

const email = ref('');
const password = ref('');
const graphicCaptchaId = ref('');
const graphicCaptchaCode = ref('');
const graphicCaptchaImage = ref('');
const emailCode = ref('');
const errorMessage = ref('');
const cooldown = ref(0);
let timer: number | null = null;
const router = useRouter();

const getGraphicCaptcha = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/captcha/image'); // TODO: 替换成你的API地址
    if (response.data.success) {
      graphicCaptchaId.value = response.data.data.captchaId;
      graphicCaptchaImage.value = response.data.data.captchaImage;
    }
  } catch (error) {
    errorMessage.value = '获取图形验证码失败';
  }
};

const sendEmailCode = async () => {
  if (cooldown.value > 0 || !email.value || !graphicCaptchaCode.value) {
    if (!email.value || !graphicCaptchaCode.value) alert('请先填写邮箱和图形验证码！');
    return;
  }

  cooldown.value = 60;
  timer = setInterval(() => {
    cooldown.value--;
    if (cooldown.value <= 0 && timer) clearInterval(timer);
  }, 1000);

  try {
    await axios.post('http://localhost:8080/api/captcha/email', { // TODO: 替换成你的API地址
      email: email.value,
      captchaId: graphicCaptchaId.value,
      captchaCode: graphicCaptchaCode.value,
    });
    alert('验证码发送成功！');
  } catch (error) {
    console.error('发送邮箱验证码失败:', error);
  }
};

const handleSubmit = async () => {
  if (!email.value || !password.value || !emailCode.value) {
    errorMessage.value = '请填写所有字段！';
    return;
  }

  try {
    const response = await axios.post('http://localhost:8080/api/user/loginOrRegister', { // TODO: 替换成你的API地址
      email: email.value,
      password: password.value,
      captchaCode: emailCode.value,
      loginType: 0,
    });

    if (response.data.success) {
      const { token, nickname, avatar } = response.data.data;
      authState.login(token, nickname, avatar);
      router.push('/');
    } else {
      errorMessage.value = response.data.message || '登录或注册失败';
      await getGraphicCaptcha(); // 登录失败后刷新验证码
    }
  } catch (error) {
    errorMessage.value = '请求失败，请检查网络';
    await getGraphicCaptcha();
  }
};

onMounted(() => {
  getGraphicCaptcha();
});
</script>

<style scoped>
/* 使用与聊天窗口类似的样式 */
.form-wrapper {
  width: 100%;
  max-width: 400px;
  background: var(--bg-dark-glass);
  backdrop-filter: blur(15px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  padding: 40px;
  box-sizing: border-box;
  color: var(--text-light);
}

h1 {
  text-align: center;
  margin-bottom: 30px;
  font-weight: 300;
}

.form-group {
  margin-bottom: 20px;
}

input {
  width: 100%;
  padding: 12px 15px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(0, 0, 0, 0.2);
  color: var(--text-light);
  font-size: 16px;
}
input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.captcha-group {
  display: flex;
  gap: 10px;
}

.captcha-image {
  height: 45px;
  width: 120px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.captcha-image img {
  height: 100%;
  width: 100%;
  border-radius: 8px;
}

.email-code-btn {
  padding: 10px 15px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  background-color: rgba(0, 0, 0, 0.2);
  color: var(--text-light);
  cursor: pointer;
  white-space: nowrap;
}
.email-code-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
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
}
.submit-btn:hover {
  background-color: #0056b3;
}

.error-message {
  color: #ff6b6b;
  text-align: center;
  margin-top: 15px;
  height: 20px;
}
</style>