import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// El servicio guarda los datos en memoria a nivel de módulo: cada prueba carga una copia nueva.
let servicio
beforeEach(async () => {
  vi.useFakeTimers()
  vi.resetModules()
  servicio = await import('./deportistasService.js')
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

describe('listarCategorias', () => {
  it('devuelve las categorías oficiales y una no vigente', async () => {
    const lista = await resolver(servicio.listarCategorias())
    expect(lista.filter((c) => c.activa).map((c) => c.nombre)).toEqual(['Sub-8', 'Sub-10', 'Sub-12', 'Sub-14', 'Sub-16'])
    expect(lista.find((c) => c.nombre === 'Sub-18').activa).toBe(false)
  })

  it('espera el retardo simulado antes de responder', async () => {
    let listo = false
    servicio.listarCategorias().then(() => { listo = true })
    await vi.advanceTimersByTimeAsync(100)
    expect(listo).toBe(false)
    await vi.advanceTimersByTimeAsync(200)
    expect(listo).toBe(true)
  })
})

describe('listarDeportistas', () => {
  it('sin filtros devuelve todos', async () => {
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })

  it('filtra por categoría (el id puede ser número o texto)', async () => {
    const a = await resolver(servicio.listarDeportistas({ idCategoria: 3 }))
    const b = await resolver(servicio.listarDeportistas({ idCategoria: '3' }))
    expect(a).toHaveLength(6)
    expect(b).toEqual(a)
    expect(a.every((x) => x.idCategoria === 3)).toBe(true)
  })

  it('una categoría sin deportistas devuelve lista vacía', async () => {
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 5 }))).toEqual([])
  })

  it('busca por nombre sin distinguir mayúsculas ni tildes', async () => {
    const r = await resolver(servicio.listarDeportistas({ texto: 'TOMAS' }))
    expect(r.map((x) => x.nombre)).toEqual(['Tomás'])
    const r2 = await resolver(servicio.listarDeportistas({ texto: 'rios' }))
    expect(r2.map((x) => x.nombre)).toEqual(['Santiago', 'Valeria'])
  })

  it('busca por documento y combina con la categoría', async () => {
    expect(await resolver(servicio.listarDeportistas({ texto: '1100000012' }))).toHaveLength(1)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 1, texto: 'giraldo' }))).toEqual([])
  })

  it('un texto sin coincidencias devuelve lista vacía', async () => {
    expect(await resolver(servicio.listarDeportistas({ texto: 'zzzz' }))).toEqual([])
  })

  it('categoría inexistente lanza CATEGORIA_NO_EXISTE', async () => {
    await expect(resolver(servicio.listarDeportistas({ idCategoria: 99 }))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
  })

  it('devuelve copias: modificar el resultado no cambia los datos', async () => {
    const [primero] = await resolver(servicio.listarDeportistas({ idCategoria: 1 }))
    primero.nombre = 'X'
    const [otra] = await resolver(servicio.listarDeportistas({ idCategoria: 1 }))
    expect(otra.nombre).toBe('Santiago')
  })

  it('dos deportistas de demo tienen el acudiente de demostración', async () => {
    const todos = await resolver(servicio.listarDeportistas())
    expect(todos.filter((x) => x.acudiente === 'acudiente@pradofc.co')).toHaveLength(2)
  })

  it('un fallo simulado lanza ERROR_DE_RED una sola vez', async () => {
    servicio.configurarSimulacion({ fallos: 1 })
    await expect(resolver(servicio.listarDeportistas())).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    expect(await resolver(servicio.listarDeportistas())).toHaveLength(21)
  })
})

describe('obtenerDeportista', () => {
  it('acepta el id como número o texto', async () => {
    expect((await resolver(servicio.obtenerDeportista(2))).nombre).toBe('Valeria')
    expect((await resolver(servicio.obtenerDeportista('2'))).nombre).toBe('Valeria')
  })

  it('un deportista inexistente lanza DEPORTISTA_NO_EXISTE', async () => {
    await expect(resolver(servicio.obtenerDeportista(999))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
  })
})

describe('asignarCategoria', () => {
  it('cambia la categoría y queda guardada (ids como texto también sirven)', async () => {
    const r = await resolver(servicio.asignarCategoria('1', '5'))
    expect(r.idCategoria).toBe(5)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 5 }))).toHaveLength(1)
    expect(await resolver(servicio.listarDeportistas({ idCategoria: 1 }))).toHaveLength(4)
  })

  it('una categoría inexistente lanza CATEGORIA_NO_EXISTE y no cambia nada', async () => {
    await expect(resolver(servicio.asignarCategoria(1, 99))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_EXISTE' })
    expect((await resolver(servicio.obtenerDeportista(1))).idCategoria).toBe(1)
  })

  it('una categoría no vigente lanza CATEGORIA_NO_VIGENTE', async () => {
    await expect(resolver(servicio.asignarCategoria(1, 6))).rejects.toMatchObject({ codigo: 'CATEGORIA_NO_VIGENTE' })
    expect((await resolver(servicio.obtenerDeportista(1))).idCategoria).toBe(1)
  })

  it('un deportista inexistente lanza DEPORTISTA_NO_EXISTE', async () => {
    await expect(resolver(servicio.asignarCategoria(999, 1))).rejects.toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
  })
})

describe('calcularEdad', () => {
  it('cuenta años cumplidos a la fecha de referencia', () => {
    expect(servicio.calcularEdad('2018-10-22', '2026-10-22')).toBe(8)
    expect(servicio.calcularEdad('2018-10-23', '2026-10-22')).toBe(7)
    expect(servicio.calcularEdad('2018-03-14')).toBe(8)
  })
})
