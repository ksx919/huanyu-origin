import axios from 'axios';

// 在开发环境下，使用同源相对路径，交由 Vite 代理转发，避免跨域预检与双请求失败
// 在生产环境下，使用环境变量配置的后端基础地址
const baseURL = import.meta.env.DEV
  ? '/'
  : (import.meta.env.VITE_API_BASE_URL || '/');

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