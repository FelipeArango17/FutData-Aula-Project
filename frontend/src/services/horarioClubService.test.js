import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FRANJAS_EJEMPLO } from './horarioClubData.js'

// El servicio guarda las franjas en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./horarioClubService.js')
})
afterEach(() => vi.useRealTimers())

const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

// Sub-11 (id 5, David Mesa = 3): miércoles de 10 a 11
const datos = (extra = {}) => ({ idCategoria: '5', dia: '3', horaInicio: '10:00', horaFin: '11:00', sede: 'UVA El Paraíso', idEntrenador: '3', grupo: '', ...extra })

describe('listar', () => {
  it('devuelve la plantilla de ejemplo, como copias', async () => {
    const lista = await resolver(servicio.listar())
    expect(lista).toHaveLength(FRANJAS_EJEMPLO.length)
    lista[0].sede = 'Otra'
    lista.pop()
    const otra = await resolver(servicio.listar())
    expect(otra).toHaveLength(FRANJAS_EJEMPLO.length)
    expect(otra[0].sede).not.toBe('Otra')
  })
})

describe('crear', () => {
  it('guarda la franja con id nuevo, números donde corresponde y null sin entrenador ni grupo', async () => {
    const nueva = await resolver(servicio.crear(datos({ idEntrenador: '', idCategoria: '2' })))
    expect(nueva).toEqual({ id: FRANJAS_EJEMPLO.length + 1, idCategoria: 2, dia: 3, horaInicio: '10:00', horaFin: '11:00', sede: 'UVA El Paraíso', idEntrenador: null, grupo: null })
    expect(await resolver(servicio.listar())).toHaveLength(FRANJAS_EJEMPLO.length + 1)
  })

  it('rechaza con VALIDACION y errores por campo, y no guarda nada', async () => {
    const error = await resolver(servicio.crear(datos({ idCategoria: '', sede: '', horaFin: '09:00' }))).catch((e) => e)
    expect(error.codigo).toBe('VALIDACION')
    expect(Object.keys(error.errores).sort()).toEqual(['horaFin', 'idCategoria', 'sede'])
    expect(await resolver(servicio.listar())).toHaveLength(FRANJAS_EJEMPLO.length)
  })

  it('rechaza que el mismo entrenador tenga dos franjas que se cruzan, pero deja coincidir otras categorías', async () => {
    // David Mesa ya tiene miércoles 19:00 a 20:00 (Sub-11)
    const error = await resolver(servicio.crear(datos({ horaInicio: '19:30', horaFin: '20:30' }))).catch((e) => e)
    expect(error.errores).toEqual({ idEntrenador: expect.stringContaining('ya tiene otra franja') })
    await expect(resolver(servicio.crear(datos({ idCategoria: '6', horaInicio: '19:00', horaFin: '20:00', idEntrenador: '1', sede: 'Horizontes' })))).resolves.toBeTruthy()
  })

  it('los ids no se repiten', async () => {
    const a = await resolver(servicio.crear(datos()))
    const b = await resolver(servicio.crear(datos({ dia: '6' })))
    expect(b.id).toBe(a.id + 1)
  })
})

describe('editar', () => {
  it('actualiza la franja conservando el id', async () => {
    const original = (await resolver(servicio.listar()))[0]
    const editada = await resolver(servicio.editar(original.id, datos({ idCategoria: String(original.idCategoria), idEntrenador: '', sede: 'Horizontes' })))
    expect(editada).toMatchObject({ id: original.id, sede: 'Horizontes', dia: 3, horaInicio: '10:00', idEntrenador: null })
    expect((await resolver(servicio.listar())).find((f) => f.id === original.id).sede).toBe('Horizontes')
  })

  it('guardar sin cambios no choca consigo misma', async () => {
    const original = (await resolver(servicio.listar())).find((f) => f.idEntrenador === 3)
    await expect(resolver(servicio.editar(original.id, original))).resolves.toEqual(original)
  })

  it('rechaza mover una franja a un horario donde su entrenador ya tiene otra', async () => {
    const lista = await resolver(servicio.listar())
    const miercoles = lista.find((f) => f.idEntrenador === 3 && f.dia === 3)
    const jueves = lista.find((f) => f.idEntrenador === 3 && f.dia === 4)
    const error = await resolver(servicio.editar(jueves.id, { ...jueves, dia: 3, horaInicio: miercoles.horaInicio, horaFin: miercoles.horaFin })).catch((e) => e)
    expect(error.codigo).toBe('VALIDACION')
    expect(error.errores.idEntrenador).toBeTruthy()
  })

  it('falla con FRANJA_NO_EXISTE si no existe', async () => {
    await expect(resolver(servicio.editar(999, datos()))).rejects.toMatchObject({ codigo: 'FRANJA_NO_EXISTE' })
  })
})

describe('eliminar', () => {
  it('quita la franja y libera el horario del entrenador', async () => {
    const lista = await resolver(servicio.listar())
    const miercoles = lista.find((f) => f.idEntrenador === 3 && f.dia === 3)
    await expect(resolver(servicio.crear(datos({ horaInicio: miercoles.horaInicio, horaFin: miercoles.horaFin })))).rejects.toMatchObject({ codigo: 'VALIDACION' })
    expect(await resolver(servicio.eliminar(miercoles.id))).toEqual({ id: miercoles.id })
    expect(await resolver(servicio.listar())).toHaveLength(FRANJAS_EJEMPLO.length - 1)
    await expect(resolver(servicio.crear(datos({ horaInicio: miercoles.horaInicio, horaFin: miercoles.horaFin })))).resolves.toBeTruthy()
  })

  it('falla con FRANJA_NO_EXISTE si no existe o ya se eliminó', async () => {
    await expect(resolver(servicio.eliminar(999))).rejects.toMatchObject({ codigo: 'FRANJA_NO_EXISTE' })
    await resolver(servicio.eliminar(1))
    await expect(resolver(servicio.eliminar(1))).rejects.toMatchObject({ codigo: 'FRANJA_NO_EXISTE' })
  })
})
