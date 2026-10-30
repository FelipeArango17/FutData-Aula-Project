// Servicio de entrenadores (HU-025). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET/POST/PUT /entrenadores) sin tocar las pantallas.
//
// Forma de datos propuesta para el frontend:
//   Entrenador = { id (UUID), nombre, apellido, tipoDocumento, documento, fechaNacimiento, telefono, correo,
//                  especialidad, categorias, horarioTrabajo, direccion }
//     tipoDocumento   "CC" | "CE" | "PASAPORTE"
//     documento       dígitos para CC/CE; letras y dígitos para PASAPORTE, 6 a 12
//     telefono        10 dígitos
//     especialidad    texto, por ejemplo "Porteros"
//     categorias      lista de categorías, por ejemplo ['Sub-8', 'Sub-10']
//     horarioTrabajo  texto libre, opcional
//
// PENDIENTE: confirmar con el modelo de datos (docs/info-sprints/sprint-1/diccionario-datos.md, sección 5):
// - D1: ¿una categoría tiene un solo entrenador o varios? El frontend permite varias casillas por
//   entrenador y NO valida que una categoría quede con un solo entrenador.
// - `especialidad` y `categorias` son campos nuevos que el diccionario aún no tiene en Entrenador.
// - La tabla Entrenador también tiene fecha_nacimiento, dirección y documentos, que esta pantalla
//   NO captura todavía.
// - La base de datos también deberá exigir que documento y correo no se repitan (observación 2).
import { CATEGORIAS } from './entrenamientosService.js'

export { CATEGORIAS }
export const TIPOS_DOCUMENTO = ['CC', 'CE', 'PASAPORTE']
export const ETIQUETAS_TIPO_DOCUMENTO = { CC: 'Cédula de ciudadanía', CE: 'Cédula de extranjería', PASAPORTE: 'Pasaporte' }
export const ESPECIALIDADES = ['Porteros', 'Preparación física', 'Formación técnica', 'Táctica']

let entrenadores = [
  {
    id: '11111111-1111-4111-8111-111111111111',
    nombre: 'Andrés',
    apellido: 'Mora',
    tipoDocumento: 'CC',
    documento: '1017234567',
    fechaNacimiento: '1990-05-14',
    telefono: '3105558821',
    correo: 'andres.mora@pradofc.co',
    especialidad: 'Formación técnica',
    categorias: ['Sub-12'],
    horarioTrabajo: 'Lunes, miércoles y sábado, 4:00 p. m. a 7:00 p. m.',
    direccion: 'Calle 40 Sur 75b-81',
  },
  {
    id: '22222222-2222-4222-8222-222222222222',
    nombre: 'Carlos',
    apellido: 'Díaz',
    tipoDocumento: 'CC',
    documento: '71654321',
    fechaNacimiento: '1988-08-20',
    telefono: '3156674410',
    correo: 'carlos.diaz@pradofc.co',
    especialidad: 'Preparación física',
    categorias: ['Sub-8', 'Sub-10', 'Sub-14', 'Sub-16'],
    horarioTrabajo: 'Lunes a viernes, 3:30 p. m. a 7:30 p. m.',
    direccion: 'Carrera 10 # 20-30',
  },
]
const copiar = (e) => ({ ...e, categorias: [...e.categorias] })
const buscarPorId = (id) => {
  const texto = String(id)
  if (/^\d+$/.test(texto)) return entrenadores[Number(texto) - 1]
  return entrenadores.find((e) => e.id === texto)
}
const idReal = (id) => (/^\d+$/.test(String(id)) ? buscarPorId(id)?.id : String(id))

// Deja los datos como se guardan: sin espacios sobrantes y con las categorías en el orden oficial
const datosLimpios = (d) => ({
  nombre: d.nombre.trim(),
  apellido: d.apellido.trim(),
  tipoDocumento: d.tipoDocumento,
  documento: d.documento.trim(),
  fechaNacimiento: d.fechaNacimiento,
  telefono: d.telefono.trim(),
  correo: d.correo.trim(),
  especialidad: d.especialidad.trim(),
  categorias: CATEGORIAS.filter((c) => d.categorias.includes(c)),
  horarioTrabajo: (d.horarioTrabajo ?? '').trim(),
  direccion: (d.direccion ?? '').trim(),
})

async function listarMock() {
  return entrenadores.map(copiar)
}

async function obtenerMock(id) {
  const encontrado = buscarPorId(id)
  return encontrado ? copiar(encontrado) : null
}

// Duplicado = mismo documento (tipo + número) o mismo correo (sin distinguir mayúsculas) en OTRO entrenador.
// Devuelve { campo: 'documento' | 'correo' } o null. Al editar se pasa ignorarId para no chocar consigo mismo.
async function detectarDuplicadoMock(datos, ignorarId = null) {
  const documento = (datos?.documento ?? '').trim()
  const correo = (datos?.correo ?? '').trim().toLowerCase()
  const otros = entrenadores.filter((e) => e.id !== idReal(ignorarId))
  if (documento && otros.some((e) => e.tipoDocumento === datos.tipoDocumento && e.documento === documento)) return { campo: 'documento' }
  if (correo && otros.some((e) => e.correo.toLowerCase() === correo)) return { campo: 'correo' }
  return null
}

async function validarDuplicado(datos, ignorarId) {
  const duplicado = await detectarDuplicadoMock(datos, ignorarId)
  if (duplicado) throw Object.assign(new Error(`Entrenador duplicado: ${duplicado.campo}`), { duplicado })
}

async function crearMock(datos) {
  await validarDuplicado(datos, null)
  const nuevo = { id: crypto.randomUUID(), ...datosLimpios(datos) }
  if (nuevo.categorias.length === 0) throw Object.assign(new Error('Selecciona al menos una categoría.'), { campos: ['categorias'], estado: 400 })
  entrenadores = [...entrenadores, nuevo]
  return copiar(nuevo)
}

async function editarMock(id, datos) {
  const actual = buscarPorId(id)
  if (!actual) throw new Error('El entrenador no existe.')
  await validarDuplicado(datos, actual.id)
  const actualizado = { id: actual.id, ...datosLimpios(datos) }
  const quitadas = actual.categorias.filter((categoria) => !actualizado.categorias.includes(categoria))
  const conflictos = quitadas.filter((categoria) => !entrenadores.some((e) => e.id !== actual.id && e.categorias.includes(categoria)))
  if (conflictos.length > 0) {
    throw Object.assign(new Error('No puedes quitar una categoría si no queda asignada a otro entrenador.'), {
      estado: 409,
      campos: ['categorias'],
    })
  }
  entrenadores = entrenadores.map((e) => (e.id === actual.id ? actualizado : e))
  return copiar(actualizado)
}

const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/api\/?$/, '')
const datosPersonales = (datos) => {
  const personales = { ...datos }
  delete personales.categorias
  return datosLimpios(personales)
}

async function apiRequest(ruta, opciones = {}) {
  const respuesta = await fetch(`${API_BASE}/api${ruta}`, {
    headers: { 'Content-Type': 'application/json', ...opciones.headers },
    ...opciones,
  })
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => ({}))
    const error = new Error(cuerpo.mensaje ?? 'No se pudo completar la operación.')
    Object.assign(error, { estado: respuesta.status, campos: cuerpo.campos ?? [] })
    throw error
  }
  return respuesta.status === 204 ? null : respuesta.json()
}

const usarApi = import.meta.env.MODE !== 'test'

export async function listar() {
  return usarApi ? apiRequest('/entrenadores') : listarMock()
}

export async function obtener(id) {
  return usarApi ? apiRequest(`/entrenadores/${encodeURIComponent(id)}`) : obtenerMock(id)
}

export async function detectarDuplicado(datos, ignorarId = null) {
  if (usarApi) return null
  const documento = (datos?.documento ?? '').trim()
  const correo = (datos?.correo ?? '').trim().toLowerCase()
  const otros = entrenadores.filter((e) => e.id !== idReal(ignorarId))
  if (documento && otros.some((e) => e.tipoDocumento === datos.tipoDocumento && e.documento === documento)) return { campo: 'documento' }
  if (correo && otros.some((e) => e.correo.toLowerCase() === correo)) return { campo: 'correo' }
  return null
}

export async function crear(datos) {
  if (!usarApi) return crearMock(datos)
  const creado = await apiRequest('/entrenadores', { method: 'POST', body: JSON.stringify(datosPersonales(datos)) })
  return apiRequest(`/entrenadores/${encodeURIComponent(creado.id)}/categorias`, {
    method: 'PUT',
    body: JSON.stringify({ categorias: datos.categorias, reasignar: false }),
  })
}

export async function editar(id, datos) {
  if (!usarApi) return editarMock(id, datos)
  await apiRequest(`/entrenadores/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(datosPersonales(datos)) })
  return apiRequest(`/entrenadores/${encodeURIComponent(id)}/categorias`, {
    method: 'PUT',
    body: JSON.stringify({ categorias: datos.categorias, reasignar: false }),
  })
}
