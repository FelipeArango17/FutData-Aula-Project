import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FRANJAS_EJEMPLO } from './horarioClubData.js'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.resetModules()
  servicio = await import('./entrenamientosService.js')
})

// Dairo (id 2) no tiene franjas los miércoles
const nuevo = (extra = {}) => ({
  categoria: 'Sub-10', fecha: '2026-10-21', hora: '10:00', horaFin: '11:00', sede: 'Horizontes', idEntrenador: '2', grupo: '', ...extra,
})
const buscar = async (filtro) => (await servicio.listar()).find(filtro)
// Sub-10 del lunes 19 de octubre, 7:00 p. m. a 8:00 p. m. (de Dairo)
const dairoLunes = { fecha: '2026-10-19', hora: '19:00', horaFin: '20:00', idEntrenador: 2 }

describe('listar', () => {
  it('devuelve los entrenamientos de la semana de HOY_EJEMPLO que salen de la plantilla, como copias', async () => {
    const lista = await servicio.listar()
    expect(lista).toHaveLength(FRANJAS_EJEMPLO.length)
    lista[0].sede = 'Otra'
    lista.pop()
    expect(await servicio.listar()).toHaveLength(FRANJAS_EJEMPLO.length)
    expect((await servicio.listar())[0].sede).not.toBe('Otra')
  })

  it('todos caen entre el lunes 19 y el sábado 24 de octubre, con sede válida y entrenador de la lista o null', async () => {
    const ids = [null, ...servicio.ENTRENADORES.map((e) => e.id)]
    for (const e of await servicio.listar()) {
      expect(e.fecha >= '2026-10-19' && e.fecha <= '2026-10-24', e.fecha).toBe(true)
      expect(servicio.SEDES).toContain(e.sede)
      expect(ids).toContain(e.idEntrenador)
      expect(servicio.CATEGORIAS).toContain(e.categoria)
      expect(e.horaFin > e.hora).toBe(true)
    }
  })

  it('los ids van del 1 al total, sin repetirse', async () => {
    const lista = await servicio.listar()
    expect(lista.map((e) => e.id)).toEqual(lista.map((_, i) => i + 1))
  })

  it('Sub-9 y Sub-10 tienen entrenamientos de grupo mañana y tarde sin entrenador fijo; las demás categorías no tienen grupo', async () => {
    const lista = await servicio.listar()
    const conGrupo = lista.filter((e) => e.grupo)
    expect(new Set(conGrupo.map((e) => e.categoria))).toEqual(new Set(['Sub-9', 'Sub-10']))
    expect(new Set(conGrupo.map((e) => e.grupo))).toEqual(new Set(['mañana', 'tarde']))
    expect(conGrupo.every((e) => e.idEntrenador === null)).toBe(true)
    expect(lista.filter((e) => !['Sub-9', 'Sub-10'].includes(e.categoria)).every((e) => e.grupo === null)).toBe(true)
  })

  it('la sede de Párvulos a Sub-11 es UVA El Paraíso y la de Sub-12 a Sub-16 es Horizontes', async () => {
    for (const e of await servicio.listar()) {
      const numero = Number(e.categoria.replace('Sub-', ''))
      const enUva = ['Párvulos', 'Cachorros'].includes(e.categoria) || numero <= 11
      expect(e.sede, e.categoria).toBe(enUva ? 'UVA El Paraíso' : 'Horizontes')
    }
  })
})

describe('detectarConflicto (mismo entrenador, misma fecha, horarios que se cruzan)', () => {
  it('el mismo entrenador con un horario que se cruza es conflicto', async () => {
    const r = await servicio.detectarConflicto({ ...dairoLunes, hora: '19:30', horaFin: '20:30' })
    expect(r.hayConflicto).toBe(true)
  })

  it('terminar justo cuando empieza otro, otro entrenador, otra fecha o "Todos los entrenadores" no es conflicto', async () => {
    const sinConflicto = { hayConflicto: false, alternativas: [] }
    expect(await servicio.detectarConflicto({ ...dairoLunes, hora: '20:00', horaFin: '21:00' })).toEqual(sinConflicto)
    expect(await servicio.detectarConflicto({ ...dairoLunes, idEntrenador: 3 })).toEqual(sinConflicto)
    expect(await servicio.detectarConflicto({ ...dairoLunes, fecha: '2026-10-26' })).toEqual(sinConflicto)
    expect(await servicio.detectarConflicto({ ...dairoLunes, idEntrenador: '' })).toEqual(sinConflicto)
    expect(await servicio.detectarConflicto({ ...dairoLunes, idEntrenador: null })).toEqual(sinConflicto)
  })

  it('el id puede llegar como texto, como en el formulario', async () => {
    expect((await servicio.detectarConflicto({ ...dairoLunes, idEntrenador: '2' })).hayConflicto).toBe(true)
  })

  it('dos categorías pueden coincidir en fecha, hora y sede (el club lo hace)', async () => {
    // Sub-9 y Sub-10 entrenan el sábado 24 a las 8:30 en UVA El Paraíso; Sub-11 también puede
    const r = await servicio.crear(nuevo({ categoria: 'Sub-11', fecha: '2026-10-24', hora: '08:30', horaFin: '10:00', sede: 'UVA El Paraíso', idEntrenador: '3' }))
    expect(r.entrenamiento.categoria).toBe('Sub-11')
  })

  it('ofrece el siguiente horario libre del entrenador, 90 minutos después y con la misma duración', async () => {
    const r = await servicio.detectarConflicto(dairoLunes)
    expect(r.alternativas).toEqual([{ tipo: 'hora', hora: '20:30', horaFin: '21:30' }])
  })

  it('salta una alternativa que también está ocupada', async () => {
    await servicio.crear(nuevo({ fecha: '2026-10-19', hora: '20:30', horaFin: '21:30' }))
    const r = await servicio.detectarConflicto(dairoLunes)
    expect(r.alternativas).toEqual([{ tipo: 'hora', hora: '22:00', horaFin: '23:00' }])
  })

  it('sin alternativas cuando se pasa de la medianoche', async () => {
    await servicio.crear(nuevo({ hora: '23:00', horaFin: '23:30' }))
    expect(await servicio.detectarConflicto(nuevo({ hora: '23:00', horaFin: '23:30' }))).toEqual({ hayConflicto: true, alternativas: [] })
  })

  it('ignorarId evita el conflicto de un entrenamiento consigo mismo', async () => {
    const propio = await buscar((e) => e.idEntrenador === 2 && e.fecha === '2026-10-19')
    expect((await servicio.detectarConflicto(dairoLunes)).hayConflicto).toBe(true)
    expect((await servicio.detectarConflicto(dairoLunes, propio.id)).hayConflicto).toBe(false)
    expect((await servicio.detectarConflicto(dairoLunes, propio.id + 1)).hayConflicto).toBe(true)
  })
})

describe('crear', () => {
  it('agrega el entrenamiento con id nuevo, idEntrenador numérico y avisa a los jugadores de la categoría', async () => {
    const total = FRANJAS_EJEMPLO.length
    const { entrenamiento, notificados } = await servicio.crear(nuevo({ categoria: 'Sub-12', idEntrenador: '1' }))
    expect(entrenamiento).toEqual({
      id: total + 1, categoria: 'Sub-12', fecha: '2026-10-21', hora: '10:00', horaFin: '11:00', sede: 'Horizontes', idEntrenador: 1, grupo: null,
    })
    expect(notificados).toBe(17)
    const lista = await servicio.listar()
    expect(lista).toHaveLength(total + 1)
    expect(lista.at(-1)).toEqual(entrenamiento)
  })

  it('"Todos los entrenadores" se guarda como null y el grupo vacío también', async () => {
    const { entrenamiento } = await servicio.crear(nuevo({ categoria: 'Cachorros', idEntrenador: '' }))
    expect(entrenamiento.idEntrenador).toBeNull()
    expect(entrenamiento.grupo).toBeNull()
    expect((await servicio.crear(nuevo({ categoria: 'Sub-9', idEntrenador: '', hora: '12:00', horaFin: '13:00', grupo: 'tarde' }))).entrenamiento.grupo).toBe('tarde')
  })

  it('los ids no se repiten', async () => {
    const a = await servicio.crear(nuevo({ hora: '08:00', horaFin: '09:00' }))
    const b = await servicio.crear(nuevo({ hora: '09:00', horaFin: '10:00' }))
    expect(b.entrenamiento.id).toBe(a.entrenamiento.id + 1)
  })

  it('rechaza un conflicto de entrenador con error que trae las alternativas y no guarda', async () => {
    const error = await servicio.crear(nuevo({ fecha: '2026-10-19', hora: '19:00', horaFin: '20:00' })).catch((e) => e)
    expect(error).toBeInstanceOf(Error)
    expect(error.conflicto.hayConflicto).toBe(true)
    expect(error.conflicto.alternativas.length).toBeGreaterThan(0)
    expect(await servicio.listar()).toHaveLength(FRANJAS_EJEMPLO.length)
  })

  it('el número de jugadores avisados depende de la categoría', async () => {
    const r = await servicio.crear(nuevo({ categoria: 'Sub-16', hora: '08:00', horaFin: '09:00', idEntrenador: '6' }))
    expect(r.notificados).toBe(12)
  })
})

describe('editar', () => {
  it('actualiza los datos conservando el id', async () => {
    const original = await buscar((e) => e.categoria === 'Cachorros')
    const { entrenamiento, notificados } = await servicio.editar(original.id, nuevo({ categoria: 'Cachorros', hora: '08:00', horaFin: '09:00', idEntrenador: '' }))
    expect(entrenamiento).toMatchObject({ id: original.id, categoria: 'Cachorros', hora: '08:00', horaFin: '09:00', fecha: '2026-10-21', idEntrenador: null })
    expect(notificados).toBe(14)
    const lista = await servicio.listar()
    expect(lista).toHaveLength(FRANJAS_EJEMPLO.length)
    expect(lista.find((e) => e.id === original.id).hora).toBe('08:00')
  })

  it('guardar sin cambios no choca consigo mismo', async () => {
    const original = await buscar((e) => e.idEntrenador === 2)
    const datos = { ...original }
    delete datos.id
    await expect(servicio.editar(original.id, datos)).resolves.toBeDefined()
  })

  it('rechaza mover un entrenamiento a un horario donde su entrenador ya tiene otro', async () => {
    const propio = await buscar((e) => e.idEntrenador === 2 && e.fecha === '2026-10-20')
    const error = await servicio.editar(propio.id, { ...propio, fecha: '2026-10-19', hora: '19:30', horaFin: '20:30' }).catch((e) => e)
    expect(error.conflicto.hayConflicto).toBe(true)
    expect((await buscar((e) => e.id === propio.id)).fecha).toBe('2026-10-20')
  })

  it('falla si el entrenamiento no existe', async () => {
    await expect(servicio.editar(999, nuevo())).rejects.toThrow('no existe')
  })
})

describe('eliminar', () => {
  it('quita el entrenamiento y devuelve los jugadores a avisar', async () => {
    const sub12 = await buscar((e) => e.categoria === 'Sub-12')
    const r = await servicio.eliminar(sub12.id)
    expect(r).toEqual({ id: sub12.id, notificados: 17 })
    const lista = await servicio.listar()
    expect(lista).toHaveLength(FRANJAS_EJEMPLO.length - 1)
    expect(lista.some((e) => e.id === sub12.id)).toBe(false)
  })

  it('libera el horario del entrenador para otro entrenamiento', async () => {
    const propio = await buscar((e) => e.idEntrenador === 2 && e.fecha === '2026-10-19')
    expect((await servicio.detectarConflicto(dairoLunes)).hayConflicto).toBe(true)
    await servicio.eliminar(propio.id)
    expect((await servicio.detectarConflicto(dairoLunes)).hayConflicto).toBe(false)
  })

  it('falla si no existe o ya se eliminó', async () => {
    await expect(servicio.eliminar(999)).rejects.toThrow('no existe')
    await servicio.eliminar(3)
    await expect(servicio.eliminar(3)).rejects.toThrow('no existe')
  })
})

describe('catálogos de ejemplo', () => {
  it('el entrenador gestiona Sub-9 y Sub-12 (las de Nicolás) y el administrador todas', () => {
    expect(servicio.categoriasPorRol.entrenador).toEqual(['Sub-9', 'Sub-12'])
    expect(servicio.categoriasPorRol.administrador).toEqual(servicio.CATEGORIAS)
    expect(servicio.ENTRENADORES.find((e) => e.id === servicio.ID_ENTRENADOR_EJEMPLO).nombre).toBe('Nicolás')
  })

  it('las sedes son Horizontes y UVA El Paraíso, y los entrenadores de ejemplo son los seis del club, todos activos', () => {
    expect(servicio.SEDES).toEqual(['Horizontes', 'UVA El Paraíso'])
    expect(servicio.ENTRENADORES.map((e) => e.nombre)).toEqual(['Nicolás', 'Dairo', 'David Mesa', 'Jhon Cardona', 'Giovanny', 'Jhon Ramírez'])
    expect(servicio.ENTRENADORES.every((e) => e.activo)).toBe(true)
  })
})
