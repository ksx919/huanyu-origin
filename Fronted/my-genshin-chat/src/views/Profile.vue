<template>
  <div class="profile-container">
    <h1>个人中心</h1>
    <p>这里是个人中心页面，未来可以开发修改昵称、头像和密码的功能。</p>
    <p>当前用户: {{ authState.nickname }}</p>
  </div>
</template>

<script setup lang="ts">
import { authState } from '../store/auth';
</script>

<style scoped>
.profile-container {
  color: white;
  text-align: center;
  padding-top: 100px;
}
</style>