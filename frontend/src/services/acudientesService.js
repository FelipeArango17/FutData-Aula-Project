// Acudientes de los deportistas. Por ahora en MEMORIA, sin backend. Las funciones son async y lanzan errores con `codigo`, como
// los demás servicios, para cambiarlas luego por endpoints sin tocar las pantallas.
//
// Forma de datos:
//   Acudiente = { id, nombre, apellido, correo, telefono }
//   Vinculo   = { idDeportista, idAcudiente, parentesco }   (parentesco: uno de PARENTESCOS)
// Cómo se guarda el vínculo: la lista de vínculos de este servicio es la fuente (un acudiente puede tener varios deportistas y
// el parentesco es del vínculo, no de la persona). Además, deportistasService.vincularAcudiente deja el correo en
// `deportista.acudiente`, que es lo que ya usan el estado de cuenta y los documentos del acudiente.
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'VALIDACION' (con `errores`: { campo: mensaje }).
//
// PENDIENTE (backend): crear la cuenta de acceso del acudiente y enviarle un correo con un enlace para crear su contraseña, igual
// que a los entrenadores; aquí solo se guardan sus datos de contacto y el vínculo. El backend también debe garantizar que el
// correo no se repita (hoy, si el correo ya existe, se reutiliza el acudiente).
import { validarCampos, validarCorreo, validarNombre, validarTelefono, enLista, MENSAJES } from '../utils/validaciones.js'

export const PARENTESCOS = ['Madre', 'Padre', 'Abuelo', 'Abuela', 'Tía', 'Tío', 'Otro']

// La API usa los mismos valores sin tilde (Tia, Tio). En pantalla y en este servicio se usa la forma con tilde.
const PARENTESCO_API = { Tía: 'Tia', Tío: 'Tio' }
const PARENTESCO_DESDE_API = { Tia: 'Tía', Tio: 'Tío' }
export const parentescoParaApi = (p) => PARENTESCO_API[p] ?? p
export const parentescoDesdeApi = (p) => PARENTESCO_DESDE_API[p] ?? p
export const ACUDIENTE_VACIO = { nombre: '', apellido: '', correo: '', telefono: '', parentesco: '' }
export const MENSAJE_PARENTESCO = 'Selecciona el parentesco.'

// Reglas de los datos del acudiente (con los validadores compartidos). Devuelve { campo: mensaje } solo con los campos con error.
export function validarDatosAcudiente(datos) {
  return validarCampos(datos, {
    nombre: [(v) => validarNombre(v, { obligatorio: MENSAJES.nombresObligatorio, longitud: MENSAJES.nombresLongitud, caracteres: MENSAJES.nombresCaracteres })],
    apellido: [(v) => validarNombre(v, { obligatorio: MENSAJES.apellidosObligatorio, longitud: MENSAJES.apellidosLongitud, caracteres: MENSAJES.apellidosCaracteres })],
    correo: [(v) => validarCorreo(v, { obligatorio: MENSAJES.correoObligatorio, formato: MENSAJES.loginCorreoFormato })],
    telefono: [validarTelefono],
    parentesco: [enLista(PARENTESCOS, MENSAJE_PARENTESCO)],
  })
}

// Datos de demostración (ficticios): la cuenta demo del acudiente, con Santiago (1) y Valeria (2).
let acudientes = [{ id: 1, nombre: 'María', apellido: 'Torres', correo: 'acudiente@pradofc.co', telefono: '3001234567' }]
let vinculos = [
  { idDeportista: 1, idAcudiente: 1, parentesco: 'Madre' },
  { idDeportista: 2, idAcudiente: 1, parentesco: 'Madre' },
]
let siguienteId = acudientes.length + 1

const error = (codigo, mensaje, extra) => Object.assign(new Error(mensaje), { codigo }, extra)
const simularRed = () => new Promise((resolve) => setTimeout(resolve, 200))
const copiar = (x) => ({ ...x })
const mismoCorreo = (a, b) => String(a ?? '').trim().toLowerCase() === String(b ?? '').trim().toLowerCase()

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para registrar acudientes.')
}

export async function listarAcudientes() {
  await simularRed()
  return acudientes.map(copiar)
}

export async function obtenerAcudientePorCorreo(correo) {
  await simularRed()
  const encontrado = acudientes.find((a) => mismoCorreo(a.correo, correo))
  return encontrado ? copiar(encontrado) : null
}

// Acudiente de un deportista con el parentesco del vínculo, o null si no tiene
export async function obtenerAcudienteDe(idDeportista) {
  await simularRed()
  const vinculo = vinculos.find((v) => v.idDeportista === Number(idDeportista))
  const acudiente = vinculo && acudientes.find((a) => a.id === vinculo.idAcudiente)
  return acudiente ? { ...copiar(acudiente), parentesco: vinculo.parentesco } : null
}

// Guarda al acudiente (valida con validarDatosAcudiente). Si ya existe uno con ese correo se reutiliza y NO se cambian sus datos
// (un acudiente puede tener varios deportistas). Devuelve { acudiente, creado }.
export async function guardarAcudiente(datos, actor) {
  await simularRed()
  exigirAdministrador(actor)
  const errores = validarDatosAcudiente(datos)
  if (Object.keys(errores).length > 0) throw error('VALIDACION', 'Hay datos que corregir.', { errores })
  const existente = acudientes.find((a) => mismoCorreo(a.correo, datos.correo))
  if (existente) return { acudiente: copiar(existente), creado: false }
  const nuevo = {
    id: siguienteId++, nombre: datos.nombre.trim(), apellido: datos.apellido.trim(), correo: datos.correo.trim(), telefono: datos.telefono.trim(),
  }
  acudientes = [...acudientes, nuevo]
  return { acudiente: copiar(nuevo), creado: true }
}

// Vincula un acudiente con un deportista. Un deportista tiene un solo acudiente: si ya tenía uno, el vínculo se reemplaza.
export async function vincularAcudiente(idDeportista, idAcudiente, parentesco, actor) {
  await simularRed()
  exigirAdministrador(actor)
  if (!PARENTESCOS.includes(parentesco)) throw error('VALIDACION', 'Hay datos que corregir.', { errores: { parentesco: MENSAJE_PARENTESCO } })
  const vinculo = { idDeportista: Number(idDeportista), idAcudiente: Number(idAcudiente), parentesco }
  vinculos = [...vinculos.filter((v) => v.idDeportista !== vinculo.idDeportista), vinculo]
  return copiar(vinculo)
}
