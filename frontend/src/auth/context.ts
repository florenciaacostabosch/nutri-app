import { createContext } from 'react'

export type Role = 'PATIENT' | 'NUTRITIONIST'

export interface User {
  id: number
  email: string
  name: string
  role: Role
}

export interface RegisterData {
  email: string
  password: string
  name: string
  role: Role
}

export interface AuthContextValue {
  user: User | null
  // true mientras comprobamos si el token guardado sigue siendo válido
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (data: RegisterData) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
