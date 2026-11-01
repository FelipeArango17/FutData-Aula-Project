import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./asistenciaService.js')
  servicio.configurarSimulacion({ ahora: () => new Date(2026, 9, 22, 17, 40, 0) })
})
afterEach(() => vi.useRealTimers())

// Espera la respuesta del servicio avanzando el retardo simulado
const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}

const andres = { rol: 'entrenador', idEntrenador: 1, nombre: 'Andrés Mora' } // Sub-12
const carlos = { rol: 'entrenador', idEntrenador: 2, nombre: 'Carlos Díaz' } // Sub-8, 10, 14, 16
const admin = { rol: 'administrador', idEntrenador: null, nombre: 'Carlos Mendoza' }

// Sub-12 activos: 9 y 11 a 15 (el 16, ahora Sub-14, está inactivo). El entrenamiento 2 es pasado y sin registrar.
const completos = (estado = 'presente') => [9, 11, 12, 13, 14, 15].map((idDeportista) => ({ idDeportista, estado }))

describe('listarEntrenamientosParaAsistencia', () => {
  it('el entrenador solo ve los de sus categorías, ya ocurridos o de hoy', async () => {
    const lista = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: andres }))
    expect(lista.map((e) => e.id)).toEqual([2, 6])
    expect(lista.every((e) => e.categoria === 'Sub-12' && !e.futuro)).toBe(true)
    expect(lista.find((e) => e.id === 6).registrada).toBe(true)
  })

  it('el administrador ve los de todas las categorías', async () => {
    const lista = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: admin }))
    expect(lista.map((e) => e.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
  })

  it('con incluirFuturos agrega los próximos y fecha filtra por día', async () => {
    const todos = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: andres, incluirFuturos: true }))
    expect(todos.filter((e) => e.futuro).map((e) => e.id)).toEqual([10, 11])
    const dia = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: admin, fecha: '2026-10-22' }))
    expect(dia.map((e) => e.id)).toEqual([7, 8])
  })

  it('un rol sin permiso lanza SIN_PERMISO', async () => {
    await expect(resolver(servicio.listarEntrenamientosParaAsistencia({ actor: { rol: 'tesorero' } }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
  })
})

describe('obtenerAsistencia', () => {
  it('sin registrar: registro null y solo deportistas activos del grupo', async () => {
    const r = await resolver(servicio.obtenerAsistencia('2', andres))
    expect(r.registro).toBeNull()
    expect(r.deportistas.map((d) => d.id)).toEqual([9, 11, 12, 13, 14, 15])
    expect(r.entrenamiento).toMatchObject({ categoria: 'Sub-12', futuro: false, entrenador: 'Andrés Mora' })
  })

  it('registrada de ejemplo: trae items, responsable e historial', async () => {
    const r = await resolver(servicio.obtenerAsistencia(6, andres))
    expect(r.registro.registradoPor).toBe('Andrés Mora')
    expect(r.registro.items).toHaveLength(5)
    expect(r.registro.historial).toHaveLength(1)
  })

  it('entrenamiento inexistente lanza ENTRENAMIENTO_NO_EXISTE', async () => {
    await expect(resolver(servicio.obtenerAsistencia(99, admin))).rejects.toMatchObject({ codigo: 'ENTRENAMIENTO_NO_EXISTE' })
  })
})

describe('registrarAsistencia', () => {
  it('registro correcto: guarda responsable, fecha y hora, y observación sin espacios', async () => {
    const items = completos()
    items[3] = { idDeportista: 13, estado: 'ausente', observacion: '  Sin aviso ' }
    const r = await resolver(servicio.registrarAsistencia('2', items, andres))
    expect(r.registradoPor).toBe('Andrés Mora')
    expect(r.registradoEn).toBe('2026-10-22T17:40')
    expect(r.historial).toEqual([])
    expect(r.items.find((i) => i.idDeportista === 13)).toEqual({ idDeportista: 13, estado: 'ausente', observacion: 'Sin aviso' })
    const otra = await resolver(servicio.obtenerAsistencia(2, andres))
    expect(otra.registro.items).toHaveLength(6)
  })

  it('faltan estados: indica los ids que faltan y no guarda nada', async () => {
    const items = completos().slice(0, 4)
    items.push({ idDeportista: 14, estado: '' })
    await expect(resolver(servicio.registrarAsistencia(2, items, andres))).rejects.toMatchObject({ codigo: 'FALTAN_ESTADOS', faltan: [14, 15] })
    expect((await resolver(servicio.obtenerAsistencia(2, andres))).registro).toBeNull()
  })

  it('entrenamiento futuro lanza ENTRENAMIENTO_FUTURO', async () => {
    await expect(resolver(servicio.registrarAsistencia(10, completos(), andres))).rejects.toMatchObject({ codigo: 'ENTRENAMIENTO_FUTURO' })
  })

  it('doble registro lanza YA_REGISTRADA', async () => {
    await expect(resolver(servicio.registrarAsistencia(6, completos(), andres))).rejects.toMatchObject({ codigo: 'YA_REGISTRADA' })
    await resolver(servicio.registrarAsistencia(2, completos(), andres))
    await expect(resolver(servicio.registrarAsistencia('2', completos(), andres))).rejects.toMatchObject({ codigo: 'YA_REGISTRADA' })
  })

  it('un entrenador ajeno no puede registrar (SIN_PERMISO) y no cambia nada', async () => {
    await expect(resolver(servicio.registrarAsistencia(2, completos(), carlos))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    await expect(resolver(servicio.registrarAsistencia(7, completos(), andres))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    expect((await resolver(servicio.obtenerAsistencia(2, admin))).registro).toBeNull()
  })

  it('el administrador puede registrar en cualquier grupo y queda como responsable', async () => {
    const r = await resolver(servicio.registrarAsistencia(2, completos('justificado'), admin))
    expect(r.registradoPor).toBe('Carlos Mendoza')
  })

  it('un deportista que no pertenece al grupo (o está inactivo) lanza DEPORTISTA_NO_PERTENECE', async () => {
    await expect(resolver(servicio.registrarAsistencia(2, [...completos(), { idDeportista: 1, estado: 'presente' }], andres))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_PERTENECE' })
    await expect(resolver(servicio.registrarAsistencia(2, [...completos(), { idDeportista: 16, estado: 'presente' }], andres))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_PERTENECE' })
  })

  it('estado inválido y observación de más de 200 caracteres se rechazan', async () => {
    const mal = completos()
    mal[1] = { idDeportista: 11, estado: 'tarde' }
    await expect(resolver(servicio.registrarAsistencia(2, mal, andres))).rejects.toMatchObject({ codigo: 'ESTADO_INVALIDO' })
    const larga = completos()
    larga[1] = { idDeportista: 11, estado: 'presente', observacion: 'a'.repeat(201) }
    await expect(resolver(servicio.registrarAsistencia(2, larga, andres))).rejects.toMatchObject({ codigo: 'OBSERVACION_LARGA' })
    larga[1].observacion = 'a'.repeat(200)
    await expect(resolver(servicio.registrarAsistencia(2, larga, andres))).resolves.toBeTruthy()
  })
})

describe('editarAsistencia', () => {
  it('cambia estado y observación y agrega al historial antes/después sin borrar lo anterior', async () => {
    const r = await resolver(servicio.editarAsistencia(6, [{ idDeportista: 13, estado: 'justificado', observacion: 'Gripa' }], andres))
    expect(r.items.find((i) => i.idDeportista === 13)).toEqual({ idDeportista: 13, estado: 'justificado', observacion: 'Gripa' })
    expect(r.historial).toHaveLength(2)
    expect(r.historial[0].cambios[0].idDeportista).toBe(14)
    expect(r.historial[1]).toEqual({
      fechaHora: '2026-10-22T17:40',
      responsable: 'Andrés Mora',
      cambios: [{ idDeportista: 13, antes: { estado: 'ausente', observacion: 'Sin aviso' }, despues: { estado: 'justificado', observacion: 'Gripa' } }],
    })
    expect(r.registradoPor).toBe('Andrés Mora')
  })

  it('solo registra los cambios reales cuando se envían varios deportistas', async () => {
    const r = await resolver(servicio.editarAsistencia(6, [{ idDeportista: 11, estado: 'presente' }, { idDeportista: 12, estado: 'ausente' }], admin))
    const ultima = r.historial.at(-1)
    expect(ultima.responsable).toBe('Carlos Mendoza')
    expect(ultima.cambios.map((c) => c.idDeportista)).toEqual([12])
  })

  it('sin cambios reales lanza SIN_CAMBIOS y no agrega historial', async () => {
    await expect(resolver(servicio.editarAsistencia(6, [{ idDeportista: 11, estado: 'presente', observacion: ' ' }], andres))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    await expect(resolver(servicio.editarAsistencia(6, [], andres))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    expect((await resolver(servicio.obtenerAsistencia(6, andres))).registro.historial).toHaveLength(1)
  })

  it('un deportista que no pertenece al registro lanza DEPORTISTA_NO_PERTENECE', async () => {
    await expect(resolver(servicio.editarAsistencia(6, [{ idDeportista: 1, estado: 'ausente' }], andres))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_PERTENECE' })
  })

  it('observación de más de 200 caracteres lanza OBSERVACION_LARGA', async () => {
    await expect(resolver(servicio.editarAsistencia(6, [{ idDeportista: 11, observacion: 'b'.repeat(201) }], andres))).rejects.toMatchObject({ codigo: 'OBSERVACION_LARGA' })
  })

  it('un entrenador ajeno no puede editar (SIN_PERMISO); el administrador sí', async () => {
    await expect(resolver(servicio.editarAsistencia(6, [{ idDeportista: 11, estado: 'ausente' }], carlos))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    await expect(resolver(servicio.editarAsistencia(6, [{ idDeportista: 11, estado: 'ausente' }], admin))).resolves.toBeTruthy()
  })

  it('editar una asistencia sin registrar lanza NO_REGISTRADA', async () => {
    await expect(resolver(servicio.editarAsistencia(2, [{ idDeportista: 11, estado: 'ausente' }], andres))).rejects.toMatchObject({ codigo: 'NO_REGISTRADA' })
  })
})

describe('simulación de red', () => {
  it('un fallo simulado lanza ERROR_DE_RED una sola vez', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.obtenerAsistencia(6, andres))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    await expect(resolver(servicio.obtenerAsistencia(6, andres))).resolves.toBeTruthy()
  })
})
