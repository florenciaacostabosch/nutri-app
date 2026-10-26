const TOKEN_KEY = 'nutri_token'

export const tokenStorage = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// Error con la forma del ProblemDetail (RFC 9457) que devuelve el backend.
export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// Se llama cuando una petición autenticada devuelve 401 (token vencido o inválido).
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type', 'application/json')

  const token = tokenStorage.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { ...options, headers })

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    // Un 401 con token guardado = sesión caducada. Sin token (login fallido) no.
    if (response.status === 401 && token) onUnauthorized()
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `Error ${response.status}`,
      problem?.errors ?? {},
    )
  }

  return response.json() as Promise<T>
}
