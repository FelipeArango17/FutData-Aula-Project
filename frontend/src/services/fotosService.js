// Foto del deportista (HU de ficha). Por ahora en MEMORIA: no se sube nada a ningún servidor. Las funciones son async y
// lanzan errores con `codigo`, como deportistasService, para cambiarlas luego sin tocar las pantallas.
//
// Errores: Error con `codigo` -> 'SIN_PERMISO' | 'ARCHIVO_INVALIDO' (el mensaje dice el motivo).
//
// PENDIENTE (backend): guardar el archivo en Supabase Storage y la ruta en deportista.foto; obtenerFoto devolverá una URL firmada
// (o pública) en vez de un object URL del navegador. Los object URL de aquí viven solo mientras la pestaña esté abierta.
// Solo el administrador sube o quita fotos (el servicio lo simula; en producción lo valida el backend con el JWT).
import { TIPOS_FOTO, validarArchivo } from '../utils/archivos.js'
import { apiRequest, subirArchivo } from './apiClient.js'
import { usarApiDe } from './usarApi.js'
import { errorDeDeportistas } from '../utils/deportistasApi.js'

// Con "deportistas" en VITE_API_MODULOS la foto es la del backend: POST/DELETE /api/deportistas/{id}/foto (parte multipart "archivo",
// JPG o PNG de hasta 5 MB, validado por el backend) y la URL firmada (60 min) viene en el propio deportista (`foto`).
const usarApi = usarApiDe('deportistas')
const rutaFoto = (id) => `/deportistas/${encodeURIComponent(id)}/foto`
const llamar = async (operacion) => {
  try {
    return await operacion()
  } catch (error) {
    throw errorDeDeportistas(error)
  }
}

// Las fotos viven en este módulo (no en un componente): sobreviven a la navegación entre páginas hasta recargar la pestaña.
// Los object URL solo se revocan al reemplazar o quitar la foto, así la lista nunca recibe una imagen rota.
const fotos = new Map() // idDeportista -> object URL

const error = (codigo, mensaje) => Object.assign(new Error(mensaje), { codigo })
const simularRed = () => new Promise((resolve) => setTimeout(resolve, 250))

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para cambiar la foto de un deportista.')
}

// URL de la foto, o null si no tiene
export async function obtenerFoto(idDeportista) {
  if (usarApi) return (await llamar(() => apiRequest(`/deportistas/${encodeURIComponent(idDeportista)}`))).foto ?? null
  await simularRed()
  return fotos.get(Number(idDeportista)) ?? null
}

// Fotos de varios deportistas de una vez, para las listas (una sola llamada por pantalla, no una por fila):
// { idDeportista: url } solo con los que tienen foto. Sin `ids` devuelve todas. Los ids pueden ser texto.
// PENDIENTE (backend): la lista de deportistas traerá la ruta de la foto (deportista.foto) y no hará falta esta llamada aparte.
export async function obtenerFotos(ids) {
  if (usarApi) return {} // las listas del backend ya traen la URL de cada foto: no hace falta otra llamada
  await simularRed()
  const buscados = ids ? new Set(ids.map(Number)) : null
  return Object.fromEntries([...fotos].filter(([id]) => !buscados || buscados.has(id)))
}

// archivo: File (o { name, type, size, ... } que URL.createObjectURL acepte). JPG o PNG, de 1 byte a 5 MB.
// Reemplaza la foto anterior. Devuelve la URL nueva.
export async function guardarFoto(idDeportista, archivo, actor) {
  if (usarApi) return (await llamar(() => subirArchivo(rutaFoto(idDeportista), archivo))).foto ?? null
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
  if (usarApi) {
    await llamar(() => apiRequest(rutaFoto(idDeportista), { method: 'DELETE' }))
    return
  }
  await simularRed()
  exigirAdministrador(actor)
  const id = Number(idDeportista)
  const anterior = fotos.get(id)
  if (anterior) URL.revokeObjectURL(anterior)
  fotos.delete(id)
}
