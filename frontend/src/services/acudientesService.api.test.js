import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Módulo "acudientes" con la API activa. Sin red y sin depender de .env.local: el cliente HTTP está simulado y el interruptor sale de vi.stubEnv.
const api = vi.hoisted(() => ({ apiRequest: vi.fn(), apiListaPaginada: vi.fn() }))
vi.mock('./apiClient.js', () => api)

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const jugadorDto = (extra = {}) => ({
  id: ID, nombre: 'Santiago', apellido: 'Ríos', fechaNacimiento: '2018-03-14', edad: 8, categoria: 'Semillero', idCategoria: 'c1', grupo: 'mañana',
  posicion: null, foto: null, parentesco: 'Tio', activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})

let servicio
async function cargar(modulos) {
  vi.resetModules()
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', modulos)
  servicio = await import('./acudientesService.js')
}

beforeEach(() => {
  for (const f of Object.values(api)) f.mockReset()
})
afterEach(() => vi.unstubAllEnvs())

describe('interruptor', () => {
  it('solo está encendido con "acudientes" en VITE_API_MODULOS', async () => {
    await cargar('acudientes')
    expect(servicio.acudientesPorApi()).toBe(true)
    await cargar('deportistas,documentos')
    expect(servicio.acudientesPorApi()).toBe(false)
  })
})

describe('listarMisJugadores', () => {
  it('pide la lista del acudiente con sesión y adapta el DTO', async () => {
    await cargar('acudientes')
    api.apiRequest.mockResolvedValue([jugadorDto(), jugadorDto({ id: 'otro', categoria: null, grupo: null, parentesco: null, estadoInscripcion: 'COMPLETA' })])
    const lista = await servicio.listarMisJugadores()
    expect(api.apiRequest).toHaveBeenCalledWith('/acudientes/me/deportistas')
    expect(lista).toHaveLength(2)
    expect(lista[0]).toMatchObject({ id: ID, parentesco: 'Tío', grupoEntrenamiento: 'mañana', estadoInscripcion: 'PENDIENTE' })
    expect(lista[1]).toMatchObject({ categoria: null, parentesco: null, estadoInscripcion: 'COMPLETA' })
  })

  it('una lista vacía es una lista vacía', async () => {
    await cargar('acudientes')
    api.apiRequest.mockResolvedValue([])
    expect(await servicio.listarMisJugadores()).toEqual([])
  })

  it('los errores del servidor llegan tal cual (estado y mensaje)', async () => {
    await cargar('acudientes')
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 403, mensaje: 'Sin permiso.' }))
    await expect(servicio.listarMisJugadores()).rejects.toMatchObject({ estado: 403, mensaje: 'Sin permiso.' })
  })
})

describe('obtenerMiJugador', () => {
  it('pide la ficha por id (UUID como texto) y trae los documentos que faltan', async () => {
    await cargar('acudientes')
    api.apiRequest.mockResolvedValue({
      ...jugadorDto(), tipoDocumento: 'RC', documento: '123', telefono: '300', direccion: 'Calle 1', documentosFaltantes: ['DOCUMENTO_IDENTIDAD'],
    })
    const f = await servicio.obtenerMiJugador(ID)
    expect(api.apiRequest).toHaveBeenCalledWith(`/acudientes/me/deportistas/${ID}`)
    expect(f).toMatchObject({ id: ID, tipoDocumento: 'Registro civil', documento: '123' })
    expect(f.documentosFaltantes).toEqual([{ tipo: 'DOCUMENTO_IDENTIDAD', nombre: 'Documento de identidad' }])
  })

  it('un 404 (ajeno o inexistente) se propaga con el mensaje del servidor', async () => {
    await cargar('acudientes')
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 404, mensaje: 'No existe un deportista con ese id.' }))
    await expect(servicio.obtenerMiJugador(ID)).rejects.toMatchObject({ estado: 404, mensaje: 'No existe un deportista con ese id.' })
  })
})
