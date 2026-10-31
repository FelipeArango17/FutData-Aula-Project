import { beforeEach, describe, expect, it, vi } from 'vitest'

const supabase = vi.hoisted(() => ({ auth: { getSession: vi.fn(), signOut: vi.fn() } }))
vi.mock('./supabaseClient.js', () => ({ supabase, autenticacionReal: true }))

import { apiRequest, configurarAlNoAutorizado } from './apiClient.js'

const respuesta = (estado, cuerpo) =>
  new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } })

let fetchSimulado
beforeEach(() => {
  supabase.auth.getSession.mockReset().mockResolvedValue({ data: { session: { access_token: 'token-de-prueba' } } })
  supabase.auth.signOut.mockReset().mockResolvedValue({ error: null })
  configurarAlNoAutorizado(null)
  fetchSimulado = vi.fn(async () => respuesta(200, { ok: true }))
  vi.stubGlobal('fetch', fetchSimulado)
})

const ultimaLlamada = () => fetchSimulado.mock.calls.at(-1)

describe('apiRequest: petición', () => {
  it('añade el access_token de Supabase como Bearer y envía cookies', async () => {
    await apiRequest('/entrenadores')
    const [url, opciones] = ultimaLlamada()
    expect(url).toMatch(/\/api\/entrenadores$/)
    expect(opciones.headers.Authorization).toBe('Bearer token-de-prueba')
    expect(opciones.credentials).toBe('include')
  })

  it('sin sesión no envía Authorization', async () => {
    supabase.auth.getSession.mockResolvedValue({ data: { session: null } })
    await apiRequest('/entrenadores')
    expect(ultimaLlamada()[1].headers.Authorization).toBeUndefined()
  })

  it('con cuerpo envía JSON; sin cuerpo no envía Content-Type', async () => {
    await apiRequest('/auth/2fa/codigo', { method: 'POST' })
    expect(ultimaLlamada()[1].headers['Content-Type']).toBeUndefined()
    expect(ultimaLlamada()[1].body).toBeUndefined()
    await apiRequest('/auth/2fa/verificar', { method: 'POST', body: JSON.stringify({ codigo: '123456' }) })
    expect(ultimaLlamada()[1].headers['Content-Type']).toBe('application/json')
  })

  it('respeta las cabeceras que se le pasan', async () => {
    await apiRequest('/x', { headers: { 'X-Prueba': '1' } })
    expect(ultimaLlamada()[1].headers['X-Prueba']).toBe('1')
    expect(ultimaLlamada()[1].headers.Authorization).toBe('Bearer token-de-prueba')
  })

  it('devuelve el JSON, o null si la respuesta es 204', async () => {
    expect(await apiRequest('/x')).toEqual({ ok: true })
    fetchSimulado.mockResolvedValue(respuesta(204))
    expect(await apiRequest('/x')).toBeNull()
  })
})

describe('apiRequest: errores', () => {
  it('convierte el cuerpo de error en { estado, mensaje, campos, codigo }', async () => {
    fetchSimulado.mockResolvedValue(respuesta(409, { estado: 409, mensaje: 'documento: ya está registrado', campos: ['documento'] }))
    const error = await apiRequest('/entrenadores', { method: 'POST' }).catch((e) => e)
    expect(error).toBeInstanceOf(Error)
    expect(error).toMatchObject({ estado: 409, mensaje: 'documento: ya está registrado', campos: ['documento'], codigo: null })
    expect(error.message).toBe('documento: ya está registrado')
    expect(supabase.auth.signOut).not.toHaveBeenCalled()
  })

  it('conserva el codigo y los msRestantes si el backend los manda', async () => {
    fetchSimulado.mockResolvedValue(respuesta(409, { mensaje: 'x', codigo: 'BLOQUEADO', msRestantes: 900000 }))
    const error = await apiRequest('/x').catch((e) => e)
    expect(error).toMatchObject({ codigo: 'BLOQUEADO', msRestantes: 900000 })
  })

  it('un cuerpo que no es JSON da el mensaje genérico y campos vacíos', async () => {
    fetchSimulado.mockResolvedValue(new Response('<html>', { status: 502 }))
    const error = await apiRequest('/x').catch((e) => e)
    expect(error).toMatchObject({ estado: 502, mensaje: 'No se pudo completar la operación.', campos: [], codigo: null })
  })

  it('401: cierra la sesión local, avisa al manejador y lanza el error', async () => {
    const alNoAutorizado = vi.fn()
    configurarAlNoAutorizado(alNoAutorizado)
    fetchSimulado.mockResolvedValue(respuesta(401, { estado: 401, mensaje: 'Debes iniciar sesión.' }))
    const error = await apiRequest('/entrenadores').catch((e) => e)
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
    expect(alNoAutorizado).toHaveBeenCalledTimes(1)
    expect(error).toMatchObject({ estado: 401, codigo: 'SESION', mensaje: 'Debes iniciar sesión.' })
  })

  it('401: aunque falle el cierre de sesión local, avisa y lanza el 401', async () => {
    const alNoAutorizado = vi.fn()
    configurarAlNoAutorizado(alNoAutorizado)
    supabase.auth.signOut.mockRejectedValue(new Error('sin red'))
    fetchSimulado.mockResolvedValue(respuesta(401, {}))
    const error = await apiRequest('/x').catch((e) => e)
    expect(alNoAutorizado).toHaveBeenCalledTimes(1)
    expect(error.estado).toBe(401)
  })

  it('403: sube como error de permisos y NO cierra la sesión', async () => {
    const alNoAutorizado = vi.fn()
    configurarAlNoAutorizado(alNoAutorizado)
    fetchSimulado.mockResolvedValue(respuesta(403, { estado: 403, mensaje: 'No tienes permiso para hacer esta acción.' }))
    const error = await apiRequest('/usuarios').catch((e) => e)
    expect(error).toMatchObject({ estado: 403, codigo: 'PERMISOS', mensaje: 'No tienes permiso para hacer esta acción.' })
    expect(supabase.auth.signOut).not.toHaveBeenCalled()
    expect(alNoAutorizado).not.toHaveBeenCalled()
  })

  it('sin respuesta del servidor lanza RED con estado 0', async () => {
    fetchSimulado.mockRejectedValue(new TypeError('Failed to fetch'))
    const error = await apiRequest('/x').catch((e) => e)
    expect(error).toMatchObject({ estado: 0, codigo: 'RED', campos: [] })
  })
})
