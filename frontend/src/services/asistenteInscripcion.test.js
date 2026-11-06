import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CLAVE_FOTO, ESTADO_INICIAL, PASOS, primerPasoConErrores, reducirAsistente, validarPaso } from './asistenteInscripcion.js'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
})
afterEach(() => vi.useRealTimers())

const archivo = (extra = {}) => ({ name: 'doc.pdf', type: 'application/pdf', size: 100 * 1024, ...extra })
const jugadorOk = { nombre: 'Lucas', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento: '2012-05-10', telefono: '3001234567', direccion: 'Calle 50 # 10-20', grupoEntrenamiento: '', posicion: '' }
const acudienteOk = { nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia@correo.com', telefono: '3001234567', parentesco: 'Madre' }
const aplicar = (estado, ...acciones) => acciones.reduce(reducirAsistente, estado)
const llenar = (campos, tipo) => Object.entries(campos).map(([campo, valor]) => ({ tipo, campo, valor }))

describe('el asistente', () => {
  it('son 4 pasos y arranca en blanco, sin datos escritos de antemano', () => {
    expect(PASOS).toEqual(['Jugador', 'Acudiente', 'Documentos', 'Confirmar'])
    expect(ESTADO_INICIAL.paso).toBe(1)
    expect(Object.values(ESTADO_INICIAL.jugador).every((v) => v === '')).toBe(true)
    expect(Object.values(ESTADO_INICIAL.acudiente).every((v) => v === '')).toBe(true)
    expect(JSON.stringify(ESTADO_INICIAL)).not.toMatch(/Nicolás Mejía|Claudia Ruiz/)
  })
})

describe('validarPaso', () => {
  it('paso 1: todos los obligatorios vacíos fallan; con datos correctos pasa', () => {
    const errores = validarPaso(1, ESTADO_INICIAL)
    for (const campo of ['nombre', 'apellido', 'tipoDocumento', 'documento', 'fechaNacimiento', 'telefono', 'direccion']) expect(errores[campo], campo).toBeTruthy()
    expect(validarPaso(1, { ...ESTADO_INICIAL, jugador: jugadorOk })).toEqual({})
  })

  it('paso 1: la fecha fuera de rango (2009, 2023) falla, y el grupo es obligatorio solo en Cachorros y Semillero', () => {
    for (const fecha of ['2009-12-31', '2023-01-01']) expect(validarPaso(1, { ...ESTADO_INICIAL, jugador: { ...jugadorOk, fechaNacimiento: fecha } }).fechaNacimiento, fecha).toMatch(/Solo se inscriben deportistas nacidos entre 2010 y 2022/)
    expect(validarPaso(1, { ...ESTADO_INICIAL, jugador: { ...jugadorOk, fechaNacimiento: '2017-05-10' } })).toEqual({ grupoEntrenamiento: 'Selecciona el grupo de entrenamiento: mañana o tarde.' })
    expect(validarPaso(1, { ...ESTADO_INICIAL, jugador: { ...jugadorOk, fechaNacimiento: '2017-05-10', grupoEntrenamiento: 'tarde' } })).toEqual({})
  })

  it('paso 2: nombres, apellidos, correo, teléfono y parentesco con los validadores compartidos', () => {
    expect(Object.keys(validarPaso(2, ESTADO_INICIAL)).sort()).toEqual(['apellido', 'correo', 'nombre', 'parentesco', 'telefono'])
    expect(validarPaso(2, { ...ESTADO_INICIAL, acudiente: acudienteOk })).toEqual({})
    expect(validarPaso(2, { ...ESTADO_INICIAL, acudiente: { ...acudienteOk, correo: 'no-es-correo', telefono: '123', parentesco: 'Vecino' } })).toEqual({
      correo: expect.any(String), telefono: expect.any(String), parentesco: 'Selecciona el parentesco.',
    })
  })

  it('paso 3: es opcional (sin archivos pasa) y revisa los archivos que haya', () => {
    expect(validarPaso(3, ESTADO_INICIAL)).toEqual({})
    const malo = validarPaso(3, { ...ESTADO_INICIAL, documentos: { PAZ_Y_SALVO: archivo({ type: 'image/gif' }) }, foto: archivo({ type: 'application/pdf' }) })
    expect(malo['documento-PAZ_Y_SALVO']).toMatch(/Solo se aceptan archivos/)
    expect(malo[CLAVE_FOTO]).toMatch(/JPG o PNG/)
  })

  it('paso 4 no tiene campos, y primerPasoConErrores señala el primer paso malo', () => {
    expect(validarPaso(4, ESTADO_INICIAL)).toEqual({})
    expect(primerPasoConErrores(ESTADO_INICIAL)).toBe(1)
    expect(primerPasoConErrores({ ...ESTADO_INICIAL, jugador: jugadorOk })).toBe(2)
    expect(primerPasoConErrores({ ...ESTADO_INICIAL, jugador: jugadorOk, acudiente: acudienteOk })).toBeNull()
  })
})

describe('reducirAsistente: navegar sin perder datos', () => {
  it('no avanza con errores: se queda, guarda los errores y cuenta el intento (para mandar el foco)', () => {
    const e = aplicar(ESTADO_INICIAL, { tipo: 'continuar' })
    expect(e.paso).toBe(1)
    expect(e.errores.nombre).toBeTruthy()
    expect(e.intentos).toBe(1)
  })

  it('con el paso correcto avanza y limpia los errores; escribir en un campo quita su error', () => {
    let e = aplicar(ESTADO_INICIAL, { tipo: 'continuar' }, { tipo: 'jugador', campo: 'nombre', valor: 'Lucas' })
    expect(e.errores.nombre).toBeUndefined()
    expect(e.errores.apellido).toBeTruthy()
    e = aplicar(ESTADO_INICIAL, ...llenar(jugadorOk, 'jugador'), { tipo: 'continuar' })
    expect(e.paso).toBe(2)
    expect(e.errores).toEqual({})
  })

  it('los datos se conservan al ir atrás y volver, y con los "Editar" del resumen', () => {
    let e = aplicar(ESTADO_INICIAL, ...llenar(jugadorOk, 'jugador'), { tipo: 'continuar' }, ...llenar(acudienteOk, 'acudiente'), { tipo: 'continuar' })
    e = aplicar(e, { tipo: 'documento', idTipo: 'PAZ_Y_SALVO', archivo: archivo() }, { tipo: 'foto', archivo: archivo({ name: 'f.png', type: 'image/png' }) }, { tipo: 'continuar' })
    expect(e.paso).toBe(4)
    e = aplicar(e, { tipo: 'ir', paso: 1 })
    expect(e.paso).toBe(1)
    expect(e.jugador).toMatchObject(jugadorOk)
    e = aplicar(e, { tipo: 'continuar' }, { tipo: 'atras' }, { tipo: 'atras' })
    expect(e.paso).toBe(1)
    expect(e.acudiente).toMatchObject(acudienteOk)
    expect(e.documentos.PAZ_Y_SALVO.name).toBe('doc.pdf')
    expect(e.foto.name).toBe('f.png')
  })

  it('no se puede saltar hacia adelante ni pasar de 4 ni bajar de 1', () => {
    expect(aplicar(ESTADO_INICIAL, { tipo: 'ir', paso: 3 }).paso).toBe(1)
    expect(aplicar(ESTADO_INICIAL, { tipo: 'atras' }).paso).toBe(1)
    const e = aplicar(ESTADO_INICIAL, ...llenar(jugadorOk, 'jugador'), { tipo: 'continuar' }, ...llenar(acudienteOk, 'acudiente'), { tipo: 'continuar' }, { tipo: 'continuar' }, { tipo: 'continuar' })
    expect(e.paso).toBe(4)
  })

  it('el grupo se limpia si la fecha cambia a una categoría sin grupo', () => {
    const e = aplicar(ESTADO_INICIAL, ...llenar({ ...jugadorOk, fechaNacimiento: '2017-05-10', grupoEntrenamiento: 'tarde' }, 'jugador'))
    expect(e.jugador.grupoEntrenamiento).toBe('tarde')
    expect(aplicar(e, { tipo: 'jugador', campo: 'fechaNacimiento', valor: '2012-05-10' }).jugador.grupoEntrenamiento).toBe('')
  })

  it('un archivo inválido no se guarda y muestra su error al instante; quitar un documento lo borra', () => {
    let e = aplicar(ESTADO_INICIAL, { tipo: 'documento', idTipo: 'CARTA_RENUNCIA', archivo: archivo({ size: 6 * 1024 * 1024 }) })
    expect(e.documentos.CARTA_RENUNCIA).toBeUndefined()
    expect(e.errores['documento-CARTA_RENUNCIA']).toMatch(/máximo es 5 MB/)
    e = aplicar(e, { tipo: 'documento', idTipo: 'CARTA_RENUNCIA', archivo: archivo() })
    expect(e.errores['documento-CARTA_RENUNCIA']).toBeUndefined()
    expect(e.documentos.CARTA_RENUNCIA).toBeTruthy()
    expect(aplicar(e, { tipo: 'documento', idTipo: 'CARTA_RENUNCIA', archivo: null }).documentos).toEqual({})
  })

  it('un error del servidor vuelve al paso indicado con sus mensajes', () => {
    const e = aplicar(ESTADO_INICIAL, { tipo: 'errorDelServidor', paso: 1, errores: { documento: 'Ya existe un deportista registrado con este documento.' } })
    expect(e.paso).toBe(1)
    expect(e.errores.documento).toMatch(/Ya existe/)
    expect(e.intentos).toBe(1)
  })

  it('con errores de varios pasos: muestra los del primero y los del otro al llegar a él (los campos pueden llamarse igual)', () => {
    const porPaso = { 1: { nombre: 'Del jugador.' }, 2: { nombre: 'Del acudiente.', correo: 'Mal.' } }
    let e = aplicar({ ...ESTADO_INICIAL, paso: 4 }, { tipo: 'errorDelServidor', paso: 1, errores: porPaso[1], porPaso })
    expect(e).toMatchObject({ paso: 1, errores: { nombre: 'Del jugador.' } })
    e = { ...e, jugador: { ...e.jugador, ...jugadorOk } }
    e = aplicar({ ...e, errores: {} }, { tipo: 'continuar' })
    expect(e).toMatchObject({ paso: 2, errores: { nombre: 'Del acudiente.', correo: 'Mal.' }, erroresServidor: {} })
    expect(e.intentos).toBeGreaterThan(1)
  })
})
