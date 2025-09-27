<template>
  <div class="form-wrapper">
    <div class="mode-switcher">
      <button :class="{ active: mode === 'login' }" @click="setMode('login')">登录</button>
      <button :class="{ active: mode === 'register' }" @click="setMode('register')">注册</button>
    </div>

    <div v-if="mode === 'login'">
      <h1 class="form-title">欢迎回来</h1>
      <div class="form-group">
        <input type="email" v-model="form.email" placeholder="邮箱地址">
      </div>
      <div class="form-group">
        <input type="password" v-model="form.password" placeholder="密码">
      </div>
      <button @click="handleLogin" class="submit-btn">登 录</button>
    </div>

    <div v-if="mode === 'register'">
      <div v-if="registrationStep === 1">
        <h1 class="form-title">创建新账户</h1>
        <div class="form-group">
          <input type="email" v-model="form.email" placeholder="邮箱地址">
        </div>
        <div class="form-group">
          <input type="password" v-model="form.password" placeholder="请输入密码">
        </div>
        <div class="form-group">
          <input type="password" v-model="form.confirmPassword" placeholder="请确认密码">
        </div>
        <div class="form-group captcha-group">
          <input type="text" v-model="form.graphicCaptchaCode" placeholder="图形验证码">
          <div class="captcha-image" @click="getGraphicCaptcha">
            <img v-if="graphicCaptchaImage" :src="graphicCaptchaImage" alt="图形验证码">
            <span v-else>...</span>
          </div>
        </div>
        <button @click="handleRegistrationStep1" class="submit-btn">获取邮箱验证码</button>
      </div>

      <div v-if="registrationStep === 2">
        <h1 class="form-title">验证邮箱</h1>
        <p class="email-notice">验证码已发送至 {{ form.email }}，请查收。</p>
        <div class="form-group captcha-group">
          <input type="text" v-model="form.emailCode" placeholder="请输入邮箱验证码">
          <button @click="resendEmailCode" :disabled="cooldown > 0" class="email-code-btn">
            {{ cooldown > 0 ? `${cooldown}s` : '重新发送' }}
          </button>
        </div>
        <button @click="handleFinalRegistration" class="submit-btn">完 成 注 册</button>
      </div>
    </div>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import axios from 'axios';
import { useRouter } from 'vue-router';
import { authState } from '../store/auth';

// 'login' 或 'register'
const mode = ref('login');
// 注册流程的步骤，1为填写基本信息，2为验证邮箱
const registrationStep = ref(1);

// 使用 reactive 来组织表单数据
const form = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  graphicCaptchaCode: '',
  emailCode: '',
});

const graphicCaptchaId = ref('');
const graphicCaptchaImage = ref('');
const errorMessage = ref('');
const cooldown = ref(0);
let timer: number | null = null;

const router = useRouter();

//通用函数
const getGraphicCaptcha = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/captcha/image'); // TODO: 替换API地址
    if (response.data.success) {
      graphicCaptchaId.value = response.data.data.captchaId;
      graphicCaptchaImage.value = response.data.data.captchaImage;
      errorMessage.value = '';
    }
  } catch (error) {
    errorMessage.value = '获取图形验证码失败';
  }
};

const startCooldown = () => {
  cooldown.value = 60;
  timer = setInterval(() => {
    cooldown.value--;
    if (cooldown.value <= 0 && timer) clearInterval(timer);
  }, 1000);
}

const resetForm = () => {
  Object.assign(form, { email: '', password: '', confirmPassword: '', graphicCaptchaCode: '', emailCode: '' });
  errorMessage.value = '';
  registrationStep.value = 1;
  getGraphicCaptcha();
}

const setMode = (newMode: 'login' | 'register') => {
  mode.value = newMode;
  resetForm();
};

//注册流程
const handleRegistrationStep1 = async () => {
  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致！';
    return;
  }
  if (!form.email || !form.password || !form.graphicCaptchaCode) {
    errorMessage.value = '请填写所有必填项！';
    return;
  }

  try {
    //获取邮箱验证码
    await axios.post('http://localhost:8080/api/captcha/email', { // TODO: 替换API地址
      email: form.email,
      captchaId: graphicCaptchaId.value,
      captchaCode: form.graphicCaptchaCode,
    });

    // 成功后，进入第二步并开始冷却
    registrationStep.value = 2;
    startCooldown();
    errorMessage.value = '';

  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '请求失败，请重试';
    getGraphicCaptcha(); // 失败后刷新图形验证码
  }
};

const resendEmailCode = () => {
  // 重新发送实际上就是再执行一遍第一步的逻辑
  handleRegistrationStep1();
};

const handleFinalRegistration = async () => {
  if (!form.emailCode) {
    errorMessage.value = '请输入邮箱验证码！';
    return;
  }

  try {
    // 这个接口对应最终的注册请求
    await axios.post('http://localhost:8080/api/user/register', { // TODO: 替换API地址
      email: form.email,
      password: form.password,
      captchaCode: form.emailCode,
      loginType: 0, // 密码注册
    });

    alert('注册成功！将自动跳转到登录页面。');
    setMode('login'); // 注册成功后切换到登录模式

  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '注册失败，请检查验证码';
  }
};

//登录流程
const handleLogin = async () => {
  if (!form.email || !form.password) {
    errorMessage.value = '请输入邮箱和密码！';
    return;
  }

  try {
    // 这个接口对应登录请求
    const response = await axios.post('http://localhost:8080/api/user/login', { // TODO: 替换API地址
      email: form.email,
      password: form.password,
      loginType: 0, // 密码登录
    });

    if (response.data.success) {
      const { token, nickname, avatar } = response.data.data;
      authState.login(token, nickname, avatar);
      router.push('/'); // 登录成功，跳转到主页
    } else {
      errorMessage.value = response.data.message || '登录失败';
    }
  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '登录失败，请检查凭据';
  }
};

onMounted(() => {
  getGraphicCaptcha();
});
</script>

<style scoped>
.form-wrapper {
  width: 100%;
  max-width: 420px;
  background: var(--bg-dark-glass);
  backdrop-filter: blur(15px);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  padding: 30px 40px;
  box-sizing: border-box;
  color: var(--text-light);
}

.mode-switcher {
  display: flex;
  justify-content: center;
  margin-bottom: 25px;
  background-color: rgba(0,0,0,0.2);
  border-radius: 10px;
  padding: 5px;
}

.mode-switcher button {
  flex: 1;
  padding: 10px;
  border: none;
  background-color: transparent;
  color: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.3s ease;
}

.mode-switcher button.active {
  background-color: rgba(255, 255, 255, 0.2);
  color: white;
  font-weight: bold;
}

.form-title {
  text-align: center;
  margin-bottom: 25px;
  font-weight: 300;
  font-size: 24px;
}

.form-group {
  margin-bottom: 18px;
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
.captcha-group input {
  flex-grow: 1;
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
  background-color: #fff;
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
  margin-top: 10px;
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

.email-notice {
  text-align: center;
  margin-bottom: 20px;
  color: rgba(255, 255, 255, 0.8);
}
</style>