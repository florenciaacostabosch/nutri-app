import { useEffect, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStorage } from '../api/client'
import { AuthContext, type RegisterData, type User } from './context'

interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  // Si hay token guardado, empezamos "cargando" hasta validarlo con el backend.
  const [loading, setLoading] = useState<boolean>(() => tokenStorage.get() !== null)

  // Al abrir la app: si hay token, pedimos /me para saber quién es.
  useEffect(() => {
    if (!tokenStorage.get()) return
    api<User>('/api/users/me')
      .then(setUser)
      .catch(() => tokenStorage.clear()) // token vencido o inválido
      .finally(() => setLoading(false))
  }, [])

  // Si cualquier petición recibe 401 con token, cerramos la sesión.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      tokenStorage.clear()
      setUser(null)
    })
  }, [])

  async function login(email: string, password: string) {
    const { accessToken } = await api<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    tokenStorage.set(accessToken)
    setUser(await api<User>('/api/users/me'))
  }

  async function register(data: RegisterData) {
    await api<User>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  }

  function logout() {
    tokenStorage.clear()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
