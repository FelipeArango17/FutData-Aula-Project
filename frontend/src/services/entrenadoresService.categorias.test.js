import { beforeEach, describe, expect, it, vi } from 'vitest'

// El simulado de las operaciones nuevas (categorías con un solo entrenador, activar y desactivar), con las mismas reglas
// que la API. Datos de ejemplo (plantilla del club): Nicolás (Sub-9 y Sub-12) y Dairo (Sub-10), entre otros.
let servicio
let nicolas
let dairo
beforeEach(async () => {
  vi.resetModules()
  servicio = await import('./entrenadoresService.js')
  ;[nicolas, dairo] = await servicio.listar()
})

const rechazo = (promesa) => promesa.then(() => null, (error) => error)

describe('asignarCategorias (simulado)', () => {
  it('una categoría que no es de nadie se asigna sin confirmar', async () => {
    await servicio.asignarCategorias(dairo.id, ['Sub-10', 'Párvulos'])
    const dueno = (await servicio.listarCategorias()).find((c) => c.nombre === 'Párvulos')
    expect(dueno.entrenador).toMatchObject({ nombre: 'Dairo' })
  })

  it('quitar una categoría la deja sin entrenador y ya no es un error', async () => {
    const resultado = await servicio.asignarCategorias(nicolas.id, [])
    expect(resultado.categorias).toEqual([])
    expect((await servicio.listarCategorias()).find((c) => c.nombre === 'Sub-12').entrenador).toBeNull()
  })

  it('la de otro entrenador responde 409 con campos [categorias] hasta confirmar la reasignación', async () => {
    const error = await rechazo(servicio.asignarCategorias(nicolas.id, ['Sub-9', 'Sub-10', 'Sub-12']))
    expect(error).toMatchObject({ estado: 409, campos: ['categorias'] })
    expect((await servicio.obtener(nicolas.id)).categorias).toEqual(['Sub-9', 'Sub-12'])

    const resultado = await servicio.asignarCategorias(nicolas.id, ['Sub-9', 'Sub-10', 'Sub-12'], true)
    expect(resultado.categorias).toEqual(['Sub-9', 'Sub-10', 'Sub-12'])
    expect((await servicio.obtener(dairo.id)).categorias).toEqual([])
  })

  it('un entrenador inactivo no recibe categorías (409 con campos [activo])', async () => {
    await servicio.cambiarEstado(nicolas.id, false)
    expect(await rechazo(servicio.asignarCategorias(nicolas.id, ['Sub-12']))).toMatchObject({ estado: 409, campos: ['activo'] })
  })

  it('un entrenador que no existe responde 404', async () => {
    expect(await rechazo(servicio.asignarCategorias('00000000-0000-4000-8000-000000000000', []))).toMatchObject({ estado: 404 })
  })
})

describe('cambiarEstado (simulado)', () => {
  it('desactivar deja sin entrenador sus categorías; reactivar no se las devuelve', async () => {
    const inactivo = await servicio.cambiarEstado(dairo.id, false)
    expect(inactivo).toMatchObject({ activo: false, categorias: [] })
    expect((await servicio.listarCategorias()).filter((c) => c.entrenador === null).map((c) => c.nombre)).toEqual(['Párvulos', 'Cachorros', 'Sub-10'])

    const activo = await servicio.cambiarEstado(dairo.id, true)
    expect(activo).toMatchObject({ activo: true, categorias: [] })
  })

  it('los entrenadores de ejemplo y los nuevos nacen activos', async () => {
    expect(nicolas.activo).toBe(true)
    const creado = await servicio.crearDatos({
      nombre: 'Luis', apellido: 'Pérez', tipoDocumento: 'CC', documento: '1098765432', fechaNacimiento: '1990-05-14',
      telefono: '3001112233', correo: 'luis.perez@pradofc.co', especialidad: 'Porteros', categorias: [],
    })
    expect(creado).toMatchObject({ activo: true, categorias: [] })
  })
})

describe('listarCategorias (simulado)', () => {
  it('las diez categorías del club en orden oficial, cada una con su entrenador (o null)', async () => {
    await servicio.asignarCategorias(dairo.id, ['Sub-10'])
    const categorias = await servicio.listarCategorias()
    expect(categorias.map((c) => c.nombre)).toEqual(['Párvulos', 'Cachorros', 'Sub-9', 'Sub-10', 'Sub-11', 'Sub-12', 'Sub-13', 'Sub-14', 'Sub-15', 'Sub-16'])
    expect(categorias.map((c) => (c.entrenador ? `${c.entrenador.nombre} ${c.entrenador.apellido}` : null))).toEqual([
      null, null, 'Nicolás Ejemplo', 'Dairo Ejemplo', 'David Mesa', 'Nicolás Ejemplo', 'Jhon Cardona', 'Jhon Cardona', 'Giovanny Ejemplo', 'Jhon Ramírez',
    ])
    expect(categorias.find((c) => c.nombre === 'Párvulos')).toMatchObject({ edadMinima: 4, edadMaxima: 6 })
  })
})

describe('editarDatos (simulado)', () => {
  it('cambia los datos personales sin tocar categorías ni estado', async () => {
    const editado = await servicio.editarDatos(dairo.id, { ...dairo, nombre: 'Dairo Andrés', categorias: [] })
    expect(editado).toMatchObject({ nombre: 'Dairo Andrés', categorias: ['Sub-10'], activo: true })
  })
})
