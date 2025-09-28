<template>
  <div class="background-container">
    <div class="background-image"></div>
    <div class="particles">
      <div class="particle" v-for="n in 150" :key="n"></div>
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

<style lang="scss">

@use "sass:math";

:root {
  --text-light: #f4f4f4;
  --bg-dark-glass: rgba(0, 0, 0, 0.3);
}


body {
  margin: 0;
  font-family: 'Helvetica Neue', 'Hiragino Sans GB', 'WenQuanYi Micro Hei', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
  background-color: transparent;
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
  background: white;
  border-radius: 50%;
  animation: float 25s infinite linear;


  box-shadow: 0 0 10px 5px rgba(255, 255, 255, 0.8),
  0 0 20px 10px rgba(255, 255, 255, 0.5),
  0 0 30px 15px rgba(255, 255, 255, 0.2);

  filter: blur(2px);

  z-index: 1;
}

@keyframes float {
  0% {
    transform: translateY(100vh) scale(0.2);
    opacity: 0;
  }
  20% {
    opacity: 0.8;
  }
  80% {
    opacity: 0.6;
  }
  100% {
    transform: translateY(30vh) scale(1.2);
    opacity: 0;
  }
}

/*SCSS 循环：调整随机范围 */
@for $i from 1 through 150 {
  .particle:nth-child(#{$i}) {
    $size: (math.random(5) + 4) + px;
    top: (math.random(100)) + vh;
    left: (math.random(100)) + vw;
    width: $size;
    height: $size;

    animation-delay: (math.random(40)) * -1s;
    animation-duration: (math.random(20) + 25) + s;
  }
}

.main-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: var(--text-light);
}

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
  transition: all 0.25s ease;
}
.header-content nav a:hover {

  color: #38b6ff;
  text-shadow: 0 0 8px rgba(56, 182, 255, 0.8);
  text-decoration: none;
}

.header-content nav a.router-link-active {
  color: #38b6ff;
  text-shadow: 0 0 8px rgba(56, 182, 255, 0.8);
}
</style>