import { describe, expect, it } from 'vitest'
import {
  MENSAJES_ERROR, MENSAJE_CORREO_NO_DISPONIBLE, MENSAJE_DOCUMENTO_REPETIDO, adaptarInscripcion, avisosDeInscripcion, cuerpoInscripcion,
  erroresDeInscripcionPorPaso, errorDeInscripcion, parentescoParaApi,
} from './inscripcionApi.js'

const jugador = {
  nombre: ' Mateo ', apellido: 'Ríos', tipoDocumento: 'Tarjeta de identidad', documento: '1234567890', fechaNacimiento: '2016-03-03',
  telefono: '3001234567', direccion: 'Calle 1 # 2-3', posicion: 'Delantero', grupoEntrenamiento: 'mañana',
}
const acudiente = { nombre: 'Ana', apellido: 'Ríos', correo: ' ana@correo.co ', telefono: '3001234567', parentesco: 'Madre' }
const dto = {
  deportista: { id: 'uuid-d', nombre: 'Mateo', apellido: 'Ríos', tipoDocumento: 'TI', documento: '1234567890', fechaNacimiento: '2016-03-03', edad: 10, telefono: '3001234567', direccion: 'Calle 1 # 2-3', posicion: 'Delantero', foto: null, categoria: 'Semillero', idCategoria: 'uuid-c', grupo: 'mañana', activo: true, estadoInscripcion: 'PENDIENTE' },
  acudiente: { id: 'uuid-a', nombre: 'Ana', apellido: 'Ríos', correo: 'ana@correo.co', reutilizado: false, cuentaCreada: true },
  parentesco: 'Madre',
  correoEnviado: true,
}
const apiError = (estado, mensaje, campos = [], codigo = null) => Object.assign(new Error(mensaje), { estado, mensaje, campos, codigo })

describe('parentesco', () => {
  it('Tía y Tío salen sin tilde, con y sin tilde y en cualquier mayúscula', () => {
    expect(parentescoParaApi('Tía')).toBe('Tia')
    expect(parentescoParaApi('Tia')).toBe('Tia')
    expect(parentescoParaApi('tío')).toBe('Tio')
    expect(parentescoParaApi('Tío')).toBe('Tio')
  })
  it('los demás pasan igual; uno desconocido se manda tal cual para que el backend lo rechace', () => {
    for (const p of ['Madre', 'Padre', 'Abuelo', 'Abuela', 'Otro']) expect(parentescoParaApi(p)).toBe(p)
    expect(parentescoParaApi(' Primo ')).toBe('Primo')
  })
})

describe('cuerpoInscripcion', () => {
  it('arma deportista y acudiente con el código del documento, sin categoría ni activo, y recorta', () => {
    expect(cuerpoInscripcion(jugador, acudiente)).toEqual({
      deportista: { nombre: 'Mateo', apellido: 'Ríos', tipoDocumento: 'TI', documento: '1234567890', fechaNacimiento: '2016-03-03', telefono: '3001234567', direccion: 'Calle 1 # 2-3', posicion: 'Delantero', grupo: 'mañana' },
      acudiente: { nombre: 'Ana', apellido: 'Ríos', correo: 'ana@correo.co', telefono: '3001234567', parentesco: 'Madre' },
    })
  })
  it('posición y grupo vacíos no se envían', () => {
    const { deportista } = cuerpoInscripcion({ ...jugador, posicion: ' ', grupoEntrenamiento: '' }, acudiente)
    expect(deportista).not.toHaveProperty('posicion')
    expect(deportista).not.toHaveProperty('grupo')
  })
  it('convierte Tía del formulario', () => {
    expect(cuerpoInscripcion(jugador, { ...acudiente, parentesco: 'Tía' }).acudiente.parentesco).toBe('Tia')
  })
})

describe('adaptarInscripcion', () => {
  it('mapea el 201: deportista con la forma de las pantallas, acudiente, parentesco y correoEnviado', () => {
    const r = adaptarInscripcion(dto)
    expect(r.deportista).toMatchObject({ id: 'uuid-d', tipoDocumento: 'Tarjeta de identidad', grupoEntrenamiento: 'mañana', categoria: 'Semillero', estadoInscripcion: 'PENDIENTE' })
    expect(r.acudiente).toEqual({ id: 'uuid-a', nombre: 'Ana', apellido: 'Ríos', correo: 'ana@correo.co', reutilizado: false, cuentaCreada: true })
    expect(r.parentesco).toBe('Madre')
    expect(r.correoEnviado).toBe(true)
  })
  it('el parentesco Tia/Tio se muestra con tilde', () => {
    expect(adaptarInscripcion({ ...dto, parentesco: 'Tia' }).parentesco).toBe('Tía')
  })
  it('correoEnviado conserva true, false y null', () => {
    expect(adaptarInscripcion({ ...dto, correoEnviado: false }).correoEnviado).toBe(false)
    expect(adaptarInscripcion({ ...dto, correoEnviado: null }).correoEnviado).toBeNull()
    expect(adaptarInscripcion({ ...dto, correoEnviado: undefined }).correoEnviado).toBeNull()
  })
  it('una respuesta sin deportista o sin acudiente es inválida', () => {
    expect(() => adaptarInscripcion({})).toThrow()
    expect(() => adaptarInscripcion({ deportista: dto.deportista })).toThrow()
  })
})

describe('avisosDeInscripcion', () => {
  const ana = { nombre: 'Ana', apellido: 'Ríos', correo: 'ana@correo.co', reutilizado: false }
  it('correoEnviado true: avisa que se envió el enlace al correo', () => {
    expect(avisosDeInscripcion({ acudiente: ana, correoEnviado: true })).toEqual([{ texto: 'Se envió el enlace para crear la contraseña a ana@correo.co.' }])
  })
  it('correoEnviado false: la inscripción quedó hecha pero el correo no salió', () => {
    const [aviso] = avisosDeInscripcion({ acudiente: ana, correoEnviado: false })
    expect(aviso.tono).toBe('danger')
    expect(aviso.texto).toBe('La inscripción quedó hecha, pero no se pudo enviar el correo con el enlace para crear la contraseña a ana@correo.co. Avísale al acudiente.')
    expect(aviso.texto).not.toMatch(/Olvidaste/)
  })
  it('correoEnviado null: no dice nada del correo', () => {
    expect(avisosDeInscripcion({ acudiente: ana, correoEnviado: null })).toEqual([])
  })
  it('acudiente reutilizado: aclara que ya existía y que sus datos no cambiaron', () => {
    const avisos = avisosDeInscripcion({ acudiente: { ...ana, reutilizado: true }, correoEnviado: null })
    expect(avisos).toHaveLength(1)
    expect(avisos[0].texto).toMatch(/Ana Ríos ya existía como acudiente.*sus datos no se modificaron/)
  })
})

describe('errores 400 y 409 con campos prefijados', () => {
  const mensaje = 'deportista.documento: es obligatorio; deportista.grupo: solo aplica en Cachorros y Semillero; acudiente.correo: no tiene formato válido; acudiente.nombre: es obligatorio'
  const campos = ['deportista.documento', 'deportista.grupo', 'acudiente.correo', 'acudiente.nombre']

  it('separa los mensajes por paso y traduce grupo al campo del formulario', () => {
    const { porPaso, otros } = erroresDeInscripcionPorPaso(apiError(400, mensaje, campos))
    expect(porPaso).toEqual({
      1: { documento: 'Es obligatorio.', grupoEntrenamiento: 'Solo aplica en Cachorros y Semillero.' },
      2: { correo: 'No tiene formato válido.', nombre: 'Es obligatorio.' },
    })
    expect(otros).toBe(false)
  })
  it('un texto con "; " dentro no se corta', () => {
    const { porPaso } = erroresDeInscripcionPorPaso(apiError(400, 'deportista.fechaNacimiento: tiene 17 años; el club solo inscribe de 4 a 16; acudiente.correo: es obligatorio', ['deportista.fechaNacimiento', 'acudiente.correo']))
    expect(porPaso[1].fechaNacimiento).toBe('Tiene 17 años; el club solo inscribe de 4 a 16.')
    expect(porPaso[2].correo).toBe('Es obligatorio.')
  })
  it('errorDeInscripcion lleva al primer paso con error y trae todos los pasos', () => {
    const e = errorDeInscripcion(apiError(400, mensaje, campos))
    expect(e).toMatchObject({ codigo: 'VALIDACION', paso: 1, errores: { documento: 'Es obligatorio.' } })
    expect(e.erroresPorPaso[2].correo).toBe('No tiene formato válido.')
  })
  it('si solo falla el acudiente, el primer paso con error es el 2', () => {
    const e = errorDeInscripcion(apiError(400, 'acudiente.parentesco: es obligatorio', ['acudiente.parentesco']))
    expect(e).toMatchObject({ paso: 2, errores: { parentesco: 'Es obligatorio.' } })
  })
  it('409 de documento: mensaje propio en el paso 1', () => {
    const e = errorDeInscripcion(apiError(409, 'deportista.documento: ya está registrado', ['deportista.documento']))
    expect(e).toMatchObject({ codigo: 'DOCUMENTO_DUPLICADO', paso: 1, errores: { documento: MENSAJE_DOCUMENTO_REPETIDO } })
  })
  it('409 de correo: mensaje propio en el paso 2', () => {
    const e = errorDeInscripcion(apiError(409, 'acudiente.correo: ese correo ya pertenece a una cuenta con otro rol', ['acudiente.correo']))
    expect(e).toMatchObject({ codigo: 'CORREO_NO_DISPONIBLE', paso: 2, errores: { correo: MENSAJE_CORREO_NO_DISPONIBLE } })
  })
  it('un campo sin texto en el mensaje muestra un texto genérico, nunca el mensaje completo', () => {
    const { porPaso } = erroresDeInscripcionPorPaso(apiError(400, 'algo raro', ['acudiente.telefono']))
    expect(porPaso[2].telefono).toBe('Revisa este campo.')
  })
  it('un 400 sin campos del formulario ("cuerpo") es un aviso general', () => {
    const e = errorDeInscripcion(apiError(400, 'cuerpo: obligatorio', ['cuerpo']))
    expect(e).toMatchObject({ codigo: 'ERROR', mensajeUsuario: MENSAJES_ERROR.REVISAR })
    expect(e.paso).toBeUndefined()
  })
})

describe('errores generales: amables y sin texto técnico', () => {
  const tecnico = 'org.postgresql.util.PSQLException: SQL [insert into deportista] uuid 123e4567-e89b-12d3-a456-426614174000'
  it('403 sin permiso y 403 por 2FA', () => {
    expect(errorDeInscripcion(apiError(403, tecnico, [], 'PERMISOS'))).toMatchObject({ codigo: 'SIN_PERMISO', mensajeUsuario: MENSAJES_ERROR.PERMISO })
    expect(errorDeInscripcion(apiError(403, tecnico, [], '2FA_REQUERIDA'))).toMatchObject({ codigo: '2FA_REQUERIDA', mensajeUsuario: MENSAJES_ERROR.DOS_PASOS })
  })
  it('503, 401, sin conexión y 500', () => {
    expect(errorDeInscripcion(apiError(503, tecnico)).mensajeUsuario).toBe(MENSAJES_ERROR.NO_DISPONIBLE)
    expect(errorDeInscripcion(apiError(401, tecnico)).mensajeUsuario).toBe(MENSAJES_ERROR.SESION)
    expect(errorDeInscripcion(apiError(0, 'x', [], 'RED')).mensajeUsuario).toBe(MENSAJES_ERROR.SIN_CONEXION)
    expect(errorDeInscripcion(apiError(500, tecnico)).mensajeUsuario).toBe(MENSAJES_ERROR.GENERICO)
    expect(errorDeInscripcion(new TypeError('boom')).mensajeUsuario).toBe(MENSAJES_ERROR.GENERICO)
  })
  it('ningún mensaje al usuario trae texto del servidor', () => {
    for (const estado of [0, 400, 401, 403, 409, 500, 503]) {
      const e = errorDeInscripcion(apiError(estado, tecnico))
      expect(`${e.message} ${e.mensajeUsuario}`).not.toMatch(/SQL|uuid|Exception|123e4567/i)
    }
  })
})
