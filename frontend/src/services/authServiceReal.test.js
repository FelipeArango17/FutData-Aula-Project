import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Supabase y fetch simulados. El servicio guarda su estado a nivel de módulo: cada prueba carga una copia nueva.
const ID = '3f8c0a52-6c1e-4f55-9d0e-1a2b3c4d5e6f'
const supabase = { auth: { signInWithPassword: vi.fn(), signOut: vi.fn(), getSession: vi.fn(), onAuthStateChange: vi.fn() } }

const respuesta = (estado, cuerpo) =>
  new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } })
const perfil = (extra = {}) => ({ id: ID, nombre: 'Laura', apellido: 'Gómez', correo: 'tesorero@pradofc.co', rol: 'TESORERO', activo: true, ...extra })
const codigo202 = { correoEnmascarado: 't***@pradofc.co', vigenciaSegundos: 600, reenvioDisponibleEn: 60000 }
const error2FA = (mensaje, extra = {}) => respuesta(409, { estado: 409, mensaje, campos: ['codigo'], ...extra })

let auth
let fetchSimulado
let rutas // { 'GET /usuarios/<id>': () => Response, ... }

const llamadas = () => fetchSimulado.mock.calls.map(([url, opciones]) => ({ ruta: url.replace(/^.*\/api/, ''), metodo: opciones.method ?? 'GET', opciones }))
const llamada = (metodo, ruta) => llamadas().find((l) => l.metodo === metodo && l.ruta === ruta)

beforeEach(async () => {
  globalThis.sessionStorage?.clear()
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-30T10:00:00Z'))
  supabase.auth.signInWithPassword.mockReset().mockResolvedValue({ data: { user: { id: ID } }, error: null })
  supabase.auth.signOut.mockReset().mockResolvedValue({ error: null })
  supabase.auth.getSession.mockReset().mockResolvedValue({ data: { session: { access_token: 'token-de-prueba', user: { email: 'Tesorero@pradofc.co' } } } })
  supabase.auth.onAuthStateChange.mockReset().mockReturnValue({ data: { subscription: { unsubscribe: vi.fn() } } })
  rutas = {
    [`GET /usuarios/${ID}`]: () => respuesta(200, perfil()),
    'POST /auth/2fa/codigo': () => respuesta(202, codigo202),
    'POST /auth/2fa/verificar': () => respuesta(200, perfil()),
    'POST /auth/2fa/reenviar': () => respuesta(200, codigo202),
  }
  fetchSimulado = vi.fn(async (url, opciones) => {
    const clave = `${opciones.method ?? 'GET'} ${url.replace(/^.*\/api/, '')}`
    if (!rutas[clave]) throw new Error(`Llamada no prevista: ${clave}`)
    return rutas[clave]()
  })
  vi.stubGlobal('fetch', fetchSimulado)
  vi.resetModules()
  vi.doMock('./supabaseClient.js', () => ({ supabase, autenticacionReal: true }))
  auth = await import('./authServiceReal.js')
})
afterEach(() => vi.useRealTimers())

const entrar = (correo = ' tesorero@pradofc.co ') => auth.iniciarSesion(correo, 'Clave2026!')
const rechazo = (promesa) => promesa.then(() => null, (error) => error)

describe('iniciarSesion', () => {
  it('con credenciales válidas pide el perfil y el código, y devuelve la sesión pendiente', async () => {
    const usuario = await entrar()
    expect(supabase.auth.signInWithPassword).toHaveBeenCalledWith({ email: 'tesorero@pradofc.co', password: 'Clave2026!' })
    expect(usuario).toEqual({ correo: 'tesorero@pradofc.co', rol: 'tesorero', nombre: 'Laura', verificada: false })
    // el perfil lleva el token; /codigo va sin cuerpo (el usuario sale del JWT)
    expect(llamada('GET', `/usuarios/${ID}`).opciones.headers.Authorization).toBe('Bearer token-de-prueba')
    const codigo = llamada('POST', '/auth/2fa/codigo')
    expect(codigo.opciones.body).toBeUndefined()
    expect(codigo.opciones.credentials).toBe('include')
  })

  it('estadoCodigo sale de la respuesta de /codigo', async () => {
    await entrar()
    expect(auth.estadoCodigo()).toEqual({
      correo: 'tesorero@pradofc.co',
      correoEnmascarado: 't***@pradofc.co',
      rol: 'tesorero',
      nombre: 'Laura',
      intentosRestantes: 5,
      reenvioDisponibleEn: Date.now() + 60000,
      bloqueoHasta: null,
    })
  })

  it('estadoCodigo es null sin solicitud', () => {
    expect(auth.estadoCodigo()).toBeNull()
  })

  it('credenciales malas: CREDENCIALES, sin pedir perfil ni código', async () => {
    supabase.auth.signInWithPassword.mockResolvedValue({ data: { user: null }, error: { name: 'AuthApiError', status: 400, code: 'invalid_credentials' } })
    const error = await rechazo(entrar())
    expect(error.codigo).toBe('CREDENCIALES')
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  it.each([
    ['fallo de red de Supabase', { name: 'AuthRetryableFetchError', status: 0 }, 'RED'],
    ['error 500 de Supabase', { name: 'AuthApiError', status: 500 }, 'RED'],
    ['cuenta bloqueada en Supabase', { name: 'AuthApiError', status: 400, code: 'user_banned' }, 'INACTIVA'],
  ])('%s', async (_nombre, errorSupabase, codigo) => {
    supabase.auth.signInWithPassword.mockResolvedValue({ data: { user: null }, error: errorSupabase })
    expect((await rechazo(entrar())).codigo).toBe(codigo)
  })

  it('demasiados intentos en Supabase (429): BLOQUEADO con msRestantes', async () => {
    supabase.auth.signInWithPassword.mockResolvedValue({ data: { user: null }, error: { name: 'AuthApiError', status: 429, code: 'over_request_rate_limit' } })
    const error = await rechazo(entrar())
    expect(error).toMatchObject({ codigo: 'BLOQUEADO', msRestantes: 60000 })
  })

  it('si signInWithPassword lanza (sin red), RED', async () => {
    supabase.auth.signInWithPassword.mockRejectedValue(new TypeError('Failed to fetch'))
    expect((await rechazo(entrar())).codigo).toBe('RED')
  })

  it('perfil con 403 (rol sin permiso para GET /api/usuarios/{id}): PERFIL, cierra la sesión y no pide código', async () => {
    rutas[`GET /usuarios/${ID}`] = () => respuesta(403, { estado: 403, mensaje: 'No tienes permiso para hacer esta acción.' })
    const error = await rechazo(entrar())
    expect(error).toMatchObject({ codigo: 'PERFIL', estado: 403 })
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
    expect(llamada('POST', '/auth/2fa/codigo')).toBeUndefined()
    expect(auth.estadoCodigo()).toBeNull()
  })

  it('perfil con 404 (cuenta de Auth sin perfil en FutData): PERFIL', async () => {
    rutas[`GET /usuarios/${ID}`] = () => respuesta(404, { mensaje: 'No existe un usuario con ese id.' })
    expect((await rechazo(entrar())).codigo).toBe('PERFIL')
    expect(supabase.auth.signOut).toHaveBeenCalled()
  })

  it('cuenta inactiva en el perfil: INACTIVA y cierra la sesión', async () => {
    rutas[`GET /usuarios/${ID}`] = () => respuesta(200, perfil({ activo: false }))
    expect((await rechazo(entrar())).codigo).toBe('INACTIVA')
    expect(supabase.auth.signOut).toHaveBeenCalled()
  })

  it('si el código no se pudo enviar (503): RED y cierra la sesión', async () => {
    rutas['POST /auth/2fa/codigo'] = () => respuesta(503, { mensaje: 'No pudimos enviarte el código.' })
    expect((await rechazo(entrar())).codigo).toBe('RED')
    expect(supabase.auth.signOut).toHaveBeenCalled()
    expect(auth.estadoCodigo()).toBeNull()
  })

  it('si el backend no responde: RED', async () => {
    fetchSimulado.mockRejectedValue(new TypeError('Failed to fetch'))
    expect((await rechazo(entrar())).codigo).toBe('RED')
  })

  it('si el código está bloqueado (409 en /codigo): BLOQUEADO', async () => {
    rutas['POST /auth/2fa/codigo'] = () => error2FA('La verificación está bloqueada temporalmente.')
    expect((await rechazo(entrar())).codigo).toBe('BLOQUEADO')
  })
})

describe('verificarCodigo', () => {
  beforeEach(() => entrar())

  it('FORMATO sin llamar al backend', async () => {
    fetchSimulado.mockClear()
    expect((await rechazo(auth.verificarCodigo('12ab'))).codigo).toBe('FORMATO')
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  it('con el código correcto envía SOLO { codigo }, devuelve el usuario y limpia la solicitud', async () => {
    const usuario = await auth.verificarCodigo('123456', { recordar: true })
    expect(usuario).toEqual({ correo: 'tesorero@pradofc.co', rol: 'tesorero', nombre: 'Laura' })
    const { opciones } = llamada('POST', '/auth/2fa/verificar')
    expect(JSON.parse(opciones.body)).toEqual({ codigo: '123456' })
    expect(auth.estadoCodigo()).toBeNull()
  })

  it('"recordar" no envía nada al backend', async () => {
    await auth.verificarCodigo('123456', { recordar: true })
    expect(llamadas().some((l) => l.ruta.includes('equipo') || String(l.opciones.body ?? '').includes('recordar'))).toBe(false)
  })

  it('INCORRECTO lleva los intentos restantes, contados en el servicio', async () => {
    rutas['POST /auth/2fa/verificar'] = () => error2FA('El código no es correcto.')
    expect(await rechazo(auth.verificarCodigo('000000'))).toMatchObject({ codigo: 'INCORRECTO', intentosRestantes: 4 })
    expect(await rechazo(auth.verificarCodigo('000000'))).toMatchObject({ codigo: 'INCORRECTO', intentosRestantes: 3 })
    expect(auth.estadoCodigo().intentosRestantes).toBe(3)
  })

  it('"Se alcanzó el máximo de intentos" es BLOQUEADO con 15 minutos y queda en estadoCodigo', async () => {
    rutas['POST /auth/2fa/verificar'] = () => error2FA('Se alcanzó el máximo de intentos. Intenta de nuevo más tarde.')
    expect(await rechazo(auth.verificarCodigo('000000'))).toMatchObject({ codigo: 'BLOQUEADO', msRestantes: auth.BLOQUEO_MS })
    expect(auth.estadoCodigo().bloqueoHasta).toBe(Date.now() + auth.BLOQUEO_MS)
  })

  it('"bloqueada temporalmente" es BLOQUEADO y descuenta el tiempo ya transcurrido', async () => {
    rutas['POST /auth/2fa/verificar'] = () => error2FA('Se alcanzó el máximo de intentos. Intenta de nuevo más tarde.')
    await rechazo(auth.verificarCodigo('000000'))
    vi.setSystemTime(Date.now() + 5 * 60 * 1000)
    rutas['POST /auth/2fa/verificar'] = () => error2FA('La verificación está bloqueada temporalmente.')
    expect(await rechazo(auth.verificarCodigo('000000'))).toMatchObject({ codigo: 'BLOQUEADO', msRestantes: 10 * 60 * 1000 })
  })

  it('VENCIDO', async () => {
    rutas['POST /auth/2fa/verificar'] = () => error2FA('El código expiró. Solicita uno nuevo.')
    expect((await rechazo(auth.verificarCodigo('000000'))).codigo).toBe('VENCIDO')
  })

  it('SIN_SOLICITUD descarta la solicitud local', async () => {
    rutas['POST /auth/2fa/verificar'] = () => error2FA('No hay un código pendiente de verificación.')
    expect((await rechazo(auth.verificarCodigo('000000'))).codigo).toBe('SIN_SOLICITUD')
    expect(auth.estadoCodigo()).toBeNull()
  })

  it('un 400 sobre el campo codigo es FORMATO', async () => {
    rutas['POST /auth/2fa/verificar'] = () => respuesta(400, { estado: 400, mensaje: 'codigo: debe tener exactamente 6 dígitos', campos: ['codigo'] })
    expect((await rechazo(auth.verificarCodigo('000000'))).codigo).toBe('FORMATO')
  })

  it('si el backend manda codigo y msRestantes en el cuerpo, los usa en lugar del texto', async () => {
    rutas['POST /auth/2fa/verificar'] = () => respuesta(409, { mensaje: 'texto cualquiera', campos: ['codigo'], codigo: 'BLOQUEADO', msRestantes: 120000 })
    expect(await rechazo(auth.verificarCodigo('000000'))).toMatchObject({ codigo: 'BLOQUEADO', msRestantes: 120000 })
  })

  it('un error que no es del código (500) se deja subir tal cual', async () => {
    rutas['POST /auth/2fa/verificar'] = () => respuesta(500, { mensaje: 'Ocurrió un error interno.' })
    expect(await rechazo(auth.verificarCodigo('000000'))).toMatchObject({ estado: 500 })
  })
})

describe('reenviarCodigo', () => {
  beforeEach(() => entrar())

  it('POST sin cuerpo, reinicia intentos y actualiza cuándo se puede reenviar', async () => {
    rutas['POST /auth/2fa/verificar'] = () => error2FA('El código no es correcto.')
    await rechazo(auth.verificarCodigo('000000'))
    vi.setSystemTime(Date.now() + 61000)
    const resultado = await auth.reenviarCodigo()
    expect(llamada('POST', '/auth/2fa/reenviar').opciones.body).toBeUndefined()
    expect(resultado.reenvioDisponibleEn).toBe(Date.now() + 60000)
    expect(auth.estadoCodigo()).toMatchObject({ intentosRestantes: 5, reenvioDisponibleEn: Date.now() + 60000 })
  })

  it('DEMASIADO_PRONTO con los ms que faltan', async () => {
    rutas['POST /auth/2fa/reenviar'] = () => error2FA('Debes esperar antes de solicitar otro código.')
    vi.setSystemTime(Date.now() + 20000)
    expect(await rechazo(auth.reenviarCodigo())).toMatchObject({ codigo: 'DEMASIADO_PRONTO', msRestantes: 40000 })
  })

  it('BLOQUEADO', async () => {
    rutas['POST /auth/2fa/reenviar'] = () => error2FA('La verificación está bloqueada temporalmente.')
    expect((await rechazo(auth.reenviarCodigo())).codigo).toBe('BLOQUEADO')
  })
})

describe('cerrar sesión', () => {
  it('cancelarSolicitud descarta la solicitud y cierra la sesión local de Supabase', async () => {
    await entrar()
    await auth.cancelarSolicitud()
    expect(auth.estadoCodigo()).toBeNull()
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
  })

  it('cerrarSesionGlobal cierra en todos los equipos', async () => {
    await entrar()
    await auth.cerrarSesionGlobal('tesorero@pradofc.co')
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'global' })
    expect(auth.estadoCodigo()).toBeNull()
  })

  it('"Recordar este equipo" está pendiente en el backend: sin equipo de confianza', () => {
    expect(auth.estadoEquipo('tesorero@pradofc.co')).toBeNull()
    expect(() => auth.dejarDeRecordarEquipo('tesorero@pradofc.co')).not.toThrow()
  })
})

describe('restaurarSesion', () => {
  it('sin sesión de Supabase devuelve null y descarta la solicitud', async () => {
    supabase.auth.getSession.mockResolvedValue({ data: { session: null } })
    expect(await auth.restaurarSesion({ verificada: true })).toBeNull()
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  it('con sesión y 2FA completo en esta pestaña sigue verificada, sin llamar al backend', async () => {
    expect(await auth.restaurarSesion({ verificada: true })).toEqual({ estado: 'verificada' })
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  it('con sesión pero sin 2FA en esta pestaña vuelve al paso del código (pide el código)', async () => {
    const restaurada = await auth.restaurarSesion({ verificada: false })
    expect(restaurada.estado).toBe('pendiente')
    expect(restaurada.usuario.correo).toBe('tesorero@pradofc.co')
    expect(llamada('POST', '/auth/2fa/codigo')).toBeDefined()
    expect(auth.estadoCodigo().correoEnmascarado).toBe('t***@pradofc.co')
  })

  it('si ya hay una solicitud pendiente de esa cuenta no vuelve a pedir el código', async () => {
    await entrar()
    fetchSimulado.mockClear()
    const restaurada = await auth.restaurarSesion({ verificada: false })
    expect(restaurada).toMatchObject({ estado: 'pendiente', usuario: { rol: 'tesorero', nombre: 'Laura' } })
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  it('si no se puede pedir el código cierra la sesión y devuelve null', async () => {
    rutas['POST /auth/2fa/codigo'] = () => respuesta(503, { mensaje: 'No pudimos enviarte el código.' })
    expect(await auth.restaurarSesion({ verificada: false })).toBeNull()
    expect(supabase.auth.signOut).toHaveBeenCalledWith({ scope: 'local' })
  })
})

describe('alCerrarSesionExterna', () => {
  it('avisa solo en SIGNED_OUT y devuelve la baja', () => {
    const baja = vi.fn()
    supabase.auth.onAuthStateChange.mockReturnValue({ data: { subscription: { unsubscribe: baja } } })
    const aviso = vi.fn()
    const darDeBaja = auth.alCerrarSesionExterna(aviso)
    const escucha = supabase.auth.onAuthStateChange.mock.calls[0][0]
    escucha('TOKEN_REFRESHED')
    escucha('SIGNED_IN')
    expect(aviso).not.toHaveBeenCalled()
    escucha('SIGNED_OUT')
    expect(aviso).toHaveBeenCalledTimes(1)
    darDeBaja()
    expect(baja).toHaveBeenCalled()
  })
})
