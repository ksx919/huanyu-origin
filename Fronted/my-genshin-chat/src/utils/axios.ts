import axios from 'axios';

// 使用 Vite 环境变量配置后端基础地址
// 默认为 http://localhost:8000
const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

const http = axios.create({
  baseURL,
  timeout: 15000,
});

// 将本地存储的JWT自动注入到请求头
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers = config.headers || {};
    config.headers['Authorization'] = `Bearer ${token}`;
    // 兼容后端自定义 header 读取方式
    config.headers['token'] = token;
  }
  return config;
});

// 导出基础地址与获取授权头的工具，供fetch等流式API使用
export const apiBaseURL = baseURL;
export const getAuthHeader = () => {
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}`, token } : {};
};

export default http;