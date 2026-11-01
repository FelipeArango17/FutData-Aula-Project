import { beforeEach, describe, expect, it, vi } from 'vitest'

// El simulado de las operaciones nuevas (categorías con un solo entrenador, activar y desactivar), con las mismas reglas
// que la API. Datos de ejemplo: Andrés Mora (Sub-12) y Carlos Díaz (Cachorros, Sub-10, Sub-14, Sub-16).
let servicio
let andres
let carlos
beforeEach(async () => {
  vi.resetModules()
  servicio = await import('./entrenadoresService.js')
  ;[andres, carlos] = await servicio.listar()
})

const rechazo = (promesa) => promesa.then(() => null, (error) => error)

describe('asignarCategorias (simulado)', () => {
  it('una categoría que no es de nadie se asigna sin confirmar', async () => {
    await servicio.asignarCategorias(carlos.id, ['Cachorros', 'Sub-10', 'Sub-14'])
    const dueno = (await servicio.listarCategorias()).find((c) => c.nombre === 'Sub-16')
    expect(dueno.entrenador).toBeNull()
  })

  it('quitar una categoría la deja sin entrenador y ya no es un error', async () => {
    const resultado = await servicio.asignarCategorias(andres.id, [])
    expect(resultado.categorias).toEqual([])
    expect((await servicio.listarCategorias()).find((c) => c.nombre === 'Sub-12').entrenador).toBeNull()
  })

  it('la de otro entrenador responde 409 con campos [categorias] hasta confirmar la reasignación', async () => {
    const error = await rechazo(servicio.asignarCategorias(andres.id, ['Sub-12', 'Cachorros']))
    expect(error).toMatchObject({ estado: 409, campos: ['categorias'] })
    expect((await servicio.obtener(andres.id)).categorias).toEqual(['Sub-12'])

    const resultado = await servicio.asignarCategorias(andres.id, ['Sub-12', 'Cachorros'], true)
    expect(resultado.categorias).toEqual(['Cachorros', 'Sub-12'])
    expect((await servicio.obtener(carlos.id)).categorias).toEqual(['Sub-10', 'Sub-14', 'Sub-16'])
  })

  it('un entrenador inactivo no recibe categorías (409 con campos [activo])', async () => {
    await servicio.cambiarEstado(andres.id, false)
    expect(await rechazo(servicio.asignarCategorias(andres.id, ['Sub-12']))).toMatchObject({ estado: 409, campos: ['activo'] })
  })

  it('un entrenador que no existe responde 404', async () => {
    expect(await rechazo(servicio.asignarCategorias('00000000-0000-4000-8000-000000000000', []))).toMatchObject({ estado: 404 })
  })
})

describe('cambiarEstado (simulado)', () => {
  it('desactivar deja sin entrenador sus categorías; reactivar no se las devuelve', async () => {
    const inactivo = await servicio.cambiarEstado(carlos.id, false)
    expect(inactivo).toMatchObject({ activo: false, categorias: [] })
    expect((await servicio.listarCategorias()).filter((c) => c.entrenador === null).map((c) => c.nombre)).toEqual(['Párvulos', 'Cachorros', 'Sub-9', 'Sub-10', 'Sub-11', 'Sub-13', 'Sub-14', 'Sub-15', 'Sub-16'])

    const activo = await servicio.cambiarEstado(carlos.id, true)
    expect(activo).toMatchObject({ activo: true, categorias: [] })
  })

  it('los entrenadores de ejemplo y los nuevos nacen activos', async () => {
    expect(andres.activo).toBe(true)
    const creado = await servicio.crearDatos({
      nombre: 'Luis', apellido: 'Pérez', tipoDocumento: 'CC', documento: '1098765432', fechaNacimiento: '1990-05-14',
      telefono: '3001112233', correo: 'luis.perez@pradofc.co', especialidad: 'Porteros', categorias: [],
    })
    expect(creado).toMatchObject({ activo: true, categorias: [] })
  })
})

describe('listarCategorias (simulado)', () => {
  it('las diez categorías del club en orden oficial, cada una con su entrenador (o null)', async () => {
    await servicio.asignarCategorias(carlos.id, ['Sub-10'])
    const categorias = await servicio.listarCategorias()
    expect(categorias.map((c) => c.nombre)).toEqual(['Párvulos', 'Cachorros', 'Sub-9', 'Sub-10', 'Sub-11', 'Sub-12', 'Sub-13', 'Sub-14', 'Sub-15', 'Sub-16'])
    expect(categorias.map((c) => c.entrenador?.apellido ?? null)).toEqual([null, null, null, 'Díaz', null, 'Mora', null, null, null, null])
    expect(categorias.find((c) => c.nombre === 'Párvulos')).toMatchObject({ edadMinima: 4, edadMaxima: 6 })
  })
})

describe('editarDatos (simulado)', () => {
  it('cambia los datos personales sin tocar categorías ni estado', async () => {
    const editado = await servicio.editarDatos(carlos.id, { ...carlos, nombre: 'Carlos Andrés', categorias: [] })
    expect(editado).toMatchObject({ nombre: 'Carlos Andrés', categorias: ['Cachorros', 'Sub-10', 'Sub-14', 'Sub-16'], activo: true })
  })
})
