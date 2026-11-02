import { beforeEach, describe, expect, it, vi } from 'vitest'

const supabase = vi.hoisted(() => ({ auth: { getSession: vi.fn(), signOut: vi.fn() } }))
vi.mock('./supabaseClient.js', () => ({ supabase, autenticacionReal: true }))

import { apiListaPaginada, apiRequest, configurarAlNoAutorizado, normalizarPagina, subirArchivo } from './apiClient.js'

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

describe('apiRequest: FormData y JSON', () => {
  it('con FormData no pone Content-Type (lo pone el navegador con el boundary) ni lo convierte en JSON', async () => {
    const datos = new FormData()
    datos.append('archivo', new Blob(['x']), 'a.pdf')
    await apiRequest('/documentos', { method: 'POST', body: datos })
    const [, opciones] = ultimaLlamada()
    expect(opciones.headers['Content-Type']).toBeUndefined()
    expect(opciones.body).toBe(datos)
  })

  it('con JSON sigue enviando Content-Type: application/json', async () => {
    await apiRequest('/entrenadores', { method: 'POST', body: JSON.stringify({ a: 1 }) })
    expect(ultimaLlamada()[1].headers['Content-Type']).toBe('application/json')
    expect(ultimaLlamada()[1].body).toBe('{"a":1}')
  })

  it('subirArchivo arma el FormData con el archivo y los campos (omite null y undefined)', async () => {
    const archivo = new Blob(['x'], { type: 'application/pdf' })
    await subirArchivo('/deportistas/3/documentos', archivo, { tipo: 'Registro civil', vencimiento: null, nota: undefined })
    const [url, opciones] = ultimaLlamada()
    expect(url).toMatch(/\/api\/deportistas\/3\/documentos$/)
    expect(opciones.method).toBe('POST')
    expect(opciones.headers['Content-Type']).toBeUndefined()
    expect(opciones.body).toBeInstanceOf(FormData)
    expect([...opciones.body.keys()]).toEqual(['archivo', 'tipo'])
    expect(opciones.body.get('tipo')).toBe('Registro civil')
  })
})

describe('apiListaPaginada', () => {
  it('arma la query (page, size, sort y filtros; sin los vacíos)', async () => {
    await apiListaPaginada('/deportistas', { page: 2, size: 10, sort: 'nombre,asc', estado: 'activo', texto: '', categoria: undefined })
    expect(ultimaLlamada()[0]).toMatch(/\/api\/deportistas\?page=2&size=10&sort=nombre%2Casc&estado=activo$/)
  })

  it('normaliza la forma de Spring Page', async () => {
    fetchSimulado.mockResolvedValue(respuesta(200, { content: [{ id: 1 }, { id: 2 }], totalElements: 25, number: 1, size: 2 }))
    expect(await apiListaPaginada('/x', { page: 1, size: 2 })).toEqual({ items: [{ id: 1 }, { id: 2 }], total: 25, pagina: 1, tamano: 2 })
  })

  it('normaliza la forma { items, total } usando page y size pedidos', async () => {
    fetchSimulado.mockResolvedValue(respuesta(200, { items: [{ id: 7 }], total: 11 }))
    expect(await apiListaPaginada('/x', { page: 3, size: 5 })).toEqual({ items: [{ id: 7 }], total: 11, pagina: 3, tamano: 5 })
  })

  it('un arreglo simple o un cuerpo vacío no rompen', () => {
    expect(normalizarPagina([{ id: 1 }])).toEqual({ items: [{ id: 1 }], total: 1, pagina: 0, tamano: 1 })
    expect(normalizarPagina(null)).toEqual({ items: [], total: 0, pagina: 0, tamano: 0 })
  })

  it('los errores sin cambios: 401, 403, 409 y red', async () => {
    fetchSimulado.mockResolvedValue(respuesta(401, {}))
    expect(await apiListaPaginada('/x').catch((e) => e)).toMatchObject({ estado: 401, codigo: 'SESION' })
    fetchSimulado.mockResolvedValue(respuesta(403, {}))
    expect(await apiListaPaginada('/x').catch((e) => e)).toMatchObject({ estado: 403, codigo: 'PERMISOS' })
    fetchSimulado.mockResolvedValue(respuesta(409, { mensaje: 'Ya existe.', campos: ['documento'], codigo: 'DUPLICADO' }))
    expect(await apiListaPaginada('/x').catch((e) => e)).toMatchObject({ estado: 409, campos: ['documento'], codigo: 'DUPLICADO' })
    fetchSimulado.mockRejectedValue(new TypeError('Failed'))
    expect(await apiListaPaginada('/x').catch((e) => e)).toMatchObject({ estado: 0, codigo: 'RED' })
  })
})

describe('apiRequest: respuestas binarias', () => {
  it('con respuesta: "blob" devuelve el cuerpo binario (PDF) y envía el token', async () => {
    fetchSimulado.mockResolvedValue(new Response('%PDF-1.4', { status: 200, headers: { 'Content-Type': 'application/pdf' } }))
    const blob = await apiRequest('/pagos/x/comprobante', { respuesta: 'blob' })
    expect(blob).toBeInstanceOf(Blob)
    expect(await blob.text()).toBe('%PDF-1.4')
    expect(ultimaLlamada()[1].headers.Authorization).toBe('Bearer token-de-prueba')
    expect(ultimaLlamada()[1].respuesta).toBeUndefined() // no viaja a fetch
  })

  it('un error en una descarga binaria sigue siendo un error de la API (409 con su mensaje)', async () => {
    fetchSimulado.mockResolvedValue(respuesta(409, { mensaje: 'estado: el comprobante solo existe para pagos aprobados', campos: ['estado'] }))
    const error = await apiRequest('/pagos/x/comprobante', { respuesta: 'blob' }).catch((e) => e)
    expect(error).toMatchObject({ estado: 409, campos: ['estado'] })
  })

  it('sin la opción sigue leyendo JSON', async () => {
    fetchSimulado.mockResolvedValue(respuesta(200, { ok: 1 }))
    expect(await apiRequest('/x')).toEqual({ ok: 1 })
  })
})

describe('apiRequest: rutas públicas (publica: true)', () => {
  it('no manda Authorization aunque haya sesión, ni siquiera consulta la sesión', async () => {
    await apiRequest('/auth/crear-contrasena', { method: 'POST', publica: true, body: JSON.stringify({ token: 't', contrasena: 'x' }) })
    const [url, opciones] = ultimaLlamada()
    expect(url).toMatch(/\/api\/auth\/crear-contrasena$/)
    expect(opciones.headers.Authorization).toBeUndefined()
    expect(opciones.headers['Content-Type']).toBe('application/json')
    expect(opciones.publica).toBeUndefined() // no viaja a fetch
    expect(supabase.auth.getSession).not.toHaveBeenCalled()
  })

  it('un 401 no cierra la sesión ni llama al manejador; el error conserva su estado y mensaje', async () => {
    const alNoAutorizado = vi.fn()
    configurarAlNoAutorizado(alNoAutorizado)
    fetchSimulado.mockResolvedValue(respuesta(401, { mensaje: 'Token inválido.' }))
    const error = await apiRequest('/auth/crear-contrasena', { method: 'POST', publica: true }).catch((e) => e)
    expect(error).toMatchObject({ estado: 401, mensaje: 'Token inválido.' })
    expect(alNoAutorizado).not.toHaveBeenCalled()
    expect(supabase.auth.signOut).not.toHaveBeenCalled()
  })

  it('un 400 llega con su estado; sin publica, el 401 sí cierra la sesión', async () => {
    fetchSimulado.mockResolvedValue(respuesta(400, { mensaje: 'Enlace inválido.' }))
    expect(await apiRequest('/x', { publica: true }).catch((e) => e)).toMatchObject({ estado: 400 })
    const alNoAutorizado = vi.fn()
    configurarAlNoAutorizado(alNoAutorizado)
    fetchSimulado.mockResolvedValue(respuesta(401, {}))
    await apiRequest('/x').catch(() => {})
    expect(alNoAutorizado).toHaveBeenCalledTimes(1)
  })
})
