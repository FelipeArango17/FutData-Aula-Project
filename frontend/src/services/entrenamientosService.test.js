import { beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.resetModules()
  servicio = await import('./entrenamientosService.js')
})

const nuevo = (extra = {}) => ({ categoria: 'Sub-10', fecha: '2026-10-21', hora: '10:00', lugar: 'Cancha 1', idEntrenador: '2', ...extra })

describe('listar', () => {
  it('devuelve los 11 entrenamientos de ejemplo y copias que no alteran el servicio', async () => {
    const lista = await servicio.listar()
    expect(lista).toHaveLength(11)
    lista[0].lugar = 'Otro'
    lista.pop()
    expect(await servicio.listar()).toHaveLength(11)
    expect((await servicio.listar())[0].lugar).toBe('Cancha 1')
  })
})

describe('detectarConflicto', () => {
  it('mismo lugar, fecha y hora es conflicto', async () => {
    const r = await servicio.detectarConflicto({ fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' })
    expect(r.hayConflicto).toBe(true)
  })

  it('otro lugar, otra hora u otra fecha no es conflicto', async () => {
    const base = { fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' }
    expect(await servicio.detectarConflicto({ ...base, lugar: 'Cancha 1' })).toEqual({ hayConflicto: false, alternativas: [] })
    expect((await servicio.detectarConflicto({ ...base, hora: '09:30' })).hayConflicto).toBe(false)
    expect((await servicio.detectarConflicto({ ...base, fecha: '2026-10-26' })).hayConflicto).toBe(false)
  })

  it('ofrece la hora 90 minutos más tarde y el otro lugar', async () => {
    const r = await servicio.detectarConflicto({ fecha: '2026-10-24', hora: '10:00', lugar: 'Cancha 2' })
    expect(r.alternativas).toEqual([
      { tipo: 'hora', hora: '11:30' },
      { tipo: 'lugar', lugar: 'Cancha 1' },
    ])
  })

  it('salta una hora alternativa que también está ocupada', async () => {
    // El 20 de octubre la Cancha 1 está ocupada a las 16:00 y a las 17:30
    const r = await servicio.detectarConflicto({ fecha: '2026-10-20', hora: '16:00', lugar: 'Cancha 1' })
    expect(r.alternativas).toContainEqual({ tipo: 'hora', hora: '19:00' })
    expect(r.alternativas).not.toContainEqual({ tipo: 'hora', hora: '17:30' })
  })

  it('no ofrece el otro lugar si también está ocupado a esa hora', async () => {
    await servicio.crear(nuevo({ fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 1' }))
    const r = await servicio.detectarConflicto({ fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' })
    expect(r.hayConflicto).toBe(true)
    expect(r.alternativas.some((a) => a.tipo === 'lugar')).toBe(false)
    expect(r.alternativas.some((a) => a.tipo === 'hora')).toBe(true)
  })

  it('sin alternativas cuando pasa de la medianoche y el otro lugar está ocupado', async () => {
    await servicio.crear(nuevo({ hora: '23:00', lugar: 'Cancha 1' }))
    await servicio.crear(nuevo({ hora: '23:00', lugar: 'Cancha 2' }))
    const r = await servicio.detectarConflicto(nuevo({ hora: '23:00', lugar: 'Cancha 1' }))
    expect(r).toEqual({ hayConflicto: true, alternativas: [] })
  })

  it('ignorarId evita el conflicto de un entrenamiento consigo mismo', async () => {
    const mismo = { fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' } // es el id 10
    expect((await servicio.detectarConflicto(mismo)).hayConflicto).toBe(true)
    expect((await servicio.detectarConflicto(mismo, 10)).hayConflicto).toBe(false)
    expect((await servicio.detectarConflicto(mismo, 1)).hayConflicto).toBe(true)
  })
})

describe('crear', () => {
  it('agrega el entrenamiento con id nuevo, idEntrenador numérico y avisa a los jugadores de la categoría', async () => {
    const { entrenamiento, notificados } = await servicio.crear(nuevo({ categoria: 'Sub-12' }))
    expect(entrenamiento).toEqual({ id: 12, categoria: 'Sub-12', fecha: '2026-10-21', hora: '10:00', lugar: 'Cancha 1', idEntrenador: 2 })
    expect(notificados).toBe(17)
    const lista = await servicio.listar()
    expect(lista).toHaveLength(12)
    expect(lista.at(-1)).toEqual(entrenamiento)
  })

  it('los ids no se repiten', async () => {
    const a = await servicio.crear(nuevo({ hora: '08:00' }))
    const b = await servicio.crear(nuevo({ hora: '09:00' }))
    expect(b.entrenamiento.id).toBe(a.entrenamiento.id + 1)
  })

  it('rechaza un conflicto con error que trae las alternativas y no guarda', async () => {
    const error = await servicio.crear(nuevo({ fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' })).catch((e) => e)
    expect(error).toBeInstanceOf(Error)
    expect(error.conflicto.hayConflicto).toBe(true)
    expect(error.conflicto.alternativas.length).toBeGreaterThan(0)
    expect(await servicio.listar()).toHaveLength(11)
  })

  it('el número de jugadores avisados depende de la categoría', async () => {
    const r = await servicio.crear(nuevo({ categoria: 'Sub-16', hora: '08:00' }))
    expect(r.notificados).toBe(12)
  })
})

describe('editar', () => {
  it('actualiza los datos conservando el id', async () => {
    const { entrenamiento, notificados } = await servicio.editar(1, nuevo({ categoria: 'Cachorros', hora: '08:00' }))
    expect(entrenamiento).toMatchObject({ id: 1, categoria: 'Cachorros', hora: '08:00', fecha: '2026-10-21' })
    expect(notificados).toBe(14)
    const lista = await servicio.listar()
    expect(lista).toHaveLength(11)
    expect(lista.find((e) => e.id === 1).hora).toBe('08:00')
  })

  it('guardar sin cambios no choca consigo mismo', async () => {
    const original = (await servicio.listar()).find((e) => e.id === 10)
    const datos = { ...original }
    delete datos.id
    await expect(servicio.editar(10, datos)).resolves.toBeDefined()
  })

  it('rechaza mover un entrenamiento a un horario ocupado por otro', async () => {
    const error = await servicio.editar(1, nuevo({ fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' })).catch((e) => e)
    expect(error.conflicto.hayConflicto).toBe(true)
    expect((await servicio.listar()).find((e) => e.id === 1).fecha).toBe('2026-10-19')
  })

  it('falla si el entrenamiento no existe', async () => {
    await expect(servicio.editar(999, nuevo())).rejects.toThrow('no existe')
  })
})

describe('eliminar', () => {
  it('quita el entrenamiento y devuelve los jugadores a avisar', async () => {
    const r = await servicio.eliminar(2) // Sub-12
    expect(r).toEqual({ id: 2, notificados: 17 })
    const lista = await servicio.listar()
    expect(lista).toHaveLength(10)
    expect(lista.some((e) => e.id === 2)).toBe(false)
  })

  it('libera el horario para otro entrenamiento', async () => {
    const ocupado = nuevo({ fecha: '2026-10-24', hora: '09:00', lugar: 'Cancha 2' })
    expect((await servicio.detectarConflicto(ocupado)).hayConflicto).toBe(true)
    await servicio.eliminar(10)
    expect((await servicio.detectarConflicto(ocupado)).hayConflicto).toBe(false)
  })

  it('falla si no existe o ya se eliminó', async () => {
    await expect(servicio.eliminar(999)).rejects.toThrow('no existe')
    await servicio.eliminar(3)
    await expect(servicio.eliminar(3)).rejects.toThrow('no existe')
  })
})

describe('categoriasPorRol', () => {
  it('el entrenador solo gestiona Sub-10 y Sub-12 y el administrador todas', () => {
    expect(servicio.categoriasPorRol.entrenador).toEqual(['Sub-10', 'Sub-12'])
    expect(servicio.categoriasPorRol.administrador).toEqual(servicio.CATEGORIAS)
  })
})
