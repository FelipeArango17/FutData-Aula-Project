import { describe, expect, it } from 'vitest'
import { erroresPorCampo } from './erroresApi.js'

const DUPLICADOS = { documento: 'Ya existe un entrenador con ese documento.', correo: 'Ya existe un entrenador con ese correo.' }

describe('erroresPorCampo', () => {
  it('un 400 con varios campos reparte el texto de cada uno', () => {
    const error = {
      estado: 400,
      mensaje: 'fechaNacimiento: es obligatoria; telefono: debe tener exactamente 10 dígitos, sin espacios',
      campos: ['fechaNacimiento', 'telefono'],
    }
    expect(erroresPorCampo(error)).toEqual({
      fechaNacimiento: 'Es obligatoria.',
      telefono: 'Debe tener exactamente 10 dígitos, sin espacios.',
    })
  })

  it('un 409 de duplicado usa el texto propio del campo', () => {
    const error = { estado: 409, mensaje: 'documento: ya está registrado en otro entrenador', campos: ['documento'] }
    expect(erroresPorCampo(error, { duplicados: DUPLICADOS })).toEqual({ documento: DUPLICADOS.documento })
  })

  it('documento y correo duplicados a la vez marcan los dos', () => {
    const error = { estado: 409, mensaje: 'documento: ya está registrado; correo: ya está registrado', campos: ['documento', 'correo'] }
    expect(erroresPorCampo(error, { duplicados: DUPLICADOS })).toEqual(DUPLICADOS)
  })

  it('un 400 sobre un campo que tiene texto de duplicado NO usa ese texto', () => {
    const error = { estado: 400, mensaje: 'documento: debe tener entre 6 y 12 dígitos', campos: ['documento'] }
    expect(erroresPorCampo(error, { duplicados: DUPLICADOS })).toEqual({ documento: 'Debe tener entre 6 y 12 dígitos.' })
  })

  it('un campo sin su propia parte en el mensaje recibe el mensaje completo', () => {
    const error = { estado: 409, mensaje: 'Cachorros ya tiene otro entrenador. Confirma la reasignación.', campos: ['categorias'] }
    expect(erroresPorCampo(error)).toEqual({ categorias: 'Cachorros ya tiene otro entrenador. Confirma la reasignación.' })
  })

  it('un error general (sin campos: 403, red, 500) devuelve {}', () => {
    expect(erroresPorCampo({ estado: 403, mensaje: 'No tienes permiso para hacer esta acción.', campos: [] })).toEqual({})
    expect(erroresPorCampo({ estado: 0, codigo: 'RED' })).toEqual({})
    expect(erroresPorCampo(null)).toEqual({})
    expect(erroresPorCampo(new Error('x'))).toEqual({})
  })
})
