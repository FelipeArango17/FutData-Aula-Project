import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API: el módulo "asistencia" sale de vi.stubEnv (no depende de .env.local) y el cliente HTTP está simulado (nunca hay red).
const api = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./apiClient.js', () => api)

let servicio
const FRANJA = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const D1 = 'd0000000-0000-4000-8000-000000000001'
const respuesta = (estado = null) => ({
  sesion: { fecha: '2026-11-03', horaInicio: '19:00', horaFin: '20:00', idCategoria: 'c', categoria: 'Sub-12', sede: 'Horizontes', grupo: null, idEntrenador: 'e', entrenador: 'Luis' },
  puedeRegistrar: true,
  deportistas: [{ idDeportista: D1, nombre: 'Ana', apellido: 'Rojas', estado, observaciones: null }],
})

async function cargar(modulos) {
  vi.stubEnv('MODE', 'development')
  vi.stubEnv('VITE_API_MODULOS', modulos)
  api.apiRequest.mockReset()
  vi.resetModules()
  servicio = await import('./asistenciaService.js')
}

afterEach(() => vi.unstubAllEnvs())

describe('con el módulo "asistencia" apagado', () => {
  beforeEach(() => cargar('horarioclub'))
  it('no usa la API', () => {
    expect(servicio.asistenciaPorApi()).toBe(false)
  })
})

describe('con el módulo "asistencia" encendido', () => {
  beforeEach(() => cargar('asistencia'))

  it('se activa por la clave del módulo', () => {
    expect(servicio.asistenciaPorApi()).toBe(true)
  })

  it('la semana se pide al horario del club sin depender de su módulo', async () => {
    api.apiRequest.mockResolvedValue({ sesiones: [{ tipo: 'FRANJA', estado: 'ACTIVO', idFranja: FRANJA, fecha: '2026-11-03', horaInicio: '19:00', horaFin: '20:00', puedeGestionar: true }] })
    const sesiones = await servicio.listarSemanaDeAsistencia('2026-11-02')
    expect(api.apiRequest).toHaveBeenCalledWith('/horario-semana?fecha=2026-11-02')
    expect(sesiones[0]).toMatchObject({ idFranja: FRANJA, puedeGestionar: true, hora: '19:00' })
  })

  it('obtenerSesionApi hace GET con fecha e idFranja y adapta la respuesta', async () => {
    api.apiRequest.mockResolvedValue(respuesta('PRESENTE'))
    const datos = await servicio.obtenerSesionApi({ fecha: '2026-11-03', idFranja: FRANJA })
    expect(api.apiRequest).toHaveBeenCalledWith(`/asistencia/sesion?fecha=2026-11-03&idFranja=${FRANJA}`)
    expect(datos.deportistas[0]).toMatchObject({ id: D1, estado: 'presente', observacion: '' })
  })

  it('guardarSesionApi hace PUT con el cuerpo del contrato y devuelve la sesión guardada', async () => {
    api.apiRequest.mockResolvedValue(respuesta('AUSENTE'))
    const datos = await servicio.guardarSesionApi({ fecha: '2026-11-03', idFranja: FRANJA }, [{ idDeportista: D1, estado: 'ausente', observacion: 'Gripa' }])
    const [ruta, opciones] = api.apiRequest.mock.calls[0]
    expect(ruta).toBe('/asistencia/sesion')
    expect(opciones.method).toBe('PUT')
    expect(JSON.parse(opciones.body)).toEqual({ fecha: '2026-11-03', idFranja: FRANJA, registros: [{ idDeportista: D1, estado: 'AUSENTE', observaciones: 'Gripa' }] })
    expect(datos.deportistas[0].estado).toBe('ausente')
  })

  it('los errores de la API llegan tal cual para que la pantalla los explique', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 409, mensaje: 'Cancelada', campos: ['fecha'] }))
    await expect(servicio.guardarSesionApi({ fecha: '2026-11-03', idFranja: FRANJA }, [])).rejects.toMatchObject({ estado: 409 })
  })
})
