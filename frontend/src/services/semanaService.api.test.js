import { beforeEach, describe, expect, it, vi } from 'vitest'

// Modo API con solo "horarioclub": el cliente HTTP está simulado (nunca hay red). El módulo "entrenamientos" (4B) no está.
const api = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./apiClient.js', () => api)
vi.mock('./usarApi.js', () => ({ usarApiDe: (modulo) => modulo === 'horarioclub' }))

import * as servicio from './semanaService.js'

const CAT = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const ENT = 'e0a1e0a1-0000-4000-8000-000000000001'
const dto = (id, extra = {}) => ({
  idFranja: id, idCategoria: CAT, categoria: 'Sub-12', dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes',
  idEntrenador: ENT, entrenador: 'Luis Pérez', entrenadorActivo: true, grupo: null, ...extra,
})
const fallo = (promesa) => promesa.then(() => { throw new Error('Debía rechazar') }, (e) => ({ ...e, message: e.message }))

beforeEach(() => {
  api.apiRequest.mockReset()
})

describe('capacidades y permisos en modo API', () => {
  it('lee y cambia el entrenador de una franja; cancelar y extras están ocultos (falta el contrato 4B)', () => {
    expect(servicio.semanaPorApi()).toBe(true)
    expect(servicio.capacidades()).toEqual({ leer: true, cancelar: false, extras: false, rotarEntrenador: true })
  })

  it('el entrenador con sesión no se puede identificar: sin categorías propias (solo lectura)', () => {
    expect(servicio.identidadEntrenador()).toBeNull()
  })

  it('cancelar y los extras no llaman a la API: avisan que no están disponibles', async () => {
    for (const llamar of [
      () => servicio.cancelar({ fecha: '2026-11-03', idFranja: 'a' }), () => servicio.reactivar('x'), () => servicio.crearExtra({}),
      () => servicio.editarExtra('x', {}), () => servicio.eliminarExtra('x'),
    ]) {
      expect(await fallo(llamar())).toMatchObject({ codigo: 'NO_DISPONIBLE' })
    }
    expect(api.apiRequest).not.toHaveBeenCalled()
  })
})

describe('listarSemana en modo API', () => {
  it('arma la semana con GET /horario-club, sin excepciones, con los nombres que trae la franja', async () => {
    api.apiRequest.mockResolvedValue([dto('a'), dto('b', { dia: 2, idEntrenador: null, entrenador: null, entrenadorActivo: null })])
    const sesiones = await servicio.listarSemana('2026-11-02')
    expect(api.apiRequest).toHaveBeenCalledTimes(1)
    expect(api.apiRequest).toHaveBeenCalledWith('/horario-club')
    expect(sesiones.map((s) => [s.idFranja, s.fecha, s.estado, s.entrenador])).toEqual([['a', '2026-11-02', 'PROGRAMADO', 'Luis Pérez'], ['b', '2026-11-03', 'PROGRAMADO', null]])
  })

  it('propaga el error de la API', async () => {
    api.apiRequest.mockRejectedValue(Object.assign(new Error('x'), { estado: 403, mensaje: 'No tienes permiso.' }))
    expect(await fallo(servicio.listarSemana('2026-11-02'))).toMatchObject({ estado: 403 })
  })
})

describe('cambiarEntrenadorDeFranja en modo API', () => {
  const NUEVO = 'e0a1e0a1-0000-4000-8000-000000000002'

  it('hace PUT con los siete campos tal como están y cambia solo idEntrenador', async () => {
    api.apiRequest.mockImplementation(async (ruta, opciones) => (opciones?.method === 'PUT' ? dto('a', { idEntrenador: NUEVO }) : [dto('a', { grupo: 'tarde' })]))
    await servicio.cambiarEntrenadorDeFranja('a', NUEVO)
    const [ruta, opciones] = api.apiRequest.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(ruta).toBe('/horario-club/a')
    expect(JSON.parse(opciones.body)).toEqual({ idCategoria: CAT, dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: NUEVO, grupo: 'tarde' })
  })

  it('"por turnos" manda idEntrenador null', async () => {
    api.apiRequest.mockImplementation(async (ruta, opciones) => (opciones?.method === 'PUT' ? dto('a', { idEntrenador: null }) : [dto('a')]))
    await servicio.cambiarEntrenadorDeFranja('a', null)
    const [, opciones] = api.apiRequest.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(JSON.parse(opciones.body).idEntrenador).toBeNull()
  })

  it('el 409 de cruce llega con el mensaje del backend', async () => {
    const mensaje = 'Ese entrenador ya tiene otra franja que se cruza con este horario'
    api.apiRequest.mockImplementation(async (ruta, opciones) => {
      if (opciones?.method === 'PUT') throw Object.assign(new Error(mensaje), { estado: 409, mensaje, campos: ['idEntrenador'] })
      return [dto('a')]
    })
    expect(await fallo(servicio.cambiarEntrenadorDeFranja('a', NUEVO))).toMatchObject({ estado: 409, mensaje, errores: { idEntrenador: mensaje } })
  })

  it('una franja que ya no existe responde NO_EXISTE sin hacer PUT', async () => {
    api.apiRequest.mockResolvedValue([dto('a')])
    expect(await fallo(servicio.cambiarEntrenadorDeFranja('zzz', NUEVO))).toMatchObject({ codigo: 'NO_EXISTE', estado: 404 })
    expect(api.apiRequest.mock.calls.every(([, o]) => o?.method !== 'PUT')).toBe(true)
  })
})
