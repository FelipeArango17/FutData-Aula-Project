import { describe, expect, it } from 'vitest'
import {
  MENSAJES, enLista, esCorreoValido, noVacia, requerido, validarCampos, validarCorreo, validarDireccion, validarDocumento,
  validarFechaEntrenamiento, validarFechaNacimiento, validarHoraEntrenamiento, validarMonto, validarNombre, validarTelefono,
} from './validaciones.js'

const HOY = '2026-10-22'

describe('esCorreoValido', () => {
  it('acepta correos con formato válido', () => {
    expect(esCorreoValido('nombre@correo.com')).toBe(true)
    expect(esCorreoValido('carlos.diaz@pradofc.co')).toBe(true)
    expect(esCorreoValido('a+b@sub.dominio.org')).toBe(true)
    expect(esCorreoValido('  nombre@correo.com  ')).toBe(true)
  })

  it('rechaza correos incompletos o con espacios', () => {
    expect(esCorreoValido('')).toBe(false)
    expect(esCorreoValido('nombre')).toBe(false)
    expect(esCorreoValido('nombre@')).toBe(false)
    expect(esCorreoValido('@correo.com')).toBe(false)
    expect(esCorreoValido('nombre@correo')).toBe(false)
    expect(esCorreoValido('nom bre@correo.com')).toBe(false)
    expect(esCorreoValido('a@@b.com')).toBe(false)
  })
})

describe('validarCorreo', () => {
  it('válido devuelve null, también con espacios en los bordes', () => {
    expect(validarCorreo('luis@pradofc.co')).toBeNull()
    expect(validarCorreo('  luis@pradofc.co ')).toBeNull()
  })

  it('vacío o solo espacios: mensaje de obligatorio; mal escrito: mensaje de formato', () => {
    expect(validarCorreo('')).toBe('Escribe el correo electrónico.')
    expect(validarCorreo('   ')).toBe('Escribe el correo electrónico.')
    expect(validarCorreo(undefined)).toBe('Escribe el correo electrónico.')
    expect(validarCorreo('luis@')).toBe('El correo no es válido. Ejemplo: nombre@pradofc.co.')
  })

  it('acepta mensajes propios (inicio de sesión)', () => {
    const login = { obligatorio: MENSAJES.loginCorreoObligatorio, formato: MENSAJES.loginCorreoFormato }
    expect(validarCorreo('', login)).toBe('Escribe tu correo electrónico.')
    expect(validarCorreo('abc', login)).toBe('El correo no es válido. Ejemplo: nombre@correo.com.')
    expect(validarCorreo('a@b.co', login)).toBeNull()
  })
})

describe('validarDocumento', () => {
  it('acepta de 6 a 12 dígitos (límites exactos)', () => {
    expect(validarDocumento('123456')).toBeNull()
    expect(validarDocumento('1234567')).toBeNull()
    expect(validarDocumento('123456789012')).toBeNull()
    expect(validarDocumento('  1098765432  ')).toBeNull()
  })

  it('5 y 13 dígitos: mensaje de rango', () => {
    expect(validarDocumento('12345')).toBe('El documento debe tener entre 6 y 12 dígitos.')
    expect(validarDocumento('1234567890123')).toBe('El documento debe tener entre 6 y 12 dígitos.')
    expect(validarDocumento('0')).toBe(MENSAJES.documentoRango)
  })

  it('vacío, solo espacios o nulo: obligatorio', () => {
    expect(validarDocumento('')).toBe('Escribe el número de documento.')
    expect(validarDocumento('    ')).toBe('Escribe el número de documento.')
    expect(validarDocumento(null)).toBe('Escribe el número de documento.')
  })

  it('letras, puntos, espacios internos, signos y caracteres especiales: solo números', () => {
    const msg = 'El documento solo puede tener números, sin puntos ni espacios.'
    for (const malo of ['12ab56', 'abcdefgh', '1.234.567', '123 456 789', '-1234567', '+573001', '12345e6', '１２３４５６７', '123456\n7']) {
      expect(validarDocumento(malo), malo).toBe(msg)
    }
  })
})

describe('validarTelefono', () => {
  it('acepta exactamente 10 dígitos, con espacios en los bordes', () => {
    expect(validarTelefono('3001112233')).toBeNull()
    expect(validarTelefono(' 3001112233 ')).toBeNull()
  })

  it('9 y 11 dígitos: mensaje de formato', () => {
    const msg = 'El teléfono debe tener exactamente 10 dígitos, sin espacios.'
    expect(validarTelefono('300111223')).toBe(msg)
    expect(validarTelefono('30011122334')).toBe(msg)
  })

  it('espacios internos, guiones, prefijo y letras: formato', () => {
    for (const malo of ['300 111 2233', '300-111-2233', '+573001112233', '300111223a', '(300)1112233']) {
      expect(validarTelefono(malo), malo).toBe(MENSAJES.telefonoFormato)
    }
  })

  it('vacío o solo espacios: obligatorio', () => {
    expect(validarTelefono('')).toBe('Escribe el teléfono.')
    expect(validarTelefono('   ')).toBe('Escribe el teléfono.')
  })
})

describe('validarNombre', () => {
  it('acepta letras con tildes y ñ, espacios, guion y apóstrofo', () => {
    for (const bueno of ['Luis', 'María José', 'Ñoño Peña', 'José Ángel Núñez', "O'Brien", 'Ana-Lucía', 'D’Angelo', 'Üme Çelik', '  Luis  ']) {
      expect(validarNombre(bueno), bueno).toBeNull()
    }
  })

  it('límites: 1 carácter falla, 2 y 60 pasan, 61 falla', () => {
    expect(validarNombre('A')).toBe(MENSAJES.nombreLongitud)
    expect(validarNombre('Al')).toBeNull()
    expect(validarNombre('a'.repeat(60))).toBeNull()
    expect(validarNombre('a'.repeat(61))).toBe(MENSAJES.nombreLongitud)
  })

  it('vacío o solo espacios: obligatorio (con mensaje propio si se pide)', () => {
    expect(validarNombre('')).toBe('Escribe el nombre.')
    expect(validarNombre('     ')).toBe('Escribe el nombre.')
    expect(validarNombre('', { obligatorio: 'Escribe el apellido.' })).toBe('Escribe el apellido.')
  })

  it('números y caracteres especiales: mensaje de caracteres', () => {
    for (const malo of ['Juan2', '123', 'Ana_Maria', 'Luis@', 'Pé<b>', '-Ana', "'Ana", 'Ana.', 'Ana 😀', '=SUMA(A1)']) {
      expect(validarNombre(malo), malo).toBe(MENSAJES.nombreCaracteres)
    }
  })

  it('los tres mensajes se pueden cambiar (campos "Nombres" y "Apellidos")', () => {
    const apellidos = { obligatorio: MENSAJES.apellidosObligatorio, longitud: MENSAJES.apellidosLongitud, caracteres: MENSAJES.apellidosCaracteres }
    expect(validarNombre('', apellidos)).toBe('Escribe los apellidos.')
    expect(validarNombre('A', apellidos)).toBe('Los apellidos deben tener entre 2 y 60 caracteres.')
    expect(validarNombre('Ríos2', apellidos)).toBe('Los apellidos solo pueden tener letras, espacios, guion y apóstrofo.')
    expect(validarNombre('Ríos Zapata', apellidos)).toBeNull()
  })

  it('una tilde escrita con la letra y el acento por separado también pasa', () => {
    expect(validarNombre('José')).toBeNull()
  })
})

describe('validarDireccion', () => {
  it('límites: 4 falla, 5 y 120 pasan, 121 falla', () => {
    expect(validarDireccion('Cll 1')).toBeNull()
    expect(validarDireccion('Cl 1')).toBe(MENSAJES.direccionLongitud)
    expect(validarDireccion('a'.repeat(120))).toBeNull()
    expect(validarDireccion('a'.repeat(121))).toBe(MENSAJES.direccionLongitud)
  })

  it('acepta números, # y guiones; recorta los bordes', () => {
    expect(validarDireccion('Calle 45 # 12-30, Apto 301')).toBeNull()
    expect(validarDireccion('  Carrera 7  ')).toBeNull()
    expect(validarDireccion('    abc    ')).toBe(MENSAJES.direccionLongitud)
  })

  it('vacío o solo espacios: obligatoria', () => {
    expect(validarDireccion('')).toBe('Escribe la dirección.')
    expect(validarDireccion('      ')).toBe('Escribe la dirección.')
  })
})

describe('validarFechaNacimiento', () => {
  it('acepta fechas reales, incluido hoy y el 29 de febrero de un año bisiesto', () => {
    expect(validarFechaNacimiento('2018-03-14', HOY)).toBeNull()
    expect(validarFechaNacimiento('2026-10-22', HOY)).toBeNull()
    expect(validarFechaNacimiento('2020-02-29', HOY)).toBeNull()
    expect(validarFechaNacimiento(' 2018-03-14 ', HOY)).toBeNull()
  })

  it('30 de febrero, 29 de febrero de un año no bisiesto, 31 de abril: no existen', () => {
    expect(validarFechaNacimiento('2018-02-30', HOY)).toBe(MENSAJES.fechaNacimientoFormato)
    expect(validarFechaNacimiento('2019-02-29', HOY)).toBe(MENSAJES.fechaNacimientoFormato)
    expect(validarFechaNacimiento('2018-04-31', HOY)).toBe(MENSAJES.fechaNacimientoFormato)
  })

  it('mes 13, mes 00, día 00 y día 32 devuelven mensaje (sin lanzar error)', () => {
    for (const mala of ['2018-13-01', '2018-00-10', '2018-05-00', '2018-05-32']) {
      expect(validarFechaNacimiento(mala, HOY), mala).toBe(MENSAJES.fechaNacimientoFormato)
    }
  })

  it('formato distinto de AAAA-MM-DD: formato', () => {
    for (const mala of ['14/03/2018', '2018/03/14', '18-03-14', '2018-3-4', 'ayer', '2018-03-14T00:00', '20180314']) {
      expect(validarFechaNacimiento(mala, HOY), mala).toBe(MENSAJES.fechaNacimientoFormato)
    }
  })

  it('fecha futura (mañana, el año próximo): no puede ser futura', () => {
    expect(validarFechaNacimiento('2026-10-23', HOY)).toBe('La fecha de nacimiento no puede ser futura.')
    expect(validarFechaNacimiento('2027-01-01', HOY)).toBe(MENSAJES.fechaNacimientoFutura)
  })

  it('edad máxima 100 años: 100 pasa, 101 falla, y el cumpleaños manda', () => {
    expect(validarFechaNacimiento('1926-10-22', HOY)).toBeNull() // cumple 100 hoy
    expect(validarFechaNacimiento('1926-01-01', HOY)).toBeNull() // 100 años y unos meses
    expect(validarFechaNacimiento('1925-10-23', HOY)).toBeNull() // le faltan 1 día para los 101
    expect(validarFechaNacimiento('1925-10-22', HOY)).toBe('La edad no puede superar los 100 años.') // cumple 101 hoy
    expect(validarFechaNacimiento('1900-01-01', HOY)).toBe(MENSAJES.fechaNacimientoEdad)
  })

  it('vacío o solo espacios: obligatoria', () => {
    expect(validarFechaNacimiento('', HOY)).toBe('Indica la fecha de nacimiento.')
    expect(validarFechaNacimiento('   ', HOY)).toBe('Indica la fecha de nacimiento.')
    expect(validarFechaNacimiento(undefined, HOY)).toBe('Indica la fecha de nacimiento.')
  })

  it('sin "hoy" usa la fecha del sistema', () => {
    expect(validarFechaNacimiento('2000-01-01')).toBeNull()
    expect(validarFechaNacimiento('2999-01-01')).toBe(MENSAJES.fechaNacimientoFutura)
  })
})

describe('validarMonto', () => {
  it('acepta enteros positivos como número o como texto de dígitos', () => {
    expect(validarMonto(90000)).toBeNull()
    expect(validarMonto(1)).toBeNull()
    expect(validarMonto('90000')).toBeNull()
    expect(validarMonto('  150000 ')).toBeNull()
  })

  it('con decimales: formato', () => {
    expect(validarMonto(90000.5)).toBe(MENSAJES.montoFormato)
    expect(validarMonto('90000.5')).toBe('El monto debe ser un número entero de pesos, sin puntos, comas, símbolo $ ni signos.')
    expect(validarMonto('90000,5')).toBe(MENSAJES.montoFormato)
    expect(validarMonto(NaN)).toBe(MENSAJES.montoFormato)
    expect(validarMonto(Infinity)).toBe(MENSAJES.montoFormato)
  })

  it('negativos: no se aceptan', () => {
    expect(validarMonto(-5)).toBe('El monto debe ser mayor que cero.')
    expect(validarMonto('-5')).toBe(MENSAJES.montoFormato)
    expect(validarMonto('+5')).toBe(MENSAJES.montoFormato)
  })

  it('cero: debe ser mayor que cero', () => {
    expect(validarMonto(0)).toBe(MENSAJES.montoPositivo)
    expect(validarMonto('0')).toBe(MENSAJES.montoPositivo)
    expect(validarMonto('000')).toBe(MENSAJES.montoPositivo)
  })

  it('con separadores de miles, símbolo $, letras o espacios internos: formato', () => {
    for (const malo of ['90.000', '90,000', '$90000', '$ 90.000', '90 000', '9e4', 'noventa mil', '0x10']) {
      expect(validarMonto(malo), malo).toBe(MENSAJES.montoFormato)
    }
  })

  it('vacío, solo espacios, nulo: obligatorio; demasiado grande: formato', () => {
    expect(validarMonto('')).toBe('Escribe el monto.')
    expect(validarMonto('   ')).toBe('Escribe el monto.')
    expect(validarMonto(null)).toBe('Escribe el monto.')
    expect(validarMonto(undefined)).toBe('Escribe el monto.')
    expect(validarMonto('9'.repeat(20))).toBe(MENSAJES.montoFormato)
  })
})

describe('validaciones del formulario de entrenamientos', () => {
  it('fecha: obligatoria, inexistente y domingo', () => {
    expect(validarFechaEntrenamiento('')).toBe('Indica la fecha del entrenamiento.')
    expect(validarFechaEntrenamiento('2026-02-30')).toBe('La fecha no es válida. Usa el formato día/mes/año.')
    expect(validarFechaEntrenamiento('2026-10-25')).toBe('Los entrenamientos se programan de lunes a sábado.')
    expect(validarFechaEntrenamiento('2026-10-24')).toBeNull()
  })

  it('hora: obligatoria e inválida', () => {
    expect(validarHoraEntrenamiento('')).toBe('Indica la hora del entrenamiento.')
    expect(validarHoraEntrenamiento('25:00')).toBe('La hora no es válida. Usa el formato HH:MM.')
    expect(validarHoraEntrenamiento('16:30')).toBeNull()
  })
})

describe('reglas genéricas', () => {
  it('requerido recorta por defecto y con recortar: false cuenta los espacios', () => {
    expect(requerido('falta')('   ')).toBe('falta')
    expect(requerido('falta')(undefined)).toBe('falta')
    expect(requerido('falta')(' a ')).toBeNull()
    expect(requerido('falta', { recortar: false })('   ')).toBeNull()
    expect(requerido('falta', { recortar: false })('')).toBe('falta')
  })

  it('enLista y noVacia', () => {
    expect(enLista(['a', 'b'], 'elige')('a')).toBeNull()
    expect(enLista(['a', 'b'], 'elige')('')).toBe('elige')
    expect(noVacia('marca')([])).toBe('marca')
    expect(noVacia('marca')(undefined)).toBe('marca')
    expect(noVacia('marca')(['x'])).toBeNull()
  })
})

describe('validarCampos', () => {
  it('devuelve solo los campos con error y el primer mensaje de cada uno', () => {
    const errores = validarCampos(
      { documento: '12ab56', telefono: '3001112233', correo: '' },
      { documento: [validarDocumento, () => 'no debería verse'], telefono: [validarTelefono], correo: [validarCorreo] },
    )
    expect(errores).toEqual({
      documento: 'El documento solo puede tener números, sin puntos ni espacios.',
      correo: 'Escribe el correo electrónico.',
    })
    expect('telefono' in errores).toBe(false)
  })

  it('todo válido devuelve un objeto vacío', () => {
    expect(validarCampos({ telefono: '3001112233' }, { telefono: [validarTelefono] })).toEqual({})
    expect(validarCampos({}, {})).toEqual({})
  })

  it('evalúa las reglas de un campo en orden y se detiene en la primera que falla', () => {
    const llamadas = []
    const regla = (nombre, mensaje) => () => {
      llamadas.push(nombre)
      return mensaje
    }
    const errores = validarCampos({ x: 1 }, { x: [regla('a', null), regla('b', 'falla b'), regla('c', 'falla c')] })
    expect(errores).toEqual({ x: 'falla b' })
    expect(llamadas).toEqual(['a', 'b'])
  })

  it('las reglas reciben el valor y todos los valores (reglas que dependen de otros campos)', () => {
    const fin = (valor, todos) => (valor < todos.inicio ? 'La fecha final no puede ser anterior a la inicial.' : null)
    expect(validarCampos({ inicio: '2026-10-10', fin: '2026-10-01' }, { fin: [fin] })).toEqual({ fin: 'La fecha final no puede ser anterior a la inicial.' })
    expect(validarCampos({ inicio: '2026-10-10', fin: '2026-10-11' }, { fin: [fin] })).toEqual({})
    const categoria = enLista(['Sub-8', 'Sub-10'], 'Selecciona una categoría.')
    expect(validarCampos({ categoria: 'Sub-20' }, { categoria: [categoria] })).toEqual({ categoria: 'Selecciona una categoría.' })
  })

  it('un campo sin valor en `valores` se trata como vacío', () => {
    expect(validarCampos({}, { nombre: [requerido('Escribe el nombre.')] })).toEqual({ nombre: 'Escribe el nombre.' })
  })
})
