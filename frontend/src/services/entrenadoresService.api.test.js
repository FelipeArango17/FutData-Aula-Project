import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio contra la API real, con fetch y Supabase simulados. MODE se cambia a 'development' ANTES de cargar el módulo
// (usarApi se decide al cargarlo). Nada de esto toca el backend.
const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const supabase = { auth: { getSession: vi.fn(), signOut: vi.fn() } }

const respuesta = (estado, cuerpo) =>
  new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } })
const entrenadorApi = (extra = {}) => ({
  id: ID, nombre: 'Luis', apellido: 'Pérez', tipoDocumento: 'CC', documento: '1098765432', fechaNacimiento: '1990-05-14',
  telefono: '3001112233', correo: 'luis.perez@pradofc.co', especialidad: 'Porteros', categorias: [],
  horarioTrabajo: null, direccion: null, activo: true, ...extra,
})
const formulario = (extra = {}) => ({
  nombre: ' Luis ', apellido: 'Pérez', tipoDocumento: 'CC', documento: '1098765432', fechaNacimiento: '1990-05-14',
  telefono: '3001112233', correo: 'luis.perez@pradofc.co', especialidad: 'Porteros', categorias: ['Sub-12', 'Cachorros'],
  horarioTrabajo: '', direccion: '', ...extra,
})

let servicio
let fetchSimulado
let rutas
const llamadas = () => fetchSimulado.mock.calls.map(([url, o]) => ({ ruta: url.replace(/^.*\/api/, ''), metodo: o.method ?? 'GET', cuerpo: o.body ? JSON.parse(o.body) : undefined, headers: o.headers }))

beforeEach(async () => {
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', 'entrenadores') // hermética: no depende del .env.local
  supabase.auth.getSession.mockReset().mockResolvedValue({ data: { session: { access_token: 'token-de-prueba' } } })
  supabase.auth.signOut.mockReset().mockResolvedValue({ error: null })
  rutas = {}
  fetchSimulado = vi.fn(async (url, opciones) => {
    const clave = `${opciones.method ?? 'GET'} ${url.replace(/^.*\/api/, '')}`
    if (!rutas[clave]) throw new Error(`Llamada no prevista: ${clave}`)
    return rutas[clave]()
  })
  vi.stubGlobal('fetch', fetchSimulado)
  vi.resetModules()
  vi.doMock('./supabaseClient.js', () => ({ supabase, autenticacionReal: true }))
  servicio = await import('./entrenadoresService.js')
})
afterEach(() => vi.unstubAllEnvs())

const rechazo = (promesa) => promesa.then(() => null, (error) => error)

describe('lectura', () => {
  it('listar convierte los null de la API en texto vacío y siempre trae activo y categorías', async () => {
    rutas['GET /entrenadores'] = () => respuesta(200, [entrenadorApi(), entrenadorApi({ id: 'otro', activo: false, categorias: ['Cachorros'] })])
    const [a, b] = await servicio.listar()
    expect(a).toMatchObject({ id: ID, horarioTrabajo: '', direccion: '', categorias: [], activo: true })
    expect(b).toMatchObject({ activo: false, categorias: ['Cachorros'] })
    expect(llamadas()[0].headers.Authorization).toBe('Bearer token-de-prueba')
  })

  it('obtener usa el id UUID como texto en la ruta y devuelve null si no existe', async () => {
    rutas[`GET /entrenadores/${ID}`] = () => respuesta(200, entrenadorApi())
    expect((await servicio.obtener(ID)).id).toBe(ID)
    rutas['GET /entrenadores/no-existe'] = () => respuesta(404, { mensaje: 'No existe un entrenador con ese id.' })
    expect(await servicio.obtener('no-existe')).toBeNull()
  })

  it('obtener deja subir los demás errores (403)', async () => {
    rutas[`GET /entrenadores/${ID}`] = () => respuesta(403, { mensaje: 'No tienes permiso para hacer esta acción.' })
    expect(await rechazo(servicio.obtener(ID))).toMatchObject({ estado: 403, codigo: 'PERMISOS' })
  })

  it('listarCategorias conserva entrenador: null', async () => {
    rutas['GET /categorias'] = () => respuesta(200, [
      { id: 'c1', nombre: 'Cachorros', edadMinima: 0, edadMaxima: 7, entrenador: null },
      { id: 'c2', nombre: 'Sub-12', edadMinima: 10, edadMaxima: 11, entrenador: { id: ID, nombre: 'Nicolás', apellido: 'Ejemplo' } },
    ])
    const categorias = await servicio.listarCategorias()
    expect(categorias[0].entrenador).toBeNull()
    expect(categorias[1].entrenador).toEqual({ id: ID, nombre: 'Nicolás', apellido: 'Ejemplo' })
  })

  it('el duplicado lo decide el backend: detectarDuplicado no consulta nada', async () => {
    expect(await servicio.detectarDuplicado(formulario())).toBeNull()
    expect(fetchSimulado).not.toHaveBeenCalled()
  })
})

describe('registrar y editar', () => {
  it('crearDatos envía solo los datos personales, limpios y con el tipo de documento como código', async () => {
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi())
    const creado = await servicio.crearDatos(formulario({ tipoDocumento: 'PASAPORTE', documento: 'ab123456', id: 'x', activo: false }))
    const [llamada] = llamadas()
    expect(llamada.cuerpo).toEqual({
      nombre: 'Luis', apellido: 'Pérez', tipoDocumento: 'PASAPORTE', documento: 'ab123456', fechaNacimiento: '1990-05-14',
      telefono: '3001112233', correo: 'luis.perez@pradofc.co', especialidad: 'Porteros', horarioTrabajo: '', direccion: '',
    })
    expect(creado.id).toBe(ID)
  })

  it('editarDatos hace PUT al id, sin categorías', async () => {
    rutas[`PUT /entrenadores/${ID}`] = () => respuesta(200, entrenadorApi({ nombre: 'Luis Felipe' }))
    await servicio.editarDatos(ID, formulario())
    expect(llamadas()[0]).toMatchObject({ metodo: 'PUT', ruta: `/entrenadores/${ID}` })
    expect(llamadas()[0].cuerpo).not.toHaveProperty('categorias')
  })

  it('crear = POST y luego PUT /categorias en el orden oficial, sin reasignar', async () => {
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi())
    rutas[`PUT /entrenadores/${ID}/categorias`] = () => respuesta(200, entrenadorApi({ categorias: ['Cachorros', 'Sub-12'] }))
    const creado = await servicio.crear(formulario())
    expect(llamadas().map((l) => `${l.metodo} ${l.ruta}`)).toEqual(['POST /entrenadores', `PUT /entrenadores/${ID}/categorias`])
    expect(llamadas()[1].cuerpo).toEqual({ categorias: ['Cachorros', 'Sub-12'], reasignar: false })
    expect(creado.categorias).toEqual(['Cachorros', 'Sub-12'])
  })

  it('crear sin categorías no llama a /categorias', async () => {
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi())
    await servicio.crear(formulario({ categorias: [] }))
    expect(llamadas()).toHaveLength(1)
  })

  it('si falla la asignación, el entrenador queda creado y el error trae `parcial`', async () => {
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi())
    rutas[`PUT /entrenadores/${ID}/categorias`] = () => respuesta(409, { mensaje: 'Cachorros ya tiene otro entrenador.', campos: ['categorias'] })
    const error = await rechazo(servicio.crear(formulario()))
    expect(error).toMatchObject({ estado: 409, campos: ['categorias'] })
    expect(error.parcial.id).toBe(ID)
    expect(llamadas().filter((l) => l.metodo === 'POST')).toHaveLength(1)
  })

  it('los errores de campo del backend suben con estado, mensaje y campos', async () => {
    rutas['POST /entrenadores'] = () => respuesta(409, { mensaje: 'documento: ya está registrado en otro entrenador', campos: ['documento'] })
    expect(await rechazo(servicio.crearDatos(formulario()))).toMatchObject({
      estado: 409, mensaje: 'documento: ya está registrado en otro entrenador', campos: ['documento'],
    })
  })
})

describe('categorías y estado', () => {
  it('asignarCategorias envía la lista completa y reasignar', async () => {
    rutas[`PUT /entrenadores/${ID}/categorias`] = () => respuesta(200, entrenadorApi({ categorias: ['Cachorros'] }))
    await servicio.asignarCategorias(ID, ['Cachorros'], true)
    expect(llamadas()[0].cuerpo).toEqual({ categorias: ['Cachorros'], reasignar: true })
    await servicio.asignarCategorias(ID, [])
    expect(llamadas()[1].cuerpo).toEqual({ categorias: [], reasignar: false })
  })

  it('una categoría de otro entrenador responde 409 con campos [categorias]', async () => {
    rutas[`PUT /entrenadores/${ID}/categorias`] = () => respuesta(409, { mensaje: 'Cachorros ya tiene otro entrenador. Confirma la reasignación.', campos: ['categorias'] })
    expect(await rechazo(servicio.asignarCategorias(ID, ['Cachorros']))).toMatchObject({ estado: 409, campos: ['categorias'] })
  })

  it('cambiarEstado hace PATCH /estado con { activo }', async () => {
    rutas[`PATCH /entrenadores/${ID}/estado`] = () => respuesta(200, entrenadorApi({ activo: false }))
    const resultado = await servicio.cambiarEstado(ID, false)
    expect(llamadas()[0]).toMatchObject({ metodo: 'PATCH', ruta: `/entrenadores/${ID}/estado`, cuerpo: { activo: false } })
    expect(resultado.activo).toBe(false)
  })
})

describe('alta con enlace por correo', () => {
  it('crearDatos pasa correoEnviado true y false; si el backend no lo manda, no se da por enviado', async () => {
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi({ correoEnviado: true }))
    expect((await servicio.crearDatos(formulario())).correoEnviado).toBe(true)
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi({ correoEnviado: false }))
    expect((await servicio.crearDatos(formulario())).correoEnviado).toBe(false)
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi())
    expect((await servicio.crearDatos(formulario())).correoEnviado).toBe(false)
  })

  it('crear (datos + categorías) conserva correoEnviado', async () => {
    rutas['POST /entrenadores'] = () => respuesta(201, entrenadorApi({ correoEnviado: true }))
    rutas[`PUT /entrenadores/${ID}/categorias`] = () => respuesta(200, entrenadorApi({ categorias: ['Cachorros'] }))
    expect((await servicio.crear(formulario())).correoEnviado).toBe(true)
  })

  it('crearContrasena: POST público con { token, contrasena }, sin Authorization ni consultar la sesión', async () => {
    rutas['POST /auth/crear-contrasena'] = () => respuesta(204)
    await servicio.crearContrasena('tok-123', 'Clave2026x')
    const [llamada] = llamadas()
    expect(llamada).toMatchObject({ metodo: 'POST', ruta: '/auth/crear-contrasena', cuerpo: { token: 'tok-123', contrasena: 'Clave2026x' } })
    expect(llamada.headers.Authorization).toBeUndefined()
    expect(supabase.auth.getSession).not.toHaveBeenCalled()
  })

  it('crearContrasena: un 400 sube con estado 400 y un 401 no cierra la sesión', async () => {
    rutas['POST /auth/crear-contrasena'] = () => respuesta(400, { mensaje: 'Token inválido.' })
    expect(await rechazo(servicio.crearContrasena('x', 'Clave2026x'))).toMatchObject({ estado: 400 })
    rutas['POST /auth/crear-contrasena'] = () => respuesta(401, { mensaje: 'No autorizado.' })
    expect(await rechazo(servicio.crearContrasena('x', 'Clave2026x'))).toMatchObject({ estado: 401 })
    expect(supabase.auth.signOut).not.toHaveBeenCalled()
  })

  it('reenviarEnlace: POST /entrenadores/{id}/reenviar-enlace con el token del administrador', async () => {
    rutas[`POST /entrenadores/${ID}/reenviar-enlace`] = () => respuesta(204)
    await servicio.reenviarEnlace(ID)
    expect(llamadas()[0]).toMatchObject({ metodo: 'POST', ruta: `/entrenadores/${ID}/reenviar-enlace` })
    expect(llamadas()[0].headers.Authorization).toBe('Bearer token-de-prueba')
  })

  it('reenviarEnlace: el 409 sube con el mensaje del backend', async () => {
    rutas[`POST /entrenadores/${ID}/reenviar-enlace`] = () => respuesta(409, { mensaje: 'El entrenador ya creó su contraseña.' })
    expect(await rechazo(servicio.reenviarEnlace(ID))).toMatchObject({ estado: 409, mensaje: 'El entrenador ya creó su contraseña.' })
  })
})
