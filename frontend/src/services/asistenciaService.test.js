import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
let ID // ids de los entrenamientos de ejemplo que salen de la plantilla del club
let ejemplo // todos los entrenamientos de ejemplo
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./asistenciaService.js')
  const entrenamientos = await import('./entrenamientosService.js')
  ejemplo = entrenamientos.entrenamientosDeEjemplo()
  const idDe = (filtro) => ejemplo.find(filtro).id
  ID = {
    registrado: idDe((e) => e.categoria === 'Sub-12' && e.fecha === '2026-10-20'), // ya registrado, con historial
    pendiente: idDe((e) => e.categoria === 'Sub-12' && e.fecha === '2026-10-19'), // pasado y sin registrar
    ajeno: idDe((e) => e.categoria === 'Sub-11' && e.fecha === '2026-10-20'), // de David Mesa: Nicolás no lo gestiona
    futuro: idDe((e) => e.categoria === 'Sub-13' && e.fecha === '2026-10-23'), // viernes, después de hoy (de Jhon Cardona)
  }
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

const nicolas = { rol: 'entrenador', idEntrenador: 1, nombre: 'Nicolás' } // Sub-12
const david = { rol: 'entrenador', idEntrenador: 2, nombre: 'David Mesa' } // Sub-11
const cardona = { rol: 'entrenador', idEntrenador: 3, nombre: 'Jhon Cardona' } // Sub-13 y Sub-14
const admin = { rol: 'administrador', idEntrenador: null, nombre: 'Carlos Mendoza' }

// Sub-12 activos: 11 a 15 (el 16, de Sub-13, está inactivo). El entrenamiento del lunes 19 es pasado y sin registrar.
const completos = (estado = 'presente') => [11, 12, 13, 14, 15].map((idDeportista) => ({ idDeportista, estado }))

describe('listarEntrenamientosParaAsistencia', () => {
  const HOY = '2026-10-22'
  const deNicolas = (e) => e.categoria === 'Sub-12'
  const deCardona = (e) => ['Sub-13', 'Sub-14'].includes(e.categoria)

  it('el entrenador solo ve los de sus categorías, ya ocurridos o de hoy', async () => {
    const lista = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: nicolas }))
    expect(lista.map((e) => e.id)).toEqual(ejemplo.filter((e) => deNicolas(e) && e.fecha <= HOY).map((e) => e.id))
    expect(lista.every((e) => deNicolas(e) && !e.futuro)).toBe(true)
    expect(lista.find((e) => e.id === ID.registrado).registrada).toBe(true)
    expect(lista.find((e) => e.id === ID.pendiente).registrada).toBe(false)
    expect(lista.some((e) => e.categoria === 'Sub-11')).toBe(false)
  })

  it('el administrador ve los de todas las categorías', async () => {
    const lista = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: admin }))
    expect(lista.map((e) => e.id)).toEqual(ejemplo.filter((e) => e.fecha <= HOY).map((e) => e.id))
    expect(new Set(lista.map((e) => e.categoria)).size).toBeGreaterThan(5)
  })

  it('con incluirFuturos agrega los próximos y fecha filtra por día', async () => {
    // Nicolás (Sub-12) ya no tiene entrenamientos después de hoy esta semana: Jhon Cardona (Sub-13 y Sub-14) sí, el viernes 23
    expect(await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: nicolas, incluirFuturos: true }))).toHaveLength(
      ejemplo.filter(deNicolas).filter((e) => e.fecha <= HOY).length,
    )
    const todos = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: cardona, incluirFuturos: true }))
    expect(todos.filter((e) => e.futuro).map((e) => e.id)).toEqual(ejemplo.filter((e) => deCardona(e) && e.fecha > HOY).map((e) => e.id))
    expect(todos.filter((e) => e.futuro).length).toBeGreaterThan(0)
    const dia = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: admin, fecha: HOY }))
    expect(dia.map((e) => e.id)).toEqual(ejemplo.filter((e) => e.fecha === HOY).map((e) => e.id))
  })

  it('cada entrenamiento trae el nombre de su entrenador, o "Por turnos" si no tiene uno fijo', async () => {
    const lista = await resolver(servicio.listarEntrenamientosParaAsistencia({ actor: admin }))
    expect(lista.find((e) => e.id === ID.registrado).entrenador).toBe('Nicolás')
    expect(lista.find((e) => e.id === ID.ajeno).entrenador).toBe('David Mesa')
    expect(lista.find((e) => e.categoria === 'Cachorros').entrenador).toBe('Por turnos')
  })

  it('un rol sin permiso lanza SIN_PERMISO', async () => {
    await expect(resolver(servicio.listarEntrenamientosParaAsistencia({ actor: { rol: 'tesorero' } }))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
  })
})

describe('obtenerAsistencia', () => {
  it('sin registrar: registro null y solo deportistas activos del grupo', async () => {
    const r = await resolver(servicio.obtenerAsistencia(String(ID.pendiente), nicolas))
    expect(r.registro).toBeNull()
    expect(r.deportistas.map((d) => d.id)).toEqual([11, 12, 13, 14, 15])
    expect(r.entrenamiento).toMatchObject({ categoria: 'Sub-12', futuro: false, entrenador: 'Nicolás' })
  })

  it('registrada de ejemplo: trae items, responsable e historial', async () => {
    const r = await resolver(servicio.obtenerAsistencia(ID.registrado, nicolas))
    expect(r.registro.registradoPor).toBe('Nicolás')
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
    items[2] = { idDeportista: 13, estado: 'ausente', observacion: '  Sin aviso ' }
    const r = await resolver(servicio.registrarAsistencia(String(ID.pendiente), items, nicolas))
    expect(r.registradoPor).toBe('Nicolás')
    expect(r.registradoEn).toBe('2026-10-22T17:40')
    expect(r.historial).toEqual([])
    expect(r.items.find((i) => i.idDeportista === 13)).toEqual({ idDeportista: 13, estado: 'ausente', observacion: 'Sin aviso' })
    const otra = await resolver(servicio.obtenerAsistencia(ID.pendiente, nicolas))
    expect(otra.registro.items).toHaveLength(5)
  })

  it('faltan estados: indica los ids que faltan y no guarda nada', async () => {
    const items = completos().slice(0, 3)
    items.push({ idDeportista: 14, estado: '' })
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, items, nicolas))).rejects.toMatchObject({ codigo: 'FALTAN_ESTADOS', faltan: [14, 15] })
    expect((await resolver(servicio.obtenerAsistencia(ID.pendiente, nicolas))).registro).toBeNull()
  })

  it('entrenamiento futuro lanza ENTRENAMIENTO_FUTURO', async () => {
    await expect(resolver(servicio.registrarAsistencia(ID.futuro, completos(), cardona))).rejects.toMatchObject({ codigo: 'ENTRENAMIENTO_FUTURO' })
  })

  it('doble registro lanza YA_REGISTRADA', async () => {
    await expect(resolver(servicio.registrarAsistencia(ID.registrado, completos(), nicolas))).rejects.toMatchObject({ codigo: 'YA_REGISTRADA' })
    await resolver(servicio.registrarAsistencia(ID.pendiente, completos(), nicolas))
    await expect(resolver(servicio.registrarAsistencia(String(ID.pendiente), completos(), nicolas))).rejects.toMatchObject({ codigo: 'YA_REGISTRADA' })
  })

  it('un entrenador ajeno no puede registrar (SIN_PERMISO) y no cambia nada', async () => {
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, completos(), david))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    await expect(resolver(servicio.registrarAsistencia(ID.ajeno, completos(), nicolas))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    expect((await resolver(servicio.obtenerAsistencia(ID.pendiente, admin))).registro).toBeNull()
  })

  it('el administrador puede registrar en cualquier grupo y queda como responsable', async () => {
    const r = await resolver(servicio.registrarAsistencia(ID.pendiente, completos('justificado'), admin))
    expect(r.registradoPor).toBe('Carlos Mendoza')
  })

  it('un deportista que no pertenece al grupo (o está inactivo) lanza DEPORTISTA_NO_PERTENECE', async () => {
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, [...completos(), { idDeportista: 1, estado: 'presente' }], nicolas))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_PERTENECE' })
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, [...completos(), { idDeportista: 16, estado: 'presente' }], nicolas))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_PERTENECE' })
  })

  it('estado inválido y observación de más de 200 caracteres se rechazan', async () => {
    const mal = completos()
    mal[0] = { idDeportista: 11, estado: 'tarde' }
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, mal, nicolas))).rejects.toMatchObject({ codigo: 'ESTADO_INVALIDO' })
    const larga = completos()
    larga[0] = { idDeportista: 11, estado: 'presente', observacion: 'a'.repeat(201) }
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, larga, nicolas))).rejects.toMatchObject({ codigo: 'OBSERVACION_LARGA' })
    larga[0].observacion = 'a'.repeat(200)
    await expect(resolver(servicio.registrarAsistencia(ID.pendiente, larga, nicolas))).resolves.toBeTruthy()
  })
})

describe('editarAsistencia', () => {
  it('cambia estado y observación y agrega al historial antes/después sin borrar lo anterior', async () => {
    const r = await resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 13, estado: 'justificado', observacion: 'Gripa' }], nicolas))
    expect(r.items.find((i) => i.idDeportista === 13)).toEqual({ idDeportista: 13, estado: 'justificado', observacion: 'Gripa' })
    expect(r.historial).toHaveLength(2)
    expect(r.historial[0].cambios[0].idDeportista).toBe(14)
    expect(r.historial[1]).toEqual({
      fechaHora: '2026-10-22T17:40',
      responsable: 'Nicolás',
      cambios: [{ idDeportista: 13, antes: { estado: 'ausente', observacion: 'Sin aviso' }, despues: { estado: 'justificado', observacion: 'Gripa' } }],
    })
    expect(r.registradoPor).toBe('Nicolás')
  })

  it('solo registra los cambios reales cuando se envían varios deportistas', async () => {
    const r = await resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 11, estado: 'presente' }, { idDeportista: 12, estado: 'ausente' }], admin))
    const ultima = r.historial.at(-1)
    expect(ultima.responsable).toBe('Carlos Mendoza')
    expect(ultima.cambios.map((c) => c.idDeportista)).toEqual([12])
  })

  it('sin cambios reales lanza SIN_CAMBIOS y no agrega historial', async () => {
    await expect(resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 11, estado: 'presente', observacion: ' ' }], nicolas))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    await expect(resolver(servicio.editarAsistencia(ID.registrado, [], nicolas))).rejects.toMatchObject({ codigo: 'SIN_CAMBIOS' })
    expect((await resolver(servicio.obtenerAsistencia(ID.registrado, nicolas))).registro.historial).toHaveLength(1)
  })

  it('un deportista que no pertenece al registro lanza DEPORTISTA_NO_PERTENECE', async () => {
    await expect(resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 1, estado: 'ausente' }], nicolas))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_PERTENECE' })
  })

  it('observación de más de 200 caracteres lanza OBSERVACION_LARGA', async () => {
    await expect(resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 11, observacion: 'b'.repeat(201) }], nicolas))).rejects.toMatchObject({ codigo: 'OBSERVACION_LARGA' })
  })

  it('un entrenador ajeno no puede editar (SIN_PERMISO); el administrador sí', async () => {
    await expect(resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 11, estado: 'ausente' }], david))).rejects.toMatchObject({ codigo: 'SIN_PERMISO' })
    await expect(resolver(servicio.editarAsistencia(ID.registrado, [{ idDeportista: 11, estado: 'ausente' }], admin))).resolves.toBeTruthy()
  })

  it('editar una asistencia sin registrar lanza NO_REGISTRADA', async () => {
    await expect(resolver(servicio.editarAsistencia(ID.pendiente, [{ idDeportista: 11, estado: 'ausente' }], nicolas))).rejects.toMatchObject({ codigo: 'NO_REGISTRADA' })
  })
})

describe('simulación de red', () => {
  it('un fallo simulado lanza ERROR_DE_RED una sola vez', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.obtenerAsistencia(ID.registrado, nicolas))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    await expect(resolver(servicio.obtenerAsistencia(ID.registrado, nicolas))).resolves.toBeTruthy()
  })
})
