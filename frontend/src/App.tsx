import { useEffect, useState } from 'react'

type Health = { status: string }

function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/health')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<Health>
      })
      .then(setHealth)
      .catch((err: Error) => setError(err.message))
  }, [])

  return (
    <main>
      <h1>nutri-app</h1>
      {error && <p>Backend no disponible: {error}</p>}
      {!error && !health && <p>Comprobando backend…</p>}
      {health && <p>Backend: {health.status}</p>}
    </main>
  )
}

export default App
