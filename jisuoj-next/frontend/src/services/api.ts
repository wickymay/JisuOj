import axios, { AxiosError } from 'axios'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  // Spring Security returns a BREACH-protected (masked) token from /auth/csrf.
  // Axios would otherwise overwrite it with the raw cookie value.
  withXSRFToken: false,
  timeout: 20_000,
})

export async function ensureCsrf(): Promise<void> {
  const { data } = await api.get<{ token: string; headerName: string }>('/auth/csrf')
  api.defaults.headers.common[data.headerName] = data.token
}

export function errorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const body = error.response?.data as { message?: string; errors?: Record<string, string> } | undefined
    if (body?.errors) return Object.values(body.errors).join('；')
    if (body?.message) return body.message
    if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
}
