import { describe, expect, it } from 'vitest'
import { adaptarAccion, adaptarSemana, adaptarSesion, capacidadesApi, consultaSemana, cuerpoCancelacion, cuerpoExtra } from './semanaApi.js'

const CAT = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const dto = (extra = {}) => ({
  tipo: 'FRANJA', estado: 'ACTIVO', idFranja: 'f1', idExcepcion: null, fecha: '2026-11-03', dia: 2, horaInicio: '16:30', horaFin: '18:00',
  idCategoria: CAT, categoria: 'Semillero', sede: 'UVA El Paraíso', idEntrenador: null, entrenador: null, entrenadorActivo: null,
  grupo: 'tarde', motivo: null, puedeGestionar: false, ...extra,
})

describe('contrato de la semana', () => {
  it('capacidades: cancelar y extras sí; editar un extra no existe', () => {
    expect(capacidadesApi()).toEqual({ cancelar: true, extras: true, editarExtra: false })
  })

  it('consulta por el lunes', () => {
    expect(consultaSemana('2026-11-02')).toBe('/horario-semana?fecha=2026-11-02')
  })

  it('una franja activa: clave por franja y fecha, estado PROGRAMADO, por turnos', () => {
    expect(adaptarSesion(dto())).toMatchObject({
      clave: 'f-f1-2026-11-03', tipo: 'FRANJA', estado: 'PROGRAMADO', hora: '16:30', horaFin: '18:00', idEntrenador: null, entrenador: null, grupo: 'tarde', puedeGestionar: false,
    })
  })

  it('una franja cancelada conserva idExcepcion y motivo; un extra no tiene idFranja', () => {
    expect(adaptarSesion(dto({ estado: 'CANCELADO', idExcepcion: 'e1', motivo: 'Lluvia' }))).toMatchObject({ estado: 'CANCELADO', idExcepcion: 'e1', motivo: 'Lluvia' })
    expect(adaptarSesion(dto({ tipo: 'EXTRA', idFranja: null, idExcepcion: 'x1', puedeGestionar: true }))).toMatchObject({ clave: 'x-x1', tipo: 'EXTRA', idFranja: null, idExcepcion: 'x1', puedeGestionar: true })
  })

  it('entrenadorActivo solo importa si hay entrenador', () => {
    expect(adaptarSesion(dto({ idEntrenador: 'e', entrenador: 'Luis Pérez', entrenadorActivo: false })).entrenadorActivo).toBe(false)
    expect(adaptarSesion(dto()).entrenadorActivo).toBeNull()
  })

  it('una respuesta sin sesiones es una lista vacía', () => {
    expect(adaptarSemana({ desde: '2026-11-02', hasta: '2026-11-07', sesiones: [] })).toEqual([])
    expect(adaptarSemana(null)).toEqual([])
  })

  it('el aviso se conserva tal cual: true, false y null son tres casos distintos', () => {
    expect(adaptarAccion({ sesion: dto(), avisoEnviado: true }).avisoEnviado).toBe(true)
    expect(adaptarAccion({ sesion: dto(), avisoEnviado: false }).avisoEnviado).toBe(false)
    expect(adaptarAccion({ sesion: null, avisoEnviado: null })).toEqual({ sesion: null, avisoEnviado: null })
  })

  it('cuerpos: motivo vacío y por turnos van como null; las horas, HH:mm', () => {
    expect(cuerpoCancelacion({ fecha: '2026-11-03', idFranja: 'f1', motivo: ' ' })).toEqual({ fecha: '2026-11-03', idFranja: 'f1', motivo: null })
    expect(cuerpoExtra({ fecha: '2026-11-10', hora: '18:00:00', horaFin: '19:30', sede: 'Horizontes', idEntrenador: '', grupo: 'mañana', motivo: '' }, CAT)).toEqual({
      idCategoria: CAT, fecha: '2026-11-10', horaInicio: '18:00', horaFin: '19:30', sede: 'Horizontes', idEntrenador: null, grupo: 'mañana', motivo: null,
    })
  })
})
