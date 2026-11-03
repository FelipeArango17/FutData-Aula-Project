import { describe, expect, it } from 'vitest'
import { adaptarUsuario, cuerpoRol, cuerpoUsuario, errorDeUsuarios } from './usuariosApi.js'

const ID = '0b8e1f6a-2c3d-4e5f-9a8b-7c6d5e4f3a2b'
const dto = (extra = {}) => ({ id: ID, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@pradofc.co', rol: 'TESORERO', activo: true, creadoEn: '2026-09-01T10:00:00-05:00', ...extra })
const errorApi = (estado, mensaje, campos = []) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo: null })

describe('adaptarUsuario', () => {
  it('pasa el rol a minúsculas, sin importar cómo llegue', () => {
    expect(adaptarUsuario(dto()).rol).toBe('tesorero')
    expect(adaptarUsuario(dto({ rol: 'administrador' })).rol).toBe('administrador')
  })
  it('conserva id UUID, activo y fecha; creadoPor es null', () => {
    expect(adaptarUsuario(dto({ activo: false }))).toEqual({
      id: ID, nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@pradofc.co', rol: 'tesorero', activo: false, creadoPor: null, creadoEn: '2026-09-01T10:00:00-05:00',
    })
  })
})

describe('cuerpos', () => {
  it('cuerpoUsuario limpia los textos y manda el rol en mayúsculas', () => {
    expect(cuerpoUsuario({ nombre: ' Ana ', apellido: 'Ruiz ', correo: ' ana@pradofc.co', rol: 'acudiente' })).toEqual({
      nombre: 'Ana', apellido: 'Ruiz', correo: 'ana@pradofc.co', rol: 'ACUDIENTE',
    })
  })
  it('cuerpoRol manda solo el rol en mayúsculas', () => {
    expect(cuerpoRol('entrenador')).toEqual({ rol: 'ENTRENADOR' })
    expect(cuerpoRol('ENTRENADOR')).toEqual({ rol: 'ENTRENADOR' })
  })
})

describe('errorDeUsuarios', () => {
  it('correo duplicado (409 con campos ["correo"]) marca el campo con texto propio', () => {
    const e = errorDeUsuarios(errorApi(409, 'correo: ya está registrado en otro usuario', ['correo']))
    expect(e.codigo).toBe('VALIDACION')
    expect(e.errores).toEqual({ correo: 'Ya existe un usuario con ese correo.' })
  })
  it('400 con campos separa el mensaje por campo', () => {
    const e = errorDeUsuarios(errorApi(400, 'nombre: es obligatorio; rol: no es válido', ['nombre', 'rol']))
    expect(e.errores).toEqual({ nombre: 'Es obligatorio.', rol: 'No es válido.' })
  })
  it('un 409 de último administrador conserva el mensaje del backend', () => {
    const e = errorDeUsuarios(errorApi(409, 'Debe existir al menos un administrador activo.', ['activo']))
    expect(e.codigo).toBe('VALIDACION')
    expect(e.message).toBe('Debe existir al menos un administrador activo.')
    expect(e.errores.activo).toBe('Debe existir al menos un administrador activo.')
  })
  it('409 sin campos es CONFLICTO, 404 y 403 tienen su código, y los demás no cambian', () => {
    expect(errorDeUsuarios(errorApi(409, 'x')).codigo).toBe('CONFLICTO')
    expect(errorDeUsuarios(errorApi(404, 'No existe un usuario con ese id.')).codigo).toBe('USUARIO_NO_EXISTE')
    expect(errorDeUsuarios(errorApi(403, 'x')).codigo).toBe('SIN_PERMISO')
    expect(errorDeUsuarios(Object.assign(errorApi(0, 'x'), { codigo: 'RED' })).codigo).toBe('RED')
  })
})
