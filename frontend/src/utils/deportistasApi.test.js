import { describe, expect, it } from 'vitest'
import {
  adaptarDeportista, comoFormulario, cuerpoDeportista, errorDeDeportistas, filtrarPorTexto, paginaDelServidor, parametrosLista, traeDocumento,
} from './deportistasApi.js'

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
const CAT = '0a0a0a0a-1111-4222-8333-444444444444'
const dtoAdmin = (extra = {}) => ({
  id: ID, nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'TI', documento: '1098765432', fechaNacimiento: '2016-08-20', edad: 10,
  telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: 'Delantera', foto: 'https://x/firmada?token=1', categoria: 'Semillero',
  idCategoria: CAT, grupo: 'tarde', activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
// Lo que recibe el ENTRENADOR: sin documento ni estadoInscripcion (ni la clave) y con teléfono y dirección en null
const dtoEntrenador = () => {
  const { documento: _d, estadoInscripcion: _e, ...resto } = dtoAdmin() // eslint-disable-line no-unused-vars
  return { ...resto, telefono: null, direccion: null }
}

describe('adaptarDeportista', () => {
  it('administrador: conserva el id UUID (texto), el tipo en español, grupo y la URL firmada de la foto', () => {
    expect(adaptarDeportista(dtoAdmin())).toEqual({
      id: ID, nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1098765432', fechaNacimiento: '2016-08-20', edad: 10,
      telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: 'Delantera', foto: 'https://x/firmada?token=1', categoria: 'Semillero',
      idCategoria: CAT, grupoEntrenamiento: 'tarde', activo: true, estadoInscripcion: 'PENDIENTE',
    })
  })
  it('los cuatro tipos de documento (sin CC) vuelven con su nombre', () => {
    const nombre = (tipo) => adaptarDeportista(dtoAdmin({ tipoDocumento: tipo })).tipoDocumento
    expect([nombre('TI'), nombre('RC'), nombre('CE'), nombre('PASAPORTE')]).toEqual(['Tarjeta de identidad', 'Registro civil', 'Cédula de extranjería', 'Pasaporte'])
  })
  it('entrenador: no aparecen documento ni estadoInscripcion (ni la clave) y teléfono y dirección quedan vacíos', () => {
    const e = adaptarDeportista(dtoEntrenador())
    expect(e).not.toHaveProperty('documento')
    expect(e).not.toHaveProperty('estadoInscripcion')
    expect(e).toMatchObject({ telefono: '', direccion: '', categoria: 'Semillero', grupoEntrenamiento: 'tarde', foto: 'https://x/firmada?token=1' })
  })
  it('fuera de 4 a 16 años: categoría e idCategoria null; sin foto ni grupo: null', () => {
    expect(adaptarDeportista(dtoAdmin({ categoria: null, idCategoria: null, grupo: null, foto: null }))).toMatchObject({ categoria: null, idCategoria: null, grupoEntrenamiento: null, foto: null })
  })
})

describe('cuerpoDeportista', () => {
  const formulario = {
    nombre: ' Ana ', apellido: 'Pérez', tipoDocumento: 'Registro civil', documento: ' 1098765432 ', fechaNacimiento: '2018-04-10',
    telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: '', grupoEntrenamiento: 'tarde', idCategoria: 'x', id: 'y',
  }
  it('manda el código del tipo, recorta, vacíos -> null y NO manda la categoría ni el id', () => {
    expect(cuerpoDeportista(formulario)).toEqual({
      nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'RC', documento: '1098765432', fechaNacimiento: '2018-04-10',
      telefono: '3001234567', direccion: 'Calle 10 # 20-30', posicion: null, grupo: 'tarde',
    })
  })
  it('`activo` solo va cuando se pide (PUT)', () => {
    expect(cuerpoDeportista(formulario)).not.toHaveProperty('activo')
    expect(cuerpoDeportista(formulario, { activo: false })).toHaveProperty('activo', false)
  })
  it('sin grupo: null', () => {
    expect(cuerpoDeportista({ ...formulario, grupoEntrenamiento: '' }).grupo).toBeNull()
  })
  it('leído y vuelto a armar da el mismo cuerpo (sirve para detectar "sin cambios")', () => {
    const leido = adaptarDeportista(dtoAdmin())
    expect(cuerpoDeportista(comoFormulario(leido), { activo: true })).toEqual({
      nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'TI', documento: '1098765432', fechaNacimiento: '2016-08-20', telefono: '3001234567',
      direccion: 'Calle 10 # 20-30', posicion: 'Delantera', grupo: 'tarde', activo: true,
    })
  })
})

describe('parametrosLista y paginación', () => {
  it('estado -> activo; categoría por nombre', () => {
    expect(parametrosLista({ pagina: 2, tamano: 10, estado: 'activos', categoria: 'Sub-12' })).toEqual({ page: 2, size: 10, sort: undefined, activo: true, categoria: 'Sub-12' })
    expect(parametrosLista({ estado: 'inactivos' }).activo).toBe(false)
    expect(parametrosLista({ estado: 'todos' }).activo).toBeUndefined()
  })
  it('paginaDelServidor calcula páginas y el "Mostrando 11–20 de 23"', () => {
    const items = Array.from({ length: 10 }, (_, i) => ({ id: i }))
    expect(paginaDelServidor({ items, total: 23, pagina: 2, tamano: 10 })).toMatchObject({ pagina: 2, totalPaginas: 3, desde: 11, hasta: 20, total: 23 })
    expect(paginaDelServidor({ items: [], total: 0, pagina: 1, tamano: 10 })).toMatchObject({ totalPaginas: 1, desde: 0, hasta: 0 })
  })
})

describe('filtrarPorTexto y traeDocumento', () => {
  const lista = [{ nombre: 'Ana', apellido: 'Ríos', documento: '111' }, { nombre: 'Luis', apellido: 'Pérez', documento: '222' }]
  it('busca por nombre (sin tildes ni mayúsculas) o por documento', () => {
    expect(filtrarPorTexto(lista, 'RIOS')).toHaveLength(1)
    expect(filtrarPorTexto(lista, '222')[0].nombre).toBe('Luis')
    expect(filtrarPorTexto(lista, '')).toBe(lista)
  })
  it('sin documento (entrenador) busca solo por nombre y no rompe', () => {
    const sin = [{ nombre: 'Ana', apellido: 'Ríos' }]
    expect(filtrarPorTexto(sin, '111')).toHaveLength(0)
    expect(filtrarPorTexto(sin, 'ana')).toHaveLength(1)
    expect(traeDocumento(sin)).toBe(false)
    expect(traeDocumento(lista)).toBe(true)
  })
})

describe('errorDeDeportistas', () => {
  const api = (estado, mensaje, campos = [], extra = {}) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null, ...extra })
  it('400 con campos: VALIDACION con el texto del backend por campo (grupo -> grupoEntrenamiento)', () => {
    const e = errorDeDeportistas(api(400, 'fechaNacimiento: por año de nacimiento tiene 17 años en 2026; el club solo inscribe de 4 a 16 años; grupo: solo Cachorros y Semillero', ['fechaNacimiento', 'grupo']))
    expect(e.codigo).toBe('VALIDACION')
    expect(e.errores.fechaNacimiento).toMatch(/solo inscribe de 4 a 16/)
    expect(e.errores.grupoEntrenamiento).toBeDefined()
    expect(e.estado).toBe(400)
  })
  it('409 de documento: DOCUMENTO_DUPLICADO con el mensaje del backend (sin decir de quién es)', () => {
    const e = errorDeDeportistas(api(409, 'documento: ya está registrado en otro deportista', ['documento']))
    expect(e.codigo).toBe('DOCUMENTO_DUPLICADO')
    expect(e.message).toBe('Ya está registrado en otro deportista.')
  })
  it('403 -> SIN_PERMISO y 404 -> DEPORTISTA_NO_EXISTE, conservando el mensaje', () => {
    expect(errorDeDeportistas(api(403, 'No tienes permiso para hacer esta acción.', [], { codigo: 'PERMISOS' }))).toMatchObject({ codigo: 'SIN_PERMISO', mensaje: 'No tienes permiso para hacer esta acción.' })
    expect(errorDeDeportistas(api(404, 'No existe un deportista con ese id.'))).toMatchObject({ codigo: 'DEPORTISTA_NO_EXISTE' })
    expect(errorDeDeportistas(api(404, 'x'), { recurso: 'categoria' }).codigo).toBe('CATEGORIA_NO_EXISTE')
  })
  it('400 sin campos (id que no es UUID), 503 y red quedan tal cual', () => {
    expect(errorDeDeportistas(api(400, 'id inválido')).codigo).toBeNull()
    expect(errorDeDeportistas(api(503, 'Storage no responde')).codigo).toBeNull()
    const sinEstado = new Error('x')
    expect(errorDeDeportistas(sinEstado)).toBe(sinEstado)
  })
})
