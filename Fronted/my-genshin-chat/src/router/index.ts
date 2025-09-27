import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import Login from '../views/Login.vue';
import Profile from '../views/Profile.vue';
import { authState } from '../store/auth';

const routes = [
    {
        path: '/',
        name: 'Home',
        component: Home,
        meta: { requiresAuth: true }
    },
    {
        path: '/login',
        name: 'Login',
        component: Login,
    },
    {
        path: '/profile',
        name: 'Profile',
        component: Profile,
        meta: { requiresAuth: true }
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach((to, from, next) => {
    const requiresAuth = to.meta.requiresAuth;
    const isAuthenticated = authState.isAuthenticated();

    if (requiresAuth && !isAuthenticated) {
        next({ name: 'Login' });
    } else if (to.name === 'Login' && isAuthenticated) {
        next({ name: 'Home' });
    } else {
        next();
    }
});

export default router;