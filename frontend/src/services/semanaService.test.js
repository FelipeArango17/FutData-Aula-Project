import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FRANJAS_EJEMPLO } from './horarioClubData.js'

// Modo ejemplo: todo en memoria. El estado es del módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./semanaService.js')
})
afterEach(() => vi.useRealTimers())

const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

const LUNES = '2026-10-19' // semana de HOY_EJEMPLO (jueves 2026-10-22)
const datosExtra = (extra = {}) => ({ categoria: 'Sub-12', fecha: '2026-10-23', hora: '16:00', horaFin: '17:00', sede: 'Horizontes', idEntrenador: '1', grupo: '', motivo: '', ...extra })

describe('modo ejemplo', () => {
  it('no usa la API y ofrece todas las capacidades', () => {
    expect(servicio.semanaPorApi()).toBe(false)
    expect(servicio.capacidades()).toEqual({ leer: true, cancelar: true, extras: true, rotarEntrenador: true })
  })

  it('el entrenador de ejemplo se conoce con sus categorías (Nicolás, Sub-12)', () => {
    expect(servicio.identidadEntrenador()).toEqual({ id: 1, categorias: ['Sub-12'] })
  })

  it('listarSemana arma las 30 franjas de la semana con nombres de categoría y entrenador', async () => {
    const sesiones = await resolver(servicio.listarSemana(LUNES))
    expect(sesiones).toHaveLength(FRANJAS_EJEMPLO.length)
    const sub12 = sesiones.find((s) => s.categoria === 'Sub-12' && s.fecha === '2026-10-19')
    expect(sub12).toMatchObject({ tipo: 'FRANJA', estado: 'PROGRAMADO', entrenador: 'Nicolás', hora: '19:00' })
    expect(sesiones.find((s) => s.categoria === 'Párvulos').entrenador).toBeNull()
  })

  it('cancelar marca la sesión de esa fecha (con motivo) y reactivar la deja como estaba', async () => {
    const antes = (await resolver(servicio.listarSemana(LUNES))).find((s) => s.categoria === 'Sub-13' && s.fecha === '2026-10-23') // viernes, futuro
    const c = await resolver(servicio.cancelar({ fecha: antes.fecha, idFranja: antes.idFranja, motivo: ' Lluvia ' }))
    const cancelada = (await resolver(servicio.listarSemana(LUNES))).find((s) => s.clave === antes.clave)
    expect(cancelada).toMatchObject({ estado: 'CANCELADO', motivo: 'Lluvia', idExcepcion: c.id })
    // la misma franja otra semana sigue programada
    expect((await resolver(servicio.listarSemana('2026-10-26'))).find((s) => s.idFranja === antes.idFranja).estado).toBe('PROGRAMADO')
    await resolver(servicio.reactivar(c.id))
    expect((await resolver(servicio.listarSemana(LUNES))).find((s) => s.clave === antes.clave).estado).toBe('PROGRAMADO')
  })

  it('no se cancela una fecha pasada, ni dos veces, ni con un motivo de más de 200 caracteres', async () => {
    const [primera] = await resolver(servicio.listarSemana(LUNES)) // lunes 19: pasado
    await expect(resolver(servicio.cancelar({ fecha: primera.fecha, idFranja: primera.idFranja }))).rejects.toMatchObject({ codigo: 'VALIDACION' })
    const futura = (await resolver(servicio.listarSemana(LUNES))).find((s) => s.fecha === '2026-10-23')
    await expect(resolver(servicio.cancelar({ fecha: futura.fecha, idFranja: futura.idFranja, motivo: 'a'.repeat(201) }))).rejects.toMatchObject({ errores: { motivo: expect.any(String) } })
    await resolver(servicio.cancelar({ fecha: futura.fecha, idFranja: futura.idFranja }))
    await expect(resolver(servicio.cancelar({ fecha: futura.fecha, idFranja: futura.idFranja }))).rejects.toMatchObject({ codigo: 'CONFLICTO' })
  })

  it('un extra se crea (fuera de las franjas), se edita y se elimina', async () => {
    const creado = await resolver(servicio.crearExtra(datosExtra({ hora: '06:00', horaFin: '07:00', motivo: 'Separar la cancha' })))
    let sesiones = await resolver(servicio.listarSemana(LUNES))
    expect(sesiones.find((s) => s.clave === `x-${creado.id}`)).toMatchObject({ tipo: 'EXTRA', hora: '06:00', motivo: 'Separar la cancha', entrenador: 'Nicolás' })
    await resolver(servicio.editarExtra(creado.id, datosExtra({ hora: '06:30', horaFin: '07:30' })))
    sesiones = await resolver(servicio.listarSemana(LUNES))
    expect(sesiones.find((s) => s.clave === `x-${creado.id}`).hora).toBe('06:30')
    await resolver(servicio.eliminarExtra(creado.id))
    expect((await resolver(servicio.listarSemana(LUNES))).some((s) => s.tipo === 'EXTRA')).toBe(false)
    await expect(resolver(servicio.eliminarExtra(creado.id))).rejects.toMatchObject({ codigo: 'NO_EXISTE' })
  })

  it('un extra valida los datos y rechaza el choque con otra sesión del mismo entrenador', async () => {
    await expect(resolver(servicio.crearExtra(datosExtra({ fecha: '2026-10-20' })))).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { fecha: expect.any(String) } })
    // Nicolás (1) tiene Sub-12 los lunes 19:00 y martes 18:00 (22 y 23 de octubre: jueves 19:00)
    await expect(resolver(servicio.crearExtra(datosExtra({ fecha: '2026-10-22', hora: '19:30', horaFin: '20:30' })))).rejects.toMatchObject({ codigo: 'CONFLICTO', errores: { idEntrenador: expect.any(String) } })
    // contiguo: no choca
    await resolver(servicio.crearExtra(datosExtra({ fecha: '2026-10-22', hora: '20:00', horaFin: '21:00' })))
  })

  it('una sesión cancelada ya no cuenta para el choque', async () => {
    const jueves = (await resolver(servicio.listarSemana(LUNES))).find((s) => s.categoria === 'Sub-12' && s.fecha === '2026-10-22')
    await resolver(servicio.cancelar({ fecha: jueves.fecha, idFranja: jueves.idFranja }))
    await resolver(servicio.crearExtra(datosExtra({ fecha: '2026-10-22', hora: '19:30', horaFin: '20:30' })))
  })

  it('cambiarEntrenadorDeFranja cambia la franja para todas las semanas', async () => {
    const sub12 = (await resolver(servicio.listarSemana(LUNES))).find((s) => s.categoria === 'Sub-12' && s.fecha === '2026-10-19')
    await resolver(servicio.cambiarEntrenadorDeFranja(sub12.idFranja, 2)) // David Mesa, libre el lunes a las 19:00
    for (const lunes of [LUNES, '2026-10-26']) {
      expect((await resolver(servicio.listarSemana(lunes))).find((s) => s.idFranja === sub12.idFranja)).toMatchObject({ idEntrenador: 2, entrenador: 'David Mesa' })
    }
    await resolver(servicio.cambiarEntrenadorDeFranja(sub12.idFranja, null)) // por turnos
    expect((await resolver(servicio.listarSemana(LUNES))).find((s) => s.idFranja === sub12.idFranja).entrenador).toBeNull()
  })

  it('cambiar a un entrenador que ya tiene otra franja a esa hora falla (cruce)', async () => {
    const sub12 = (await resolver(servicio.listarSemana(LUNES))).find((s) => s.categoria === 'Sub-12' && s.fecha === '2026-10-19')
    // Jhon Cardona (3) tiene Sub-13 los lunes de 19:00 a 20:00
    await expect(resolver(servicio.cambiarEntrenadorDeFranja(sub12.idFranja, 3))).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { idEntrenador: expect.any(String) } })
  })

  it('listarEntrenadores devuelve los de ejemplo, con ids numéricos', async () => {
    const lista = await resolver(servicio.listarEntrenadores())
    expect(lista.map((e) => e.id)).toEqual([1, 2, 3, 4, 5])
  })
})
