// Plantilla semanal del club (franjas fijas de lunes a sábado). Por ahora en MEMORIA, con los datos de ejemplo de
// horarioClubData.js. Las funciones son async y lanzan errores con `codigo`, como los demás servicios, para cambiarlas luego por
// endpoints del backend sin tocar las pantallas.
//
// Errores: Error con `codigo` -> 'VALIDACION' (con `errores`: { campo: mensaje }) | 'FRANJA_NO_EXISTE'.
// Las reglas están en utils/horarioClub.js (validarFranja). El mismo entrenador no puede tener dos franjas que se crucen el
// mismo día; dos categorías distintas sí pueden coincidir.
// PENDIENTE (backend): permisos (solo el administrador edita la plantilla; lo valida el backend con el JWT).
import { FRANJAS_EJEMPLO } from './horarioClubData.js'
import { validarFranja } from '../utils/horarioClub.js'

let franjas = FRANJAS_EJEMPLO.map((f) => ({ ...f }))
let siguienteId = franjas.length + 1

const error = (codigo, mensaje, extra) => Object.assign(new Error(mensaje), { codigo }, extra)
const simularRed = () => new Promise((resolve) => setTimeout(resolve, 200))
const copiar = (f) => ({ ...f })

// Deja la franja como se guarda: números donde corresponde y null donde no hay entrenador ni grupo
const limpia = (d) => ({
  idCategoria: Number(d.idCategoria),
  dia: Number(d.dia),
  horaInicio: d.horaInicio,
  horaFin: d.horaFin,
  sede: d.sede,
  idEntrenador: d.idEntrenador === '' || d.idEntrenador === null || d.idEntrenador === undefined ? null : Number(d.idEntrenador),
  grupo: d.grupo || null,
})

function exigirValida(datos, ignorarId) {
  const errores = validarFranja(datos, franjas, ignorarId)
  if (Object.keys(errores).length > 0) throw error('VALIDACION', 'Hay datos que corregir.', { errores })
}

export async function listar() {
  await simularRed()
  return franjas.map(copiar)
}

export async function crear(datos) {
  await simularRed()
  exigirValida(datos, null)
  const nueva = { id: siguienteId++, ...limpia(datos) }
  franjas = [...franjas, nueva]
  return copiar(nueva)
}

export async function editar(id, datos) {
  await simularRed()
  const actual = franjas.find((f) => f.id === Number(id))
  if (!actual) throw error('FRANJA_NO_EXISTE', 'La franja no existe.')
  exigirValida(datos, actual.id)
  const nueva = { id: actual.id, ...limpia(datos) }
  franjas = franjas.map((f) => (f.id === actual.id ? nueva : f))
  return copiar(nueva)
}

export async function eliminar(id) {
  await simularRed()
  const actual = franjas.find((f) => f.id === Number(id))
  if (!actual) throw error('FRANJA_NO_EXISTE', 'La franja no existe.')
  franjas = franjas.filter((f) => f.id !== actual.id)
  return { id: actual.id }
}
