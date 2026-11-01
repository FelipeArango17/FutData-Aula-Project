import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Los servicios guardan los datos en memoria a nivel de módulo: cada prueba carga una copia nueva de los dos.
let inscripcion
let deportistas
beforeEach(async () => {
  vi.useFakeTimers()
  // La validación usa hoyLocal(): el reloj queda en el "hoy" de los datos de ejemplo
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
  vi.resetModules()
  inscripcion = await import('./inscripcionService.js')
  deportistas = await import('./deportistasService.js')
})
afterEach(() => vi.useRealTimers())

// Espera la respuesta avanzando el retardo simulado. Devuelve también cuánto "tardó" (tiempo simulado)
const resolver = async (promesa) => {
  const resultado = promesa.then((v) => ({ v }), (e) => ({ e }))
  await vi.runAllTimersAsync()
  const { v, e } = await resultado
  if (e) throw e
  return v
}
const medir = async (promesa) => {
  const inicio = Date.now()
  const respuesta = await resolver(promesa)
  return { respuesta, ms: Date.now() - inicio }
}

const datos = (extra = {}) => ({
  nombre: 'Lucas',
  apellido: 'Prueba Ejemplo',
  tipoDocumento: 'Registro civil',
  documento: '1200000001',
  fechaNacimiento: '2017-05-10',
  telefono: '3000000099',
  direccion: 'Calle 50 # 10-20, Barrio Ejemplo',
  acudienteNombre: 'Marta',
  acudienteApellido: 'Prueba Ejemplo',
  acudienteCorreo: 'marta.prueba@correo.com',
  acudienteTelefono: '3000000088',
  consentimiento: true,
  ...extra,
})
const todos = async () => resolver(deportistas.listarDeportistas())
const porDocumento = async (documento) => (await resolver(deportistas.listarDeportistas({ texto: documento })))[0]
const MENSAJE = 'Recibimos la inscripción. El club se comunicará contigo al correo indicado.'

describe('crearInscripcion: creación correcta', () => {
  it('responde con el mensaje neutro y crea el deportista en la lista común', async () => {
    const r = await resolver(inscripcion.crearInscripcion(datos()))
    expect(r).toEqual({ recibida: true, mensaje: MENSAJE })
    expect(await todos()).toHaveLength(22)
  })

  it('el deportista nace con origen "Inscripción en línea", sin categoría, activo y sin acudiente vinculado', async () => {
    await resolver(inscripcion.crearInscripcion(datos()))
    const d = await porDocumento('1200000001')
    expect(d).toMatchObject({
      nombre: 'Lucas', apellido: 'Prueba Ejemplo', tipoDocumento: 'Registro civil', documento: '1200000001', fechaNacimiento: '2017-05-10',
      telefono: '3000000099', direccion: 'Calle 50 # 10-20, Barrio Ejemplo',
      origen: 'Inscripción en línea', idCategoria: null, activo: true, acudiente: null, creadoPor: null,
    })
    expect(typeof d.creadoEn).toBe('string')
  })

  it('guarda el contacto del acudiente, sin vincularlo a ninguna cuenta (ni siquiera con el correo de la demo)', async () => {
    await resolver(inscripcion.crearInscripcion(datos({ acudienteNombre: ' Marta ', acudienteCorreo: ' acudiente@pradofc.co ' })))
    const d = await porDocumento('1200000001')
    expect(d.contactoAcudiente).toEqual({ nombre: 'Marta', apellido: 'Prueba Ejemplo', correo: 'acudiente@pradofc.co', telefono: '3000000088' })
    expect(d.acudiente).toBeNull()
    // el estado de cuenta del acudiente demo sigue viendo solo a sus 2 deportistas
    const { obtenerEstadoCuenta } = await import('./pagosService.js')
    const estado = await resolver(obtenerEstadoCuenta({ rol: 'acudiente', correo: 'acudiente@pradofc.co' }))
    expect(estado.deportistas.map((x) => x.deportista.nombre)).toEqual(['Santiago', 'Valeria'])
  })

  it('guarda el consentimiento { aceptado, fechaHora, version: "borrador" }', async () => {
    await resolver(inscripcion.crearInscripcion(datos()))
    const { consentimiento } = await porDocumento('1200000001')
    expect(consentimiento).toMatchObject({ aceptado: true, version: 'borrador' })
    expect(Number.isNaN(Date.parse(consentimiento.fechaHora))).toBe(false)
  })

  it('devuelve copias: modificar lo leído no cambia lo guardado', async () => {
    await resolver(inscripcion.crearInscripcion(datos()))
    const d = await porDocumento('1200000001')
    d.contactoAcudiente.nombre = 'Otro'
    d.consentimiento.aceptado = false
    const otra = await porDocumento('1200000001')
    expect(otra.contactoAcudiente.nombre).toBe('Marta')
    expect(otra.consentimiento.aceptado).toBe(true)
  })

  it('la edad no se guarda: se calcula con HOY_EJEMPLO (2026-10-22)', async () => {
    await resolver(inscripcion.crearInscripcion(datos({ fechaNacimiento: '2018-10-22' })))
    const d = await porDocumento('1200000001')
    expect('edad' in d).toBe(false)
    expect(deportistas.calcularEdad(d.fechaNacimiento)).toBe(8)
  })
})

describe('crearInscripcion: validación', () => {
  it.each([
    ['nombre', { nombre: '' }, 'Escribe los nombres.'],
    ['apellido', { apellido: '  ' }, 'Escribe los apellidos.'],
    ['tipoDocumento', { tipoDocumento: '' }, 'Selecciona el tipo de documento.'],
    ['documento', { documento: '' }, 'Escribe el número de documento.'],
    ['fechaNacimiento', { fechaNacimiento: '' }, 'Indica la fecha de nacimiento.'],
    ['telefono', { telefono: '' }, 'Escribe el teléfono.'],
    ['direccion', { direccion: '' }, 'Escribe la dirección.'],
    ['acudienteNombre', { acudienteNombre: '' }, 'Escribe los nombres.'],
    ['acudienteApellido', { acudienteApellido: '' }, 'Escribe los apellidos.'],
    ['acudienteCorreo', { acudienteCorreo: '' }, 'Escribe el correo electrónico.'],
    ['acudienteTelefono', { acudienteTelefono: '' }, 'Escribe el teléfono.'],
  ])('falta %s: VALIDACION con el mensaje de ese campo y no crea nada', async (campo, cambio, mensaje) => {
    await expect(resolver(inscripcion.crearInscripcion(datos(cambio)))).rejects.toMatchObject({ codigo: 'VALIDACION', errores: { [campo]: mensaje } })
    expect(await todos()).toHaveLength(21)
  })

  it('todo vacío: VALIDACION con los 12 campos (11 de datos y el consentimiento)', async () => {
    const e = await resolver(inscripcion.crearInscripcion({ ...inscripcion.INSCRIPCION_VACIA })).catch((x) => x)
    expect(e.codigo).toBe('VALIDACION')
    expect(Object.keys(e.errores)).toHaveLength(12)
    expect(Object.keys(e.errores)).toContain('consentimiento')
  })

  it('correo y teléfono del acudiente inválidos', async () => {
    const e = await resolver(inscripcion.crearInscripcion(datos({ acudienteCorreo: 'marta@', acudienteTelefono: '30000008' }))).catch((x) => x)
    expect(e.errores).toEqual({
      acudienteCorreo: 'El correo no es válido. Ejemplo: nombre@correo.com.',
      acudienteTelefono: 'El teléfono debe tener exactamente 10 dígitos, sin espacios.',
    })
  })

  it('consentimiento no marcado: false, ausente o un texto "true" no valen', async () => {
    for (const consentimiento of [false, undefined, 'true', 1]) {
      const e = await resolver(inscripcion.crearInscripcion(datos({ consentimiento }))).catch((x) => x)
      expect(e.codigo, String(consentimiento)).toBe('VALIDACION')
      expect(e.errores).toEqual({ consentimiento: 'Debes aceptar el tratamiento de datos para enviar la inscripción.' })
    }
    expect(await todos()).toHaveLength(21)
  })

  it('fecha de nacimiento futura respecto a HOY_EJEMPLO: 2026-10-23 falla y 2026-10-22 pasa', async () => {
    const e = await resolver(inscripcion.crearInscripcion(datos({ fechaNacimiento: '2026-10-23' }))).catch((x) => x)
    expect(e.errores).toEqual({ fechaNacimiento: 'La fecha de nacimiento no puede ser futura.' })
    await expect(resolver(inscripcion.crearInscripcion(datos({ fechaNacimiento: '2026-10-22' })))).resolves.toBeTruthy()
  })

  it('formatos de los datos del deportista: nombre con números, documento corto, teléfono de 9 dígitos', async () => {
    const e = await resolver(inscripcion.crearInscripcion(datos({ nombre: 'Lu2', documento: '12345', telefono: '300000009' }))).catch((x) => x)
    expect(Object.keys(e.errores).sort()).toEqual(['documento', 'nombre', 'telefono'])
  })

  it('un error de validación no dice nada del documento aunque esté repetido', async () => {
    const e = await resolver(inscripcion.crearInscripcion(datos({ documento: '1100000007', direccion: '' }))).catch((x) => x)
    expect(Object.keys(e.errores)).toEqual(['direccion'])
    expect(JSON.stringify(e)).not.toMatch(/registrado|existe|Cano/)
  })
})

describe('crearInscripcion: no revela qué está registrado', () => {
  it('con un documento ya registrado la respuesta es idéntica y NO se crea un segundo registro', async () => {
    const nueva = await resolver(inscripcion.crearInscripcion(datos({ documento: '1200000001' })))
    expect(await todos()).toHaveLength(22)
    const repetidaNueva = await resolver(inscripcion.crearInscripcion(datos({ documento: '1200000001', nombre: 'Otro' })))
    const repetidaDemo = await resolver(inscripcion.crearInscripcion(datos({ documento: '1100000007' })))
    expect(repetidaNueva).toEqual(nueva)
    expect(repetidaDemo).toEqual(nueva)
    expect(JSON.stringify(repetidaDemo)).toBe(JSON.stringify(nueva))
    expect(Object.keys(repetidaDemo)).toEqual(Object.keys(nueva))
    expect(await todos()).toHaveLength(22)
    expect((await porDocumento('1100000007')).nombre).toBe('Juan Pablo') // el existente no se tocó
  })

  it('tarda lo mismo con un documento nuevo que con uno repetido', async () => {
    const nuevo = await medir(inscripcion.crearInscripcion(datos({ documento: '1200000011' })))
    const repetido = await medir(inscripcion.crearInscripcion(datos({ documento: '1100000007' })))
    expect(repetido.ms).toBe(nuevo.ms)
    expect(nuevo.ms).toBeGreaterThan(0)
  })

  it('un correo repetido (de la demo o de otra inscripción) da la misma respuesta y no bloquea a otro deportista (hermanos)', async () => {
    const primera = await resolver(inscripcion.crearInscripcion(datos({ acudienteCorreo: 'acudiente@pradofc.co' })))
    const hermano = await resolver(inscripcion.crearInscripcion(datos({ documento: '1200000002', nombre: 'Sara', acudienteCorreo: 'acudiente@pradofc.co' })))
    expect(hermano).toEqual(primera)
    expect(await todos()).toHaveLength(23)
  })

  it('la respuesta no trae id, documento, nombres ni correos', async () => {
    const r = await resolver(inscripcion.crearInscripcion(datos()))
    expect(Object.keys(r).sort()).toEqual(['mensaje', 'recibida'])
    expect(JSON.stringify(r)).not.toMatch(/1200000001|Lucas|marta|"id"/i)
  })

  it('enviar dos veces la misma inscripción (doble envío) deja un solo registro', async () => {
    await resolver(inscripcion.crearInscripcion(datos()))
    await resolver(inscripcion.crearInscripcion(datos()))
    expect(await todos()).toHaveLength(22)
  })

  it('un fallo de red lanza ERROR_DE_RED, no crea nada, y reintentar funciona', async () => {
    inscripcion.configurarSimulacion({ fallos: 1 })
    await expect(resolver(inscripcion.crearInscripcion(datos()))).rejects.toMatchObject({ codigo: 'ERROR_DE_RED' })
    expect(await todos()).toHaveLength(21)
    await expect(resolver(inscripcion.crearInscripcion(datos()))).resolves.toEqual({ recibida: true, mensaje: MENSAJE })
    expect(await todos()).toHaveLength(22)
  })
})

describe('los inscritos en línea y el resto del sistema', () => {
  const admin = { rol: 'administrador', correo: 'administrador@pradofc.co' }

  it('el campo origen, el contacto y el consentimiento no son editables ni aparecen como cambios en el historial', async () => {
    await resolver(inscripcion.crearInscripcion(datos()))
    const { id } = await porDocumento('1200000001')
    const r = await resolver(deportistas.actualizarDeportista(id, { telefono: '3000000077', origen: 'Otro', contactoAcudiente: { nombre: 'X' }, consentimiento: { aceptado: false } }, admin))
    expect(r.deportista.origen).toBe('Inscripción en línea')
    expect(r.deportista.contactoAcudiente.nombre).toBe('Marta')
    expect(r.deportista.consentimiento.aceptado).toBe(true)
    expect(r.deportista.telefono).toBe('3000000077')
    expect(r.historial[0].cambios).toEqual([{ campo: 'telefono', antes: '3000000099', despues: '3000000077' }])
  })

  it('los registros del administrador no cambian: no traen origen ni contacto', async () => {
    const r = await resolver(deportistas.crearDeportista({ ...datos(), idCategoria: '' }, admin))
    expect('origen' in r).toBe(false)
    expect('contactoAcudiente' in r).toBe(false)
    expect(r.creadoPor).toBe('administrador@pradofc.co')
  })

  it('el inscrito aparece en la lista general pero no en ningún grupo (sin categoría)', async () => {
    await resolver(inscripcion.crearInscripcion(datos()))
    for (const id of [1, 2, 3, 4, 5]) {
      expect((await resolver(deportistas.listarDeportistas({ idCategoria: id }))).some((x) => x.documento === '1200000001')).toBe(false)
    }
  })
})
