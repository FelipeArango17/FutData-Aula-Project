import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Clave de módulo "inscripcion": encendida usa POST /api/inscripciones; apagada, el flujo en memoria de siempre.
// usarApiDe lee el MODE y VITE_API_MODULOS en cada llamada: se simulan con vi.stubEnv (en las pruebas MODE vale "test", que siempre apaga la API).
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn(), subirArchivo: vi.fn() }))
vi.mock('./apiClient.js', () => api)

// Los demás servicios (documentos, fotos, deportistas) leen la clave al cargarse: se importan de nuevo después de cada vi.stubEnv.
let inscribirJugador
let inscripcionPorApi
const cargar = async () => {
  vi.resetModules()
  ;({ inscribirJugador, inscripcionPorApi } = await import('./inscribirJugadorService.js'))
}

const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }
const hoy = new Date()
const jugador = { nombre: 'Lucas', apellido: 'Prueba', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento: `${hoy.getFullYear() - 9}-05-10`, telefono: '3000000099', direccion: 'Calle 50 # 10-20', grupoEntrenamiento: 'tarde', posicion: 'Portero' }
const acudiente = { nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia.ruiz@correo.com', telefono: '3001234567', parentesco: 'Tía' }
const respuesta = (extra = {}) => ({
  deportista: { id: 'uuid-1', nombre: 'Lucas', apellido: 'Prueba', tipoDocumento: 'TI', documento: '1200000001', fechaNacimiento: jugador.fechaNacimiento, categoria: 'Semillero', grupo: 'tarde', activo: true, estadoInscripcion: 'PENDIENTE' },
  acudiente: { id: 'uuid-2', nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia.ruiz@correo.com', reutilizado: false, cuentaCreada: true },
  parentesco: 'Tia',
  correoEnviado: true,
  ...extra,
})
const pdf = { name: 'doc.pdf', type: 'application/pdf', size: 1000 }
const png = { name: 'f.png', type: 'image/png', size: 1000 }

beforeEach(() => {
  for (const f of Object.values(api)) f.mockReset()
  vi.stubEnv('MODE', 'production')
})
afterEach(() => vi.unstubAllEnvs())

describe('con la clave "inscripcion" encendida', () => {
  beforeEach(async () => {
    vi.stubEnv('VITE_API_MODULOS', 'deportistas,documentos,inscripcion')
    await cargar()
  })

  it('un solo POST /inscripciones con el cuerpo del contrato (parentesco sin tilde) y devuelve la respuesta adaptada', async () => {
    api.apiRequest.mockResolvedValue(respuesta())
    const r = await inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin)
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe('/inscripciones')
    expect(opciones.method).toBe('POST')
    const cuerpo = JSON.parse(opciones.body)
    expect(cuerpo.acudiente).toEqual({ nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia.ruiz@correo.com', telefono: '3001234567', parentesco: 'Tia' })
    expect(cuerpo.deportista).toMatchObject({ tipoDocumento: 'TI', documento: '1200000001', grupo: 'tarde', posicion: 'Portero' })
    expect(r).toMatchObject({ categoria: 'Semillero', parentesco: 'Tía', correoEnviado: true, deportista: { id: 'uuid-1', estadoInscripcion: 'PENDIENTE' }, acudiente: { reutilizado: false } })
  })

  it.each([true, false, null])('conserva correoEnviado = %s', async (valor) => {
    api.apiRequest.mockResolvedValue(respuesta({ correoEnviado: valor }))
    const r = await inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin)
    expect(r.correoEnviado).toBe(valor)
  })

  it('después del éxito sube la foto y los documentos con el id del deportista devuelto', async () => {
    api.apiRequest.mockResolvedValue(respuesta())
    api.subirArchivo.mockResolvedValue({ foto: 'https://x/f' })
    const r = await inscribirJugador({ deportista: jugador, acudiente, documentos: [{ tipo: 'DOCUMENTO_IDENTIDAD', archivo: pdf }], foto: png }, admin)
    const rutas = api.subirArchivo.mock.calls.map(([ruta]) => ruta)
    expect(rutas).toEqual(['/deportistas/uuid-1/documentos', '/deportistas/uuid-1/foto'])
    expect(api.apiRequest.mock.calls[0][0]).toBe('/inscripciones') // primero la inscripción
    expect(r.sinSubir).toEqual({ documentos: [], foto: false })
  })

  it('si un archivo falla, la inscripción NO se pierde: se reporta qué falta subir', async () => {
    api.apiRequest.mockResolvedValue(respuesta())
    api.subirArchivo.mockImplementation(async (ruta) => {
      if (ruta.endsWith('/foto')) throw Object.assign(new Error('x'), { estado: 503 })
      throw Object.assign(new Error('x'), { estado: 500 })
    })
    const r = await inscribirJugador({ deportista: jugador, acudiente, documentos: [{ tipo: 'DOCUMENTO_IDENTIDAD', archivo: pdf }], foto: png }, admin)
    expect(r.deportista.id).toBe('uuid-1')
    expect(r.sinSubir.foto).toBe(true)
    expect(r.sinSubir.documentos).toHaveLength(1)
  })

  it('valida antes de llamar: con datos inválidos no hay petición', async () => {
    await expect(inscribirJugador({ deportista: { ...jugador, nombre: '' }, acudiente, documentos: [], foto: null }, admin)).rejects.toMatchObject({ codigo: 'VALIDACION', paso: 1 })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })

  it('un 400 con campos de los dos pasos: va al primero y trae todos', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 400, mensaje: 'deportista.documento: es obligatorio; acudiente.correo: no es válido', campos: ['deportista.documento', 'acudiente.correo'], codigo: null }))
    await expect(inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin)).rejects.toMatchObject({
      codigo: 'VALIDACION', paso: 1, errores: { documento: 'Es obligatorio.' }, erroresPorPaso: { 2: { correo: 'No es válido.' } },
    })
  })

  it('un 409 de correo va al paso 2; un 503 es un aviso amable; no se reintenta sola', async () => {
    api.apiRequest.mockRejectedValueOnce(Object.assign(new Error('x'), { estado: 409, mensaje: 'acudiente.correo: otro rol', campos: ['acudiente.correo'], codigo: null }))
    await expect(inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin)).rejects.toMatchObject({ codigo: 'CORREO_NO_DISPONIBLE', paso: 2 })
    api.apiRequest.mockReset()
    api.apiRequest.mockRejectedValue(Object.assign(new Error('SQL boom'), { estado: 503, mensaje: 'SQL boom', campos: [], codigo: null }))
    const e = await inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin).catch((x) => x)
    expect(e.codigo).toBe('NO_DISPONIBLE')
    expect(e.mensajeUsuario).not.toMatch(/SQL/)
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
  })

  it('una respuesta que no se entiende avisa que quizá quedó hecha, sin reintentar', async () => {
    api.apiRequest.mockResolvedValue({})
    const e = await inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin).catch((x) => x)
    expect(e.codigo).toBe('RESPUESTA_INVALIDA')
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
  })

  it('solo el administrador inscribe', async () => {
    await expect(inscribirJugador({ deportista: jugador, acudiente }, { rol: 'tesorero' })).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    expect(api.apiRequest).not.toHaveBeenCalled()
  })
})

describe('con la clave apagada (la de producción)', () => {
  it('no se llama a /inscripciones: sigue el flujo anterior (con la API de deportistas, el aviso de inscripción parcial)', async () => {
    vi.stubEnv('VITE_API_MODULOS', 'entrenadores,deportistas,documentos')
    await cargar()
    expect(inscripcionPorApi()).toBe(false)
    api.apiRequest.mockImplementation(async (ruta) => (ruta === '/categorias' ? [] : { id: 'uuid-1', nombre: 'Lucas', apellido: 'Prueba', tipoDocumento: 'TI', documento: '1200000001', fechaNacimiento: jugador.fechaNacimiento, activo: true }))
    const fallo = await inscribirJugador({ deportista: jugador, acudiente, documentos: [], foto: null }, admin).catch((e) => e)
    expect(fallo).toMatchObject({ codigo: 'INSCRIPCION_PARCIAL', causa: 'NO_DISPONIBLE' })
    expect(api.apiRequest.mock.calls.map(([ruta]) => ruta)).not.toContain('/inscripciones')
  })
  it('la clave "inscripcionpublica" no la enciende', async () => {
    vi.stubEnv('VITE_API_MODULOS', 'inscripcionpublica,deportistas')
    await cargar()
    expect(inscripcionPorApi()).toBe(false)
  })
  it('encendida, inscripcionPorApi es true', async () => {
    vi.stubEnv('VITE_API_MODULOS', 'inscripcion')
    await cargar()
    expect(inscripcionPorApi()).toBe(true)
  })
})
