// Foto del deportista (HU de ficha). Por ahora en MEMORIA: no se sube nada a ningún servidor. Las funciones son async y
// lanzan errores con `codigo`, como deportistasService, para cambiarlas luego sin tocar las pantallas.
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ARCHIVO_INVALIDO' (el mensaje dice el motivo).
//
// PENDIENTE (backend): guardar el archivo en Supabase Storage y la ruta en deportista.foto; obtenerFoto devolverá una URL firmada
// (o pública) en vez de un object URL del navegador. Los object URL de aquí viven solo mientras la pestaña esté abierta.
// Solo el administrador sube o quita fotos (el servicio lo simula; en producción lo valida el backend con el JWT).
import { TIPOS_FOTO, validarArchivo } from '../utils/archivos.js'

const fotos = new Map() // idDeportista -> object URL

const error = (codigo, mensaje) => Object.assign(new Error(mensaje), { codigo })
const simularRed = () => new Promise((resolve) => setTimeout(resolve, 250))

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para cambiar la foto de un deportista.')
}

// URL de la foto, o null si no tiene
export async function obtenerFoto(idDeportista) {
  await simularRed()
  return fotos.get(Number(idDeportista)) ?? null
}

// archivo: File (o { name, type, size, ... } que URL.createObjectURL acepte). JPG o PNG, de 1 byte a 5 MB.
// Reemplaza la foto anterior. Devuelve la URL nueva.
export async function guardarFoto(idDeportista, archivo, actor) {
  await simularRed()
  exigirAdministrador(actor)
  const motivo = validarArchivo(archivo, { tipos: TIPOS_FOTO })
  if (motivo) throw error('ARCHIVO_INVALIDO', motivo)
  const id = Number(idDeportista)
  const anterior = fotos.get(id)
  const url = URL.createObjectURL(archivo)
  fotos.set(id, url)
  if (anterior) URL.revokeObjectURL(anterior)
  return url
}

export async function quitarFoto(idDeportista, actor) {
  await simularRed()
  exigirAdministrador(actor)
  const id = Number(idDeportista)
  const anterior = fotos.get(id)
  if (anterior) URL.revokeObjectURL(anterior)
  fotos.delete(id)
}
