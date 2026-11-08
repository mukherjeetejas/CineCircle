import { apiFetch } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserProfile } from '../types'

export function login(body: LoginRequest) {
  return apiFetch<AuthResponse>('/auth/login', { method: 'POST', json: body, silent: true })
}

export function register(body: RegisterRequest) {
  return apiFetch<AuthResponse>('/auth/register', { method: 'POST', json: body, silent: true })
}

export function fetchMe() {
  return apiFetch<UserProfile>('/users/me')
}
