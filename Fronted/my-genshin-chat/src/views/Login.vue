<template>
  <div class="form-wrapper">
    <div class="dev-actions">
      <button @click="fakeLogin" class="dev-btn">【开发用】一键登录</button>
    </div>
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
  <button @click="handleLogin" class="submit-btn" :disabled="isLoggingIn">{{ isLoggingIn ? '登录中...' : '登 录' }}</button>
</div>

    <div v-if="mode === 'register'">
      <h1 class="form-title">创建新账户</h1>
      <div class="form-group">
        <input type="email" v-model="form.email" placeholder="邮箱地址">
      </div>
      <div class="form-group">
        <input type="text" v-model="form.nickname" placeholder="昵称">
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
      <div class="form-group captcha-group">
        <input type="text" v-model="form.emailCode" placeholder="请输入邮箱验证码">
        <button @click="handleRegistrationStep1" :disabled="cooldown > 0" class="email-code-btn">
          {{ cooldown > 0 ? `${cooldown}s` : (emailCodeSent ? '重新发送' : '发送验证码') }}
        </button>
      </div>
  <button @click="handleFinalRegistration" class="submit-btn" :disabled="isRegistering">{{ isRegistering ? '注册中...' : '注 册' }}</button>
    </div>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { authState } from '../store/auth';
import http from '../utils/axios';

// 'login' 或 'register'
const mode = ref('login');
// 注册流程的步骤，1为填写基本信息，2为验证邮箱
const registrationStep = ref(1);

// 使用 reactive 来组织表单数据
const form = reactive({
  email: '',
  nickname: '',
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
const emailCodeSent = ref(false);
const isLoggingIn = ref(false);
const isRegistering = ref(false);

const router = useRouter();

const fakeLogin = () => {
  console.log("执行开发者模拟登录...");
  // 模拟从后端获取的数据
  const fakeToken = 'dev-fake-token-1234567890';
  const fakeNickname = '开发者';
  const fakeAvatar = null; // 或者一个图片URL

  // 手动调用我们 store 里的 login 方法
  authState.login(fakeToken, fakeNickname, fakeAvatar);

  // 手动跳转到主页
  router.push('/');
};

//通用函数
const getGraphicCaptcha = async () => {
  try {
    // 若已有旧验证码，先通知后端删除
    if (graphicCaptchaId.value) {
      try {
        await http.post('/user/captcha/delete', { captchaId: graphicCaptchaId.value });
      } catch (e) {
        // 删除失败不影响重新获取，忽略错误
      }
    }
    const response = await http.get('/user/captcha');
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
  Object.assign(form, { email: '', nickname: '', password: '', confirmPassword: '', graphicCaptchaCode: '', emailCode: '' });
  errorMessage.value = '';
  registrationStep.value = 1;
  getGraphicCaptcha();
  emailCodeSent.value = false;
}

const setMode = (newMode: 'login' | 'register') => {
  mode.value = newMode;
  resetForm();
};

//注册流程
const handleRegistrationStep1 = async () => {
  // 发送邮箱验证码，仅校验邮箱与图形验证码
  if (!form.email || !form.graphicCaptchaCode || !graphicCaptchaId.value) {
    errorMessage.value = '请先填写邮箱与图形验证码！';
    return;
  }

  try {
    //获取邮箱验证码
    const response = await http.post('/user/email-code', {
      email: form.email,
      captchaId: graphicCaptchaId.value,
      captchaCode: form.graphicCaptchaCode,
    });
    // 根据后端响应判断是否发送成功
    if (response.data?.success) {
      startCooldown();
      emailCodeSent.value = true;
      errorMessage.value = '';
    } else {
      errorMessage.value = response.data?.message || '发送验证码失败，请重试';
      getGraphicCaptcha(); // 失败后刷新图形验证码
    }

  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '请求失败，请重试';
    getGraphicCaptcha(); // 失败后刷新图形验证码
  }
};

const resendEmailCode = () => {
  handleRegistrationStep1();
};

const handleFinalRegistration = async () => {
  // 注册时校验所有必填项非空与密码一致性
  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致！';
    return;
  }
  if (!form.email || !form.nickname || !form.password || !form.confirmPassword || !form.emailCode) {
    errorMessage.value = '请完整填写所有注册信息！';
    return;
  }
  isRegistering.value = true;
  try {
    // 这个接口对应最终的注册请求
    const response = await http.post('/user/register', {
      email: form.email,
      password: form.password,
      emailCode: form.emailCode,
      nickname: form.nickname,
    });

    if (response.data?.success) {
      alert('注册成功！将自动跳转到登录页面。');
      setMode('login'); // 注册成功后切换到登录模式
    } else {
      errorMessage.value = response.data?.message || '注册失败，请检查验证码';
    }
  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '注册失败，请检查验证码';
  } finally {
    isRegistering.value = false;
  }
};

//登录流程
const handleLogin = async () => {
  if (!form.email || !form.password) {
    errorMessage.value = '请输入邮箱和密码！';
    return;
  }
  isLoggingIn.value = true;
  try {
    // 这个接口对应登录请求
    const response = await http.post('/user/login', {
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
  } finally {
    isLoggingIn.value = false;
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
  height: 45px;
  padding: 0 15px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(0, 0, 0, 0.2);
  color: var(--text-light);
  font-size: 16px;
  box-sizing: border-box;
}
input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.captcha-group {
  display: grid;
  grid-template-columns: 1fr 120px;
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
  width: 120px;
  height: 45px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  background-color: rgba(0, 0, 0, 0.2);
  color: var(--text-light);
  cursor: pointer;
  white-space: nowrap;
  display: flex;
  justify-content: center;
  align-items: center;
  box-sizing: border-box;
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