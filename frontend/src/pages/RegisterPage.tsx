import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import type { Role } from '../auth/context'
import { useAuth } from '../auth/useAuth'

export function RegisterPage() {
  const { register, login } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('PATIENT')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await register({ name, email, password, role })
      await login(email, password) // iniciamos sesión directamente tras registrarse
      navigate('/')
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
        setFieldErrors(err.fieldErrors)
      } else {
        setError('No se pudo conectar con el servidor')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main>
      <h1>Crear cuenta</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Nombre
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        {fieldErrors.name && <p role="alert">{fieldErrors.name}</p>}

        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        {fieldErrors.email && <p role="alert">{fieldErrors.email}</p>}

        <label>
          Contraseña (mínimo 8 caracteres)
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {fieldErrors.password && <p role="alert">{fieldErrors.password}</p>}

        <label>
          Soy
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            <option value="PATIENT">Paciente</option>
            <option value="NUTRITIONIST">Nutricionista</option>
          </select>
        </label>

        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Creando…' : 'Registrarme'}
        </button>
      </form>
      <p>
        ¿Ya tenés cuenta? <Link to="/login">Iniciá sesión</Link>
      </p>
    </main>
  )
}
