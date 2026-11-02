import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Recuperar contraseña con Supabase simulado. Lo importante: la sesión de recuperación NO se trata como ingreso (no pide el
// código 2FA ni llama al backend) y, al terminar, no queda sesión.
const supabase = {
  auth: {
    signOut: vi.fn(), getSession: vi.fn(), onAuthStateChange: vi.fn(), resetPasswordForEmail: vi.fn(), updateUser: vi.fn(), signInWithPassword: vi.fn(),
  },
}
const sesion = { access_token: 'tok', user: { email: 'entrenador@pradofc.co' } }
let auth
let fetchSimulado
let alCambiar // callback registrado con onAuthStateChange

async function cargar(url = {}) {
  vi.resetModules()
  vi.doMock('./supabaseClient.js', () => ({ supabase, autenticacionReal: true }))
  vi.doMock('./urlInicial.js', () => ({ urlInicial: url }))
  auth = await import('./authServiceReal.js')
}

beforeEach(() => {
  vi.useFakeTimers()
  globalThis.sessionStorage?.clear()
  for (const f of Object.values(supabase.auth)) f.mockReset()
  supabase.auth.signOut.mockResolvedValue({ error: null })
  supabase.auth.getSession.mockResolvedValue({ data: { session: sesion } })
  supabase.auth.resetPasswordForEmail.mockResolvedValue({ data: {}, error: null })
  supabase.auth.updateUser.mockResolvedValue({ data: {}, error: null })
  supabase.auth.onAuthStateChange.mockImplementation((f) => {
    alCambiar = f
    return { data: { subscription: { unsubscribe: vi.fn() } } }
  })
  fetchSimulado = vi.fn(async () => new Response('{}', { status: 200 }))
  vi.stubGlobal('fetch', fetchSimulado)
  vi.stubGlobal('location', { pathname: '/login', origin: 'https://app.futdata.co', hash: '', search: '' })
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const rechazo = (p) => p.then(() => null, (e) => e)
const RECUPERACION = { hash: '#access_token=a&refresh_token=b&type=recovery' }

describe('solicitarRecuperacion', () => {
  it('pide el correo en minúsculas con redirectTo a /restablecer', async () => {
    await cargar()
    await auth.solicitarRecuperacion(' Ana@PradoFC.co ')
    expect(supabase.auth.resetPasswordForEmail).toHaveBeenCalledWith('ana@pradofc.co', { redirectTo: 'https://app.futdata.co/restablecer' })
  })
  it('429 y over_email_send_rate_limit -> LIMITE; red -> RED; otro -> DESCONOCIDO', async () => {
    await cargar()
    supabase.auth.resetPasswordForEmail.mockResolvedValue({ error: { status: 429, code: 'over_email_send_rate_limit' } })
    expect(await rechazo(auth.solicitarRecuperacion('a@b.co'))).toMatchObject({ codigo: 'LIMITE' })
    supabase.auth.resetPasswordForEmail.mockResolvedValue({ error: { name: 'AuthRetryableFetchError', status: 0 } })
    expect(await rechazo(auth.solicitarRecuperacion('a@b.co'))).toMatchObject({ codigo: 'RED' })
    supabase.auth.resetPasswordForEmail.mockRejectedValue(new TypeError('Failed'))
    expect(await rechazo(auth.solicitarRecuperacion('a@b.co'))).toMatchObject({ codigo: 'RED' })
    supabase.auth.resetPasswordForEmail.mockResolvedValue({ error: { status: 400, code: 'x' } })
    expect(await rechazo(auth.solicitarRecuperacion('a@b.co'))).toMatchObject({ codigo: 'DESCONOCIDO' })
  })
})

describe('esperarRecuperacion', () => {
  it('enlace con type=recovery y sesión: listo', async () => {
    await cargar(RECUPERACION)
    expect(await auth.esperarRecuperacion()).toBe('listo')
  })
  it('llega PASSWORD_RECOVERY después (flujo con code): listo', async () => {
    await cargar({ search: '?code=abc' })
    supabase.auth.getSession.mockResolvedValue({ data: { session: null } })
    const espera = auth.esperarRecuperacion()
    await vi.advanceTimersByTimeAsync(10)
    supabase.auth.getSession.mockResolvedValue({ data: { session: sesion } })
    alCambiar('PASSWORD_RECOVERY', sesion)
    await vi.advanceTimersByTimeAsync(10)
    expect(await espera).toBe('listo')
  })
  it('error en la URL (otp_expired): inválido sin esperar', async () => {
    await cargar({ hash: '#error=access_denied&error_code=otp_expired' })
    expect(await auth.esperarRecuperacion()).toBe('invalido')
  })
  it('sin sesión: espera y, al acabarse el tiempo, inválido', async () => {
    await cargar(RECUPERACION)
    supabase.auth.getSession.mockResolvedValue({ data: { session: null } })
    const espera = auth.esperarRecuperacion({ tiempoMs: 3000 })
    await vi.advanceTimersByTimeAsync(3100)
    expect(await espera).toBe('invalido')
  })
  it('una sesión normal sin enlace de recuperación no cuenta', async () => {
    await cargar()
    const espera = auth.esperarRecuperacion({ tiempoMs: 1000 })
    await vi.advanceTimersByTimeAsync(1100)
    expect(await espera).toBe('invalido')
  })
})

describe('la sesión de recuperación no es un ingreso', () => {
  it('en /restablecer, restaurarSesion no restaura nada: ni código 2FA ni llamadas al backend', async () => {
    await cargar()
    vi.stubGlobal('location', { pathname: '/restablecer', hash: '', search: '' })
    expect(await auth.restaurarSesion({ verificada: false })).toBeNull()
    expect(await auth.restaurarSesion({ verificada: true })).toBeNull()
    expect(fetchSimulado).not.toHaveBeenCalled()
    expect(supabase.auth.getSession).not.toHaveBeenCalled()
  })
  it('cargada con el enlace del correo (type=recovery) en cualquier ruta, tampoco', async () => {
    await cargar(RECUPERACION)
    expect(await auth.restaurarSesion({ verificada: false })).toBeNull()
    expect(fetchSimulado).not.toHaveBeenCalled()
  })
  it('una carga normal sí restaura la sesión pendiente del código (sin regresión)', async () => {
    await cargar()
    const r = await auth.restaurarSesion({ verificada: false })
    expect(r.estado).toBe('pendiente')
    expect(fetchSimulado).toHaveBeenCalled() // pide el código 2FA
  })
})

describe('restablecerContrasena', () => {
  it('sin sesión de recuperación marcada: ENLACE_INVALIDO y no toca Supabase', async () => {
    await cargar()
    expect(await rechazo(auth.restablecerContrasena('Clave2026x'))).toMatchObject({ codigo: 'ENLACE_INVALIDO' })
    expect(supabase.auth.updateUser).not.toHaveBeenCalled()
  })
  it('éxito: actualiza la contraseña y cierra la sesión local (queda sin sesión)', async () => {
    await cargar(RECUPERACION)
    await auth.esperarRecuperacion()
    await auth.restablecerContrasena('Clave2026x')
    expect(supabase.auth.updateUser).toHaveBeenCalledWith({ password: 'Clave2026x' })
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
    expect(supabase.auth.updateUser.mock.invocationCallOrder[0]).toBeLessThan(supabase.auth.signOut.mock.invocationCallOrder[0])
    // ya terminó: otro intento exige un enlace nuevo
    expect(await rechazo(auth.restablecerContrasena('Otra2026x'))).toMatchObject({ codigo: 'ENLACE_INVALIDO' })
  })
  it('contraseña débil o igual a la anterior: POLITICA / IGUAL_ANTERIOR y la sesión sigue para reintentar (sin cerrar)', async () => {
    await cargar(RECUPERACION)
    await auth.esperarRecuperacion()
    supabase.auth.updateUser.mockResolvedValue({ error: { status: 422, code: 'weak_password' } })
    expect(await rechazo(auth.restablecerContrasena('abc'))).toMatchObject({ codigo: 'POLITICA' })
    supabase.auth.updateUser.mockResolvedValue({ error: { status: 422, code: 'same_password' } })
    expect(await rechazo(auth.restablecerContrasena('Clave2026x'))).toMatchObject({ codigo: 'IGUAL_ANTERIOR' })
    expect(supabase.auth.signOut).not.toHaveBeenCalled()
  })
  it('descartarRecuperacion cierra la sesión local solo si hay una recuperación activa', async () => {
    await cargar()
    await auth.descartarRecuperacion()
    expect(supabase.auth.signOut).not.toHaveBeenCalled()
    await cargar(RECUPERACION)
    await auth.descartarRecuperacion()
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
  })
})
