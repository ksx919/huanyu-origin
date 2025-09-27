<template>
  <div class="background-container">
    <div class="background-image"></div>
    <div class="particles">
      <div class="particle" v-for="n in 30" :key="n"></div>
    </div>
  </div>

  <header class="app-header" v-if="authState.isAuthenticated()">
    <div class="header-content">
      <span>欢迎你，{{ authState.nickname }}</span>
      <nav>
        <router-link to="/">主页</router-link>
        <router-link to="/profile">个人中心</router-link>
        <a href="#" @click.prevent="logout">退出登录</a>
      </nav>
    </div>
  </header>

  <div class="main-container">
    <router-view></router-view>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { authState } from './store/auth';

const router = useRouter();

const logout = () => {
  authState.logout();
  router.push('/login');
};
</script>

<style>
/* 4. 将你原来的全局和背景样式放在这里，注意不要加 scoped */
:root {
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

/* 随机粒子效果 */
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

/* 页头样式 */
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  background: rgba(0, 0, 0, 0.2);
  color: white;
  padding: 15px 30px;
  z-index: 10;
  box-sizing: border-box;
}
.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-content nav a {
  color: white;
  text-decoration: none;
  margin-left: 20px;
}
.header-content nav a:hover {
  text-decoration: underline;
}
</style>