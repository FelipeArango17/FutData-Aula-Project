// Validaciones de formularios reutilizables. Son funciones puras.
// Cada validador devuelve el mensaje de error (texto) o null si el valor es válido. Recortan los espacios de los
// bordes ellos mismos, así que se les puede pasar el valor tal como está en el campo.
// Los mensajes que ya ven los usuarios (entrenadores, entrenamientos, inicio de sesión) están aquí TAL CUAL, como
// constantes; los de los validadores nuevos (nombre, dirección, fecha de nacimiento y monto) todavía no se muestran
// en ninguna pantalla.
import { esDomingo, esFechaValida, esHoraValida } from './fechas.js'

export const MENSAJES = {
  // Entrenadores
  nombreObligatorio: 'Escribe el nombre.',
  apellidoObligatorio: 'Escribe el apellido.',
  tipoDocumento: 'Selecciona el tipo de documento.',
  documentoObligatorio: 'Escribe el número de documento.',
  documentoFormato: 'El documento solo puede tener números, sin puntos ni espacios.',
  documentoRango: 'El documento debe tener entre 6 y 12 dígitos.',
  telefonoObligatorio: 'Escribe el teléfono.',
  telefonoFormato: 'El teléfono debe tener exactamente 10 dígitos, sin espacios.',
  correoObligatorio: 'Escribe el correo electrónico.',
  correoFormato: 'El correo no es válido. Ejemplo: nombre@pradofc.co.',
  especialidadObligatoria: 'Escribe la especialidad.',
  categoriasMinimo: 'Selecciona al menos una categoría.',
  // Inicio de sesión
  loginCorreoObligatorio: 'Escribe tu correo electrónico.',
  loginCorreoFormato: 'El correo no es válido. Ejemplo: nombre@correo.com.',
  loginContrasenaObligatoria: 'Escribe tu contraseña.',
  // Entrenamientos
  entrenamientoCategoria: 'Selecciona una categoría.',
  entrenamientoEntrenador: 'Selecciona el entrenador.',
  entrenamientoFechaObligatoria: 'Indica la fecha del entrenamiento.',
  entrenamientoFechaInvalida: 'La fecha no es válida. Usa el formato día/mes/año.',
  entrenamientoFechaDomingo: 'Los entrenamientos se programan de lunes a sábado.',
  entrenamientoHoraObligatoria: 'Indica la hora del entrenamiento.',
  entrenamientoHoraInvalida: 'La hora no es válida. Usa el formato HH:MM.',
  entrenamientoLugar: 'Selecciona el lugar.',
  // Nuevos (para los formularios de deportista, inscripción y pago)
  nombreLongitud: 'El nombre debe tener entre 2 y 60 caracteres.',
  nombreCaracteres: 'El nombre solo puede tener letras, espacios, guion y apóstrofo.',
  // Campos "Nombres" y "Apellidos" (plural), como en el formulario de deportistas
  nombresObligatorio: 'Escribe los nombres.',
  nombresLongitud: 'Los nombres deben tener entre 2 y 60 caracteres.',
  nombresCaracteres: 'Los nombres solo pueden tener letras, espacios, guion y apóstrofo.',
  apellidosObligatorio: 'Escribe los apellidos.',
  apellidosLongitud: 'Los apellidos deben tener entre 2 y 60 caracteres.',
  apellidosCaracteres: 'Los apellidos solo pueden tener letras, espacios, guion y apóstrofo.',
  direccionObligatoria: 'Escribe la dirección.',
  direccionLongitud: 'La dirección debe tener entre 5 y 120 caracteres.',
  fechaNacimientoObligatoria: 'Indica la fecha de nacimiento.',
  fechaNacimientoFormato: 'La fecha de nacimiento no es válida. Usa el formato día/mes/año.',
  fechaNacimientoFutura: 'La fecha de nacimiento no puede ser futura.',
  fechaNacimientoEdad: 'La edad no puede superar los 100 años.',
  montoObligatorio: 'Escribe el monto.',
  montoFormato: 'El monto debe ser un número entero de pesos, sin puntos, comas, símbolo $ ni signos.',
  montoPositivo: 'El monto debe ser mayor que cero.',
}

export const EDAD_MAXIMA = 100

const texto = (valor) => String(valor ?? '').trim()

// Correo: algo@dominio.ext, sin espacios. Es una revisión de formato, no prueba que el buzón exista.
export const esCorreoValido = (correo) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())

export function validarCorreo(valor, { obligatorio = MENSAJES.correoObligatorio, formato = MENSAJES.correoFormato } = {}) {
  const correo = texto(valor)
  if (!correo) return obligatorio
  return esCorreoValido(correo) ? null : formato
}

export function validarDocumento(valor) {
  const documento = texto(valor)
  if (!documento) return MENSAJES.documentoObligatorio
  if (!/^\d+$/.test(documento)) return MENSAJES.documentoFormato
  if (documento.length < 6 || documento.length > 12) return MENSAJES.documentoRango
  return null
}

export function validarTelefono(valor) {
  const telefono = texto(valor)
  if (!telefono) return MENSAJES.telefonoObligatorio
  return /^\d{10}$/.test(telefono) ? null : MENSAJES.telefonoFormato
}

// 2 a 60 caracteres: letras (con tildes y ñ), espacios, guion y apóstrofo; empieza por una letra.
// Los tres mensajes se pueden cambiar (por ejemplo para "Apellidos" en plural).
export function validarNombre(valor, { obligatorio = MENSAJES.nombreObligatorio, longitud = MENSAJES.nombreLongitud, caracteres = MENSAJES.nombreCaracteres } = {}) {
  const nombre = texto(valor).normalize('NFC')
  if (!nombre) return obligatorio
  if (nombre.length < 2 || nombre.length > 60) return longitud
  return /^\p{L}[\p{L} '’-]*$/u.test(nombre) ? null : caracteres
}

export function validarDireccion(valor) {
  const direccion = texto(valor)
  if (!direccion) return MENSAJES.direccionObligatoria
  return direccion.length < 5 || direccion.length > 120 ? MENSAJES.direccionLongitud : null
}

// Fecha de hoy según el reloj del sistema (AAAA-MM-DD, en UTC como utils/fechas.js)
const hoySistema = () => new Date().toISOString().slice(0, 10)

// Años cumplidos a la fecha `hoy`; las dos son "AAAA-MM-DD"
const edadEn = (nacimiento, hoy) => {
  const [a, m, d] = nacimiento.split('-').map(Number)
  const [ah, mh, dh] = hoy.split('-').map(Number)
  return ah - a - (mh < m || (mh === m && dh < d) ? 1 : 0)
}

// AAAA-MM-DD, una fecha que existe, no futura respecto a `hoy` y con edad de 100 años como máximo.
// `hoy`: quien llama pasa el "hoy" de la demo (HOY_EJEMPLO, de entrenamientosService: utils no importa de services);
// si no lo pasa se usa la fecha del sistema.
export function validarFechaNacimiento(valor, hoy = hoySistema()) {
  const fecha = texto(valor)
  if (!fecha) return MENSAJES.fechaNacimientoObligatoria
  // Se revisa mes 01-12 y día 01-31 antes de esFechaValida, que lanza RangeError con un mes 13 (ver fechas.test.js)
  if (!/^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(fecha) || !esFechaValida(fecha)) return MENSAJES.fechaNacimientoFormato
  if (fecha > hoy) return MENSAJES.fechaNacimientoFutura
  return edadEn(fecha, hoy) > EDAD_MAXIMA ? MENSAJES.fechaNacimientoEdad : null
}

// Pesos colombianos: un número entero positivo, o un texto de solo dígitos. No acepta separadores de miles,
// símbolo $, decimales ni negativos.
export function validarMonto(valor) {
  if (typeof valor === 'number') {
    if (!Number.isSafeInteger(valor)) return MENSAJES.montoFormato
    return valor > 0 ? null : MENSAJES.montoPositivo
  }
  const monto = texto(valor)
  if (!monto) return MENSAJES.montoObligatorio
  if (!/^\d+$/.test(monto) || !Number.isSafeInteger(Number(monto))) return MENSAJES.montoFormato
  return Number(monto) > 0 ? null : MENSAJES.montoPositivo
}

// Reglas del formulario de entrenamientos
export function validarFechaEntrenamiento(valor) {
  if (!valor) return MENSAJES.entrenamientoFechaObligatoria
  if (!esFechaValida(valor)) return MENSAJES.entrenamientoFechaInvalida
  return esDomingo(valor) ? MENSAJES.entrenamientoFechaDomingo : null
}

export function validarHoraEntrenamiento(valor) {
  if (!valor) return MENSAJES.entrenamientoHoraObligatoria
  return esHoraValida(valor) ? null : MENSAJES.entrenamientoHoraInvalida
}

// Reglas genéricas para armar validarCampos. Cada una devuelve (valor, todos) => mensaje | null.
// requerido: el texto no puede estar vacío (con recortar: false los espacios cuentan como contenido, por ejemplo en contraseñas).
export const requerido = (mensaje, { recortar = true } = {}) => (valor) => {
  const t = String(valor ?? '')
  return (recortar ? t.trim() : t) ? null : mensaje
}
// enLista: el valor debe ser una de las opciones permitidas
export const enLista = (opciones, mensaje) => (valor) => (opciones.includes(valor) ? null : mensaje)
// noVacia: una lista (por ejemplo de casillas marcadas) con al menos un elemento
export const noVacia = (mensaje) => (valor) => (Array.isArray(valor) && valor.length > 0 ? null : mensaje)

// reglas = { campo: [(valor, todos) => mensaje | null, ...] }. Evalúa las reglas de cada campo en orden y se queda con el
// primer mensaje; devuelve { campo: mensaje } solo para los campos con error. `todos` son todos los valores, para reglas
// que dependen de otros campos.
export function validarCampos(valores, reglas) {
  const errores = {}
  for (const [campo, lista] of Object.entries(reglas)) {
    for (const regla of lista) {
      const mensaje = regla(valores[campo], valores)
      if (mensaje) {
        errores[campo] = mensaje
        break
      }
    }
  }
  return errores
}
