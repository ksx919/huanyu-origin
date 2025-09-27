import { reactive } from 'vue';

// 使用 reactive 创建一个响应式的状态对象
export const authState = reactive({
    token: localStorage.getItem('token') || null,
    nickname: localStorage.getItem('nickname') || null,
    avatar: localStorage.getItem('avatar') || null,
    email: localStorage.getItem('email') || null,

    // 登录成功后调用此方法
    login(token: string, nickname: string, email: string, avatar: string | null) { // <-- 2. 在 login 参数中也加上 email
        this.token = token;
        this.nickname = nickname;
        this.email = email;
        this.avatar = avatar;
        localStorage.setItem('token', token);
        localStorage.setItem('nickname', nickname);
        localStorage.setItem('email', email);
        if (avatar) localStorage.setItem('avatar', avatar);
    },

    // 退出登录时调用
    logout() {
        this.token = null;
        this.nickname = null;
        this.avatar = null;
        this.email = null;
        localStorage.removeItem('token');
        localStorage.removeItem('nickname');
        localStorage.removeItem('avatar');
        localStorage.removeItem('email');
    },

    isAuthenticated() {
        return !!this.token;
    },
    updateProfile(nickname: string | null, avatar: string | null) {
        if (nickname) {
            this.nickname = nickname;
            localStorage.setItem('nickname', nickname);
        }
        if (avatar) {
            this.avatar = avatar;
            localStorage.setItem('avatar', avatar);
        }
    },
});