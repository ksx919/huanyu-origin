import { reactive } from 'vue';

export const authState = reactive({
    token: localStorage.getItem('token') || null,
    nickname: localStorage.getItem('nickname') || null,
    avatar: localStorage.getItem('avatar') || null,

    login(token: string, nickname: string, avatar: string | null) {
        this.token = token;
        this.nickname = nickname;
        this.avatar = avatar;
        localStorage.setItem('token', token);
        localStorage.setItem('nickname', nickname);
        if (avatar) localStorage.setItem('avatar', avatar);
    },

    logout() {
        this.token = null;
        this.nickname = null;
        this.avatar = null;
        localStorage.removeItem('token');
        localStorage.removeItem('nickname');
        localStorage.removeItem('avatar');
    },

    isAuthenticated() {
        return !!this.token;
    },
});