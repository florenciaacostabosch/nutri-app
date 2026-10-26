import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'

export function ProtectedRoute() {
  const { user, loading } = useAuth()

  if (loading) return <p>Cargando…</p>
  if (!user) return <Navigate to="/login" replace />

  return <Outlet />
}
