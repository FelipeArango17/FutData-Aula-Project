import { describe, expect, it } from 'vitest'
import {
  adaptarFichaJugador, adaptarMiJugador, adaptarMisJugadores, esErrorSinReintento, fechaNacimientoLegible, mensajeDeErrorAcudiente, nombreCompleto,
} from './acudientesApi.js'

const ID = '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b'
// Forma real de MiJugadorResponseDTO
const dto = (extra = {}) => ({
  id: ID, nombre: 'Santiago', apellido: 'Ríos', fechaNacimiento: '2018-03-14', edad: 8, categoria: 'Semillero', idCategoria: 'c1', grupo: 'mañana',
  posicion: 'Delantero', foto: 'https://firmada/x.jpg', parentesco: 'Tia', activo: true, estadoInscripcion: 'PENDIENTE', ...extra,
})
const ficha = (extra = {}) => ({
  ...dto(), tipoDocumento: 'TI', documento: '1023456789', telefono: '3001234567', direccion: 'Calle 1 # 2-3',
  documentosFaltantes: ['CERTIFICADO_ESTUDIO', 'AUTORIZACION_HABEAS_DATA'], ...extra,
})
const error = (estado, mensaje = 'No se pudo completar la operación.', codigo = null) => Object.assign(new Error(mensaje), { estado, mensaje, codigo, campos: [] })

describe('adaptarMiJugador', () => {
  it('deja el id como texto, pone la tilde al parentesco y renombra grupo', () => {
    expect(adaptarMiJugador(dto())).toEqual({
      id: ID, nombre: 'Santiago', apellido: 'Ríos', fechaNacimiento: '2018-03-14', edad: 8, categoria: 'Semillero', grupoEntrenamiento: 'mañana',
      posicion: 'Delantero', foto: 'https://firmada/x.jpg', parentesco: 'Tía', activo: true, estadoInscripcion: 'PENDIENTE',
    })
  })

  it('lo nulo queda en null (categoría fuera de rango, sin foto ni parentesco)', () => {
    const j = adaptarMiJugador(dto({ categoria: null, idCategoria: null, grupo: null, posicion: null, foto: null, parentesco: null }))
    expect(j).toMatchObject({ categoria: null, grupoEntrenamiento: null, posicion: null, foto: null, parentesco: null })
  })

  it('COMPLETA se conserva y cualquier otro valor cuenta como PENDIENTE', () => {
    expect(adaptarMiJugador(dto({ estadoInscripcion: 'COMPLETA' })).estadoInscripcion).toBe('COMPLETA')
    expect(adaptarMiJugador(dto({ estadoInscripcion: undefined })).estadoInscripcion).toBe('PENDIENTE')
  })

  it('un jugador inactivo conserva activo=false', () => {
    expect(adaptarMiJugador(dto({ activo: false })).activo).toBe(false)
  })

  it('adaptarMisJugadores tolera una respuesta que no es lista', () => {
    expect(adaptarMisJugadores([dto()])).toHaveLength(1)
    expect(adaptarMisJugadores(null)).toEqual([])
  })
})

describe('adaptarFichaJugador', () => {
  it('trae documento, contacto y los faltantes con su nombre corto', () => {
    const f = adaptarFichaJugador(ficha())
    expect(f).toMatchObject({ tipoDocumento: 'Tarjeta de identidad', documento: '1023456789', telefono: '3001234567', direccion: 'Calle 1 # 2-3' })
    expect(f.documentosFaltantes).toEqual([
      { tipo: 'CERTIFICADO_ESTUDIO', nombre: 'Certificado de estudio' },
      { tipo: 'AUTORIZACION_HABEAS_DATA', nombre: 'Habeas Data' },
    ])
  })

  it('sin faltantes (inscripción completa) la lista queda vacía; un tipo desconocido se muestra tal cual', () => {
    expect(adaptarFichaJugador(ficha({ documentosFaltantes: [] })).documentosFaltantes).toEqual([])
    expect(adaptarFichaJugador(ficha({ documentosFaltantes: undefined })).documentosFaltantes).toEqual([])
    expect(adaptarFichaJugador(ficha({ documentosFaltantes: ['OTRO'] })).documentosFaltantes).toEqual([{ tipo: 'OTRO', nombre: 'OTRO' }])
  })

  it('teléfono y dirección vacíos o nulos quedan en null', () => {
    expect(adaptarFichaJugador(ficha({ telefono: null, direccion: '  ' }))).toMatchObject({ telefono: null, direccion: null })
  })
})

describe('textos', () => {
  it('fechaNacimientoLegible', () => {
    expect(fechaNacimientoLegible('2018-03-14')).toBe('14 de marzo de 2018')
    expect(fechaNacimientoLegible('2018-12-01')).toBe('1 de diciembre de 2018')
    expect(fechaNacimientoLegible(null)).toBeNull()
    expect(fechaNacimientoLegible('14/03/2018')).toBeNull()
  })

  it('nombreCompleto', () => {
    expect(nombreCompleto({ nombre: 'Ana', apellido: 'Rojas' })).toBe('Ana Rojas')
  })
})

describe('mensajeDeErrorAcudiente', () => {
  it('el 403 usa el mensaje del servidor si viene', () => {
    expect(mensajeDeErrorAcudiente(error(403, 'Verifica tu segundo factor.', '2FA_REQUERIDA'), 'x')).toBe('Verifica tu segundo factor.')
  })

  it('el 404 nunca usa el texto del servidor (trae "id")', () => {
    expect(mensajeDeErrorAcudiente(error(404, 'No existe un deportista con ese id.'), 'x')).toBe('No encontramos ese jugador entre los tuyos.')
    expect(mensajeDeErrorAcudiente(error(404, 'Texto cualquiera.'), 'x')).toBe('No encontramos ese jugador entre los tuyos.')
  })

  it('un texto técnico del servidor en 403 se reemplaza por el genérico', () => {
    for (const texto of ['Falta el id del usuario.', 'UUID inválido', 'Error en /api/acudientes', 'NullPointerException']) {
      expect(mensajeDeErrorAcudiente(error(403, texto), 'x')).toBe('No tienes permiso para ver esta información.')
    }
  })

  it('sin mensaje propio del servidor usa un texto en español', () => {
    expect(mensajeDeErrorAcudiente(error(404), 'x')).toBe('No encontramos ese jugador entre los tuyos.')
    expect(mensajeDeErrorAcudiente(error(403), 'x')).toBe('No tienes permiso para ver esta información.')
    expect(mensajeDeErrorAcudiente(error(400, 'id: no es un UUID'), 'x')).toBe('No encontramos ese jugador entre los tuyos.')
  })

  it('sin conexión, sesión vencida, servicio caído y genérico', () => {
    expect(mensajeDeErrorAcudiente(error(0, 'x', 'RED'), 'x')).toContain('No pudimos conectar')
    expect(mensajeDeErrorAcudiente(error(401), 'x')).toContain('sesión venció')
    expect(mensajeDeErrorAcudiente(error(503), 'x')).toContain('no está disponible')
    expect(mensajeDeErrorAcudiente(error(500), 'No se pudieron cargar tus jugadores.')).toBe('No se pudieron cargar tus jugadores.')
  })

  it('reintentar solo sirve en fallos pasajeros', () => {
    expect([400, 403, 404].map((e) => esErrorSinReintento(error(e)))).toEqual([true, true, true])
    expect([0, 500, 503].map((e) => esErrorSinReintento(error(e)))).toEqual([false, false, false])
  })
})
