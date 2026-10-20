import { defineStore } from 'pinia'
import { api, ensureCsrf, errorMessage } from '../services/api'
import type { UserPrincipal } from '../types/domain'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserPrincipal | null,
    ready: false,
    busy: false,
    error: '',
  }),
  getters: {
    authenticated: (state) => !!state.user,
    isSuperAdmin: (state) => !!state.user?.roles.includes('ROLE_SUPER_ADMIN'),
    isAdmin: (state) => !!state.user?.roles.some((role) => [
      'ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_PROBLEM_EDITOR', 'ROLE_CONTEST_CREATOR',
      'ROLE_USER_MANAGER', 'ROLE_PASSWORD_SETTER',
    ].includes(role)),
  },
  actions: {
    async initialize() {
      try {
        await ensureCsrf()
        const response = await api.get<UserPrincipal>('/auth/me')
        this.user = response.status === 204 ? null : response.data
      } catch {
        this.user = null
      } finally {
        this.ready = true
      }
    },
    async login(userId: string, password: string) {
      this.busy = true
      this.error = ''
      try {
        await ensureCsrf()
        this.user = (await api.post<UserPrincipal>('/auth/login', { userId, password })).data
      } catch (error) {
        this.error = errorMessage(error)
        throw error
      } finally {
        this.busy = false
      }
    },
    async register(userId: string, password: string, nick: string, email: string) {
      this.busy = true
      this.error = ''
      try {
        await ensureCsrf()
        this.user = (await api.post<UserPrincipal>('/auth/register', { userId, password, nick, email })).data
      } catch (error) {
        this.error = errorMessage(error)
        throw error
      } finally {
        this.busy = false
      }
    },
    async logout() {
      await ensureCsrf()
      await api.post('/auth/logout')
      this.user = null
    },
  },
})
