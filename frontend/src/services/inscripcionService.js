// Servicio de inscripción en línea (HU-009, tarea 316). Pantalla PÚBLICA: no hay sesión ni actor. Trabaja sobre datos de
// ejemplo EN MEMORIA y reutiliza deportistasService (los inscritos quedan en la misma lista de deportistas, sin categoría).
// Las funciones son async con retardo simulado y errores con `codigo`, para cambiarlas luego por Axios (POST /inscripciones).
//
// Datos (planos, como los campos del formulario):
//   deportista:  nombre, apellido, tipoDocumento, documento, fechaNacimiento, telefono, direccion   (SIN categoría)
//   acudiente:   acudienteNombre, acudienteApellido, acudienteCorreo, acudienteTelefono
//   consentimiento: true (casilla marcada)
// Errores: 'VALIDACION' (con `errores`: { campo: mensaje }) | 'ERROR_DE_RED'.
//
// PRIVACIDAD (no revelar cuentas): si el documento ya existe NO se crea un segundo registro, y la respuesta (forma y mensaje) y
// el retardo son EXACTAMENTE los mismos que con una inscripción nueva. Nunca se dice si un documento o un correo ya están
// registrados, ni se devuelve el id. Un correo repetido NO cuenta como duplicado: un acudiente puede inscribir a varios hijos
// con el mismo correo; solo importa el documento del deportista. El correo escrito NO se vincula con ninguna cuenta
// (cualquiera podría escribir el correo de otra persona).
//
// PENDIENTE (backend y decisiones con Juan):
// - Captcha y límite de intentos por IP/correo contra abuso (esta pantalla es pública).
// - Correo de confirmación al acudiente (n8n), igual que el del código 2FA.
// - Revisión del administrador antes de activar: hoy el deportista nace "activo" (como pide la historia), pero sin categoría
//   ni cuenta de acudiente vinculada; la vinculación y la categoría las hace el administrador.
// - Consentimiento (Ley 1581 de 2012, datos de menores): el texto de autorización y la Política de tratamiento de datos los
//   define el club; la versión guardada es 'borrador' hasta entonces.
import { MENSAJES, casillaMarcada, validarCampos, validarCorreo, validarNombre, validarTelefono } from '../utils/validaciones.js'
import { crearDeportistaDesdeInscripcion, validarDatosDeportista } from './deportistasService.js'

export const MENSAJE_INSCRIPCION_RECIBIDA = 'Recibimos la inscripción. El club se comunicará contigo al correo indicado.'
export const VERSION_CONSENTIMIENTO = 'borrador'

// Formulario en blanco
export const INSCRIPCION_VACIA = {
  nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', direccion: '',
  acudienteNombre: '', acudienteApellido: '', acudienteCorreo: '', acudienteTelefono: '',
  consentimiento: false,
}

// Reglas de toda la inscripción: los datos del deportista salen de validarDatosDeportista (contra hoyLocal()) y los del
// acudiente y el consentimiento, de los validadores compartidos. Devuelve { campo: mensaje } solo con los campos con error.
export function validarDatosInscripcion(datos) {
  return {
    ...validarDatosDeportista(datos, undefined, { grupo: false }),
    ...validarCampos(datos, {
      acudienteNombre: [(v) => validarNombre(v, { obligatorio: MENSAJES.nombresObligatorio, longitud: MENSAJES.nombresLongitud, caracteres: MENSAJES.nombresCaracteres })],
      acudienteApellido: [(v) => validarNombre(v, { obligatorio: MENSAJES.apellidosObligatorio, longitud: MENSAJES.apellidosLongitud, caracteres: MENSAJES.apellidosCaracteres })],
      acudienteCorreo: [(v) => validarCorreo(v, { obligatorio: MENSAJES.correoObligatorio, formato: MENSAJES.loginCorreoFormato })],
      acudienteTelefono: [validarTelefono],
      consentimiento: [casillaMarcada(MENSAJES.consentimientoObligatorio)],
    }),
  }
}

// Simulación de red: retardo y fallo se pueden cambiar (pruebas y verificación manual del error con "Reintentar").
let retardoMs = 300
let fallosPendientes = 0
export function configurarSimulacion({ retardo, fallos } = {}) {
  if (retardo !== undefined) retardoMs = retardo
  if (fallos !== undefined) fallosPendientes = fallos
}

async function simularRed() {
  await new Promise((resolve) => setTimeout(resolve, retardoMs))
  if (fallosPendientes > 0) {
    fallosPendientes -= 1
    throw Object.assign(new Error('No se pudo conectar con el servidor.'), { codigo: 'ERROR_DE_RED' })
  }
}

export async function crearInscripcion(datos) {
  await simularRed()
  const errores = validarDatosInscripcion(datos)
  if (Object.keys(errores).length > 0) throw Object.assign(new Error('Hay datos que corregir.'), { codigo: 'VALIDACION', errores })

  await crearDeportistaDesdeInscripcion({
    deportista: datos,
    contactoAcudiente: {
      nombre: datos.acudienteNombre.trim(),
      apellido: datos.acudienteApellido.trim(),
      correo: datos.acudienteCorreo.trim(),
      telefono: datos.acudienteTelefono.trim(),
    },
    consentimiento: { aceptado: true, fechaHora: new Date().toISOString(), version: VERSION_CONSENTIMIENTO },
  })
  // Siempre la misma respuesta, haya habido registro nuevo o no
  return { recibida: true, mensaje: MENSAJE_INSCRIPCION_RECIBIDA }
}
