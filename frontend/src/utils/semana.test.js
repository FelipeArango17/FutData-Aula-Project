import { describe, expect, it } from 'vitest'
import {
  MAX_MOTIVO, accionesDe, categoriasCreables, chocaEntrenadorEnSesiones, esFechaModificable, fechaConMes, puedeActuarSobre, puedeCrearExtra,
  semanaEnCurso, sesionesDeSemana, textoAviso, validarExtra, validarMotivo,
} from './semana.js'

const LUNES = '2026-10-19' // lunes; hoy de las pruebas = jueves 2026-10-22
const HOY = '2026-10-22'
const franja = (id, extra = {}) => ({
  id, idCategoria: 'c1', categoria: 'Sub-12', dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: 'Horizontes', idEntrenador: 'e1', entrenador: 'Luis Pérez', grupo: null, ...extra,
})
const extra = (id, e = {}) => ({
  id, tipo: 'EXTRA', fecha: '2026-10-21', motivo: 'Separar la cancha', idCategoria: 'c1', categoria: 'Sub-12', horaInicio: '16:00', horaFin: '17:00', sede: 'Horizontes',
  idEntrenador: 'e1', entrenador: 'Luis Pérez', grupo: null, ...e,
})

describe('sesionesDeSemana', () => {
  it('pone cada franja en la fecha de su día, de lunes a sábado', () => {
    const s = sesionesDeSemana([franja('a', { dia: 1 }), franja('b', { dia: 6, horaInicio: '09:00', horaFin: '10:00' })], [], LUNES)
    expect(s.map((x) => [x.idFranja, x.fecha, x.tipo, x.estado])).toEqual([['a', '2026-10-19', 'FRANJA', 'PROGRAMADO'], ['b', '2026-10-24', 'FRANJA', 'PROGRAMADO']])
    expect(s[0]).toMatchObject({ hora: '19:00', horaFin: '20:00', categoria: 'Sub-12', entrenador: 'Luis Pérez', idExcepcion: null, motivo: null })
  })

  it('una cancelación marca como CANCELADO solo esa franja en esa fecha, con su motivo', () => {
    const exc = [{ id: 9, tipo: 'CANCELACION', idFranja: 'a', fecha: '2026-10-19', motivo: 'Lluvia' }]
    const s = sesionesDeSemana([franja('a'), franja('b', { dia: 2 })], exc, LUNES)
    expect(s[0]).toMatchObject({ idFranja: 'a', estado: 'CANCELADO', motivo: 'Lluvia', idExcepcion: 9 })
    expect(s[1]).toMatchObject({ idFranja: 'b', estado: 'PROGRAMADO' })
    // la misma franja la semana siguiente no está cancelada
    expect(sesionesDeSemana([franja('a')], exc, '2026-10-26')[0].estado).toBe('PROGRAMADO')
  })

  it('los extras de la semana se agregan (aunque queden fuera de las franjas) y los de otra semana no', () => {
    const s = sesionesDeSemana([franja('a')], [extra(1), extra(2, { fecha: '2026-10-28' })], LUNES)
    expect(s.map((x) => x.clave)).toEqual(['f-a-2026-10-19', 'x-1']) // por fecha: lunes 19, miércoles 21
    expect(s[1]).toMatchObject({ tipo: 'EXTRA', idFranja: null, idExcepcion: 1, estado: 'PROGRAMADO', hora: '16:00', motivo: 'Separar la cancha' })
  })

  it('ordena por fecha, hora y categoría oficial; funciona igual con ids UUID', () => {
    const uuid = '0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b'
    const s = sesionesDeSemana(
      [franja(uuid, { categoria: 'Sub-16' }), franja('x', { categoria: 'Párvulos' }), franja('y', { dia: 1, horaInicio: '08:00', horaFin: '09:00' })],
      [{ id: uuid, tipo: 'CANCELACION', idFranja: uuid, fecha: LUNES, motivo: null }],
      LUNES,
    )
    expect(s.map((x) => x.idFranja)).toEqual(['y', 'x', uuid])
    expect(s[2].estado).toBe('CANCELADO')
  })
})

describe('permisos', () => {
  it('el administrador actúa sobre cualquier categoría; el entrenador solo sobre las suyas; los demás no', () => {
    expect(puedeActuarSobre('administrador', 'Sub-12')).toBe(true)
    expect(puedeActuarSobre('entrenador', 'Sub-12', ['Sub-12'])).toBe(true)
    expect(puedeActuarSobre('entrenador', 'Sub-13', ['Sub-12'])).toBe(false)
    expect(puedeActuarSobre('entrenador', 'Sub-12', null)).toBe(false) // no se sabe quién es: solo lectura
    expect(puedeActuarSobre('acudiente', 'Sub-12', ['Sub-12'])).toBe(false)
    expect(puedeActuarSobre('tesorero', 'Sub-12')).toBe(false)
  })

  it('categoriasCreables y puedeCrearExtra', () => {
    expect(categoriasCreables('administrador', null)).toHaveLength(9)
    expect(categoriasCreables('entrenador', ['Sub-13', 'Sub-14'])).toEqual(['Sub-13', 'Sub-14'])
    expect(categoriasCreables('entrenador', null)).toEqual([])
    expect(categoriasCreables('acudiente', ['Sub-12'])).toEqual([])
    expect(puedeCrearExtra('administrador', { extras: true }, null)).toBe(true)
    expect(puedeCrearExtra('administrador', { extras: false }, null)).toBe(false)
    expect(puedeCrearExtra('entrenador', { extras: true }, null)).toBe(false)
    expect(puedeCrearExtra('acudiente', { extras: true }, null)).toBe(false)
  })

  it('no se modifica el pasado (hoy sí)', () => {
    expect(esFechaModificable('2026-10-21', HOY)).toBe(false)
    expect(esFechaModificable(HOY, HOY)).toBe(true)
    expect(esFechaModificable('2026-10-23', HOY)).toBe(true)
  })
})

describe('accionesDe', () => {
  const todas = { cancelar: true, extras: true, rotarEntrenador: true }
  const sesion = (e = {}) => ({ tipo: 'FRANJA', estado: 'PROGRAMADO', idExcepcion: null, fecha: '2026-10-23', categoria: 'Sub-12', ...e })
  const ctx = (e = {}) => ({ rol: 'administrador', propias: null, capacidades: todas, hoy: HOY, ...e })

  it('administrador, franja futura: cancelar y cambiar entrenador; cancelada: reactivar', () => {
    expect(accionesDe(sesion(), ctx())).toMatchObject({ cancelar: true, reactivar: false, rotar: true, editarExtra: false, eliminarExtra: false })
    expect(accionesDe(sesion({ estado: 'CANCELADO', idExcepcion: 3 }), ctx())).toMatchObject({ cancelar: false, reactivar: true, rotar: true })
  })

  it('una franja pasada no se cancela ni se reactiva, pero el cambio de entrenador (todas las semanas) sigue disponible', () => {
    const pasada = sesion({ fecha: '2026-10-19' })
    expect(accionesDe(pasada, ctx())).toMatchObject({ cancelar: false, rotar: true })
    expect(accionesDe({ ...pasada, estado: 'CANCELADO', idExcepcion: 3 }, ctx()).reactivar).toBe(false)
  })

  it('el entrenador cancela solo sus categorías y nunca cambia el entrenador de una franja', () => {
    const propio = accionesDe(sesion(), ctx({ rol: 'entrenador', propias: ['Sub-12'] }))
    expect(propio).toMatchObject({ cancelar: true, rotar: false })
    expect(accionesDe(sesion({ categoria: 'Sub-13' }), ctx({ rol: 'entrenador', propias: ['Sub-12'] })).cancelar).toBe(false)
    expect(accionesDe(sesion(), ctx({ rol: 'entrenador', propias: null })).cancelar).toBe(false)
  })

  it('acudiente y tesorero no tienen ninguna acción', () => {
    for (const rol of ['acudiente', 'tesorero']) {
      expect(Object.values(accionesDe(sesion(), ctx({ rol, propias: ['Sub-12'] })))).toEqual([false, false, false, false, false])
    }
  })

  it('un extra se edita y se elimina (futuro y con permiso); una franja no', () => {
    const e = sesion({ tipo: 'EXTRA' })
    expect(accionesDe(e, ctx())).toMatchObject({ editarExtra: true, eliminarExtra: true, cancelar: false, rotar: false })
    expect(accionesDe({ ...e, fecha: '2026-10-20' }, ctx()).editarExtra).toBe(false)
    expect(accionesDe(e, ctx({ rol: 'entrenador', propias: ['Sub-13'] })).editarExtra).toBe(false)
  })

  it('sin la capacidad del servicio (modo API sin 4B) las acciones se ocultan; cambiar entrenador sigue', () => {
    const api = { cancelar: false, extras: false, rotarEntrenador: true }
    expect(accionesDe(sesion(), ctx({ capacidades: api }))).toEqual({ cancelar: false, reactivar: false, rotar: true, editarExtra: false, eliminarExtra: false })
    expect(accionesDe(sesion({ tipo: 'EXTRA' }), ctx({ capacidades: api })).editarExtra).toBe(false)
  })
})

describe('chocaEntrenadorEnSesiones', () => {
  const base = { clave: 'f-1', idEntrenador: 'e1', fecha: '2026-10-21', hora: '18:00', horaFin: '19:00', estado: 'PROGRAMADO' }
  const nueva = (e = {}) => ({ idEntrenador: 'e1', fecha: '2026-10-21', hora: '18:30', horaFin: '19:30', ...e })

  it('choca con una sesión del mismo entrenador que se cruza ese día (franja o extra)', () => {
    expect(chocaEntrenadorEnSesiones(nueva(), [base])).toBe(true)
    expect(chocaEntrenadorEnSesiones(nueva(), [{ ...base, clave: 'x-1' }])).toBe(true)
  })

  it('no choca si está cancelada, es contigua, es de otro día o de otro entrenador', () => {
    expect(chocaEntrenadorEnSesiones(nueva(), [{ ...base, estado: 'CANCELADO' }])).toBe(false)
    expect(chocaEntrenadorEnSesiones(nueva({ hora: '19:00', horaFin: '20:00' }), [base])).toBe(false)
    expect(chocaEntrenadorEnSesiones(nueva({ fecha: '2026-10-22' }), [base])).toBe(false)
    expect(chocaEntrenadorEnSesiones(nueva({ idEntrenador: 'e2' }), [base])).toBe(false)
  })

  it('"por turnos" no choca con nadie y la sesión que se edita no choca consigo misma', () => {
    expect(chocaEntrenadorEnSesiones(nueva({ idEntrenador: '' }), [base, { ...base, idEntrenador: null }])).toBe(false)
    expect(chocaEntrenadorEnSesiones(nueva(), [base], 'f-1')).toBe(false)
  })

  it('compara ids numéricos y de texto por igual', () => {
    expect(chocaEntrenadorEnSesiones(nueva({ idEntrenador: '3' }), [{ ...base, idEntrenador: 3 }])).toBe(true)
  })
})

describe('validarExtra', () => {
  const ok = { categoria: 'Sub-12', fecha: '2026-10-23', hora: '16:00', horaFin: '17:00', sede: 'Horizontes', idEntrenador: '1', grupo: '', motivo: '' }
  const ctx = { categorias: ['Sub-12'], entrenadores: [{ id: 1 }], hoy: HOY }

  it('acepta un extra válido (también fuera de las franjas) y por turnos', () => {
    expect(validarExtra(ok, ctx)).toEqual({})
    expect(validarExtra({ ...ok, idEntrenador: '' }, ctx)).toEqual({})
  })

  it('rechaza fechas pasadas, horas que no cuadran, sede, categoría y entrenador desconocidos', () => {
    const e = validarExtra({ ...ok, fecha: '2026-10-21', horaFin: '15:00', sede: 'Otra', categoria: 'Sub-13', idEntrenador: '9' }, ctx)
    expect(Object.keys(e).sort()).toEqual(['categoria', 'fecha', 'horaFin', 'idEntrenador', 'sede'])
  })

  it('el motivo admite hasta 200 caracteres', () => {
    expect(validarExtra({ ...ok, motivo: 'a'.repeat(MAX_MOTIVO) }, ctx).motivo).toBeUndefined()
    expect(validarExtra({ ...ok, motivo: 'a'.repeat(MAX_MOTIVO + 1) }, ctx).motivo).toBeTruthy()
    expect(validarMotivo('a'.repeat(MAX_MOTIVO + 1))).toBeTruthy()
    expect(validarMotivo('')).toBeNull()
  })
})

describe('textos', () => {
  it('fecha con mes y aviso a los acudientes (sin conteos)', () => {
    expect(fechaConMes('2026-11-02')).toBe('Lun 2 nov')
    expect(fechaConMes('2026-10-24')).toBe('Sáb 24 oct')
    expect(textoAviso('Sub-12')).toBe('Se avisará por correo a los acudientes de Sub-12.')
  })
})

describe('semanaEnCurso', () => {
  it('de lunes a sábado es el lunes de esa semana; el domingo, el lunes siguiente', () => {
    expect(semanaEnCurso('2026-11-07')).toBe('2026-11-02') // sábado
    expect(semanaEnCurso('2026-11-08')).toBe('2026-11-09') // domingo
    expect(semanaEnCurso('2026-11-09')).toBe('2026-11-09') // lunes
    expect(semanaEnCurso('2026-10-22')).toBe('2026-10-19') // jueves (HOY_EJEMPLO)
  })
})
