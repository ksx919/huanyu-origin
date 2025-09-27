import axios from 'axios';

// 使用 Vite 环境变量配置后端基础地址
// 默认为 http://localhost:8000
const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

const http = axios.create({
  baseURL,
  timeout: 15000,
});

export default http;