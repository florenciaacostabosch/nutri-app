import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'

type Health = { status: string }

export function HomePage() {
  const { user, logout } = useAuth()
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Health>('/api/health')
      .then(setHealth)
      .catch((err: Error) => setError(err.message))
  }, [])

  return (
    <main>
      <h1>nutri-app</h1>
      <p>
        Hola, {user?.name} ({user?.role === 'NUTRITIONIST' ? 'nutricionista' : 'paciente'})
      </p>
      {error && <p>Backend no disponible: {error}</p>}
      {!error && !health && <p>Comprobando backend…</p>}
      {health && <p>Backend: {health.status}</p>}
      <button onClick={logout}>Cerrar sesión</button>
    </main>
  )
}
