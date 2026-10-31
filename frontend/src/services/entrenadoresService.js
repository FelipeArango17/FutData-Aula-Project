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
import { apiRequest } from './apiClient.js'
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
    activo: true,
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
    activo: true,
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
  categorias: CATEGORIAS.filter((c) => (d.categorias ?? []).includes(c)),
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

async function crearMock(datos, { exigirCategorias = true } = {}) {
  await validarDuplicado(datos, null)
  const nuevo = { id: crypto.randomUUID(), ...datosLimpios(datos), activo: true }
  if (exigirCategorias && nuevo.categorias.length === 0) throw Object.assign(new Error('Selecciona al menos una categoría.'), { campos: ['categorias'], estado: 400 })
  entrenadores = [...entrenadores, nuevo]
  return copiar(nuevo)
}

async function editarMock(id, datos) {
  const actual = buscarPorId(id)
  if (!actual) throw new Error('El entrenador no existe.')
  await validarDuplicado(datos, actual.id)
  const actualizado = { id: actual.id, ...datosLimpios(datos), activo: actual.activo }
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

// Datos personales para POST/PUT: sin categorías (se asignan aparte) ni estado.
const datosPersonales = (datos) => {
  const personales = datosLimpios(datos)
  delete personales.categorias
  return personales
}

const conflicto = (mensaje, campos) => Object.assign(new Error(mensaje), { estado: 409, mensaje, campos })

// ---- Simulado de las operaciones que la API ya tiene (solo para las pruebas) ----
async function crearDatosMock(datos) {
  return crearMock(datos, { exigirCategorias: false })
}

async function editarDatosMock(id, datos) {
  const actual = buscarPorId(id)
  if (!actual) throw Object.assign(new Error('El entrenador no existe.'), { estado: 404 })
  await validarDuplicado(datos, actual.id)
  const actualizado = { ...actual, ...datosPersonales(datos) }
  entrenadores = entrenadores.map((e) => (e.id === actual.id ? actualizado : e))
  return copiar(actualizado)
}

// Una categoría tiene como máximo un entrenador. Las que ya no se envían quedan sin entrenador; las de otro exigen reasignar.
async function asignarCategoriasMock(id, categorias, reasignar) {
  const actual = buscarPorId(id)
  if (!actual) throw Object.assign(new Error('El entrenador no existe.'), { estado: 404 })
  if (!actual.activo) throw conflicto('El entrenador está inactivo. Actívalo antes de asignarle categorías.', ['activo'])
  const pedidas = CATEGORIAS.filter((c) => categorias.includes(c))
  const deOtro = pedidas.filter((c) => entrenadores.some((e) => e.id !== actual.id && e.categorias.includes(c)))
  if (deOtro.length > 0 && !reasignar) {
    throw conflicto(`${deOtro.join(', ')} ya ${deOtro.length === 1 ? 'tiene' : 'tienen'} otro entrenador. Confirma la reasignación.`, ['categorias'])
  }
  entrenadores = entrenadores.map((e) => (e.id === actual.id
    ? { ...e, categorias: pedidas }
    : { ...e, categorias: e.categorias.filter((c) => !pedidas.includes(c)) }))
  return copiar(buscarPorId(actual.id))
}

async function cambiarEstadoMock(id, activo) {
  const actual = buscarPorId(id)
  if (!actual) throw Object.assign(new Error('El entrenador no existe.'), { estado: 404 })
  const nuevo = { ...actual, activo, categorias: activo ? actual.categorias : [] }
  entrenadores = entrenadores.map((e) => (e.id === actual.id ? nuevo : e))
  return copiar(nuevo)
}

const RANGOS_EDAD = { 'Sub-8': [0, 7], 'Sub-10': [8, 9], 'Sub-12': [10, 11], 'Sub-14': [12, 13], 'Sub-16': [14, 15] }
async function listarCategoriasMock() {
  return CATEGORIAS.map((nombre) => {
    const dueno = entrenadores.find((e) => e.categorias.includes(nombre))
    const [edadMinima, edadMaxima] = RANGOS_EDAD[nombre]
    return { id: nombre, nombre, edadMinima, edadMaxima, entrenador: dueno ? { id: dueno.id, nombre: dueno.nombre, apellido: dueno.apellido } : null }
  })
}

// ---- API real ----
// La API devuelve null en los opcionales vacíos; los campos de las pantallas esperan texto.
const desdeApi = (e) => ({
  ...e,
  categorias: e.categorias ?? [],
  horarioTrabajo: e.horarioTrabajo ?? '',
  direccion: e.direccion ?? '',
  activo: e.activo !== false,
})
const ruta = (id) => `/entrenadores/${encodeURIComponent(id)}`

const api = {
  async listar() {
    return (await apiRequest('/entrenadores')).map(desdeApi)
  },
  async obtener(id) {
    try {
      return desdeApi(await apiRequest(ruta(id)))
    } catch (error) {
      if (error.estado === 404) return null
      throw error
    }
  },
  async crearDatos(datos) {
    return desdeApi(await apiRequest('/entrenadores', { method: 'POST', body: JSON.stringify(datosPersonales(datos)) }))
  },
  async editarDatos(id, datos) {
    return desdeApi(await apiRequest(ruta(id), { method: 'PUT', body: JSON.stringify(datosPersonales(datos)) }))
  },
  async asignarCategorias(id, categorias, reasignar) {
    return desdeApi(await apiRequest(`${ruta(id)}/categorias`, {
      method: 'PUT',
      body: JSON.stringify({ categorias: CATEGORIAS.filter((c) => categorias.includes(c)), reasignar: Boolean(reasignar) }),
    }))
  },
  async cambiarEstado(id, activo) {
    return desdeApi(await apiRequest(`${ruta(id)}/estado`, { method: 'PATCH', body: JSON.stringify({ activo }) }))
  },
  async listarCategorias() {
    return (await apiRequest('/categorias')).map((c) => ({ ...c, entrenador: c.entrenador ?? null }))
  },
}

const usarApi = import.meta.env.MODE !== 'test'

export async function listar() {
  return usarApi ? api.listar() : listarMock()
}

// El entrenador, o null si no existe (404).
export async function obtener(id) {
  return usarApi ? api.obtener(id) : obtenerMock(id)
}

export async function detectarDuplicado(datos, ignorarId = null) {
  if (usarApi) return null // el backend revisa los duplicados y responde 409 con `campos`
  const documento = (datos?.documento ?? '').trim()
  const correo = (datos?.correo ?? '').trim().toLowerCase()
  const otros = entrenadores.filter((e) => e.id !== idReal(ignorarId))
  if (documento && otros.some((e) => e.tipoDocumento === datos.tipoDocumento && e.documento === documento)) return { campo: 'documento' }
  if (correo && otros.some((e) => e.correo.toLowerCase() === correo)) return { campo: 'correo' }
  return null
}

// Registrar es en dos pasos: POST de los datos personales y PUT de las categorías. Las pantallas los llaman por separado
// (crearDatos y asignarCategorias) para poder reintentar solo la asignación si el segundo falla.
export async function crearDatos(datos) {
  return usarApi ? api.crearDatos(datos) : crearDatosMock(datos)
}

export async function editarDatos(id, datos) {
  return usarApi ? api.editarDatos(id, datos) : editarDatosMock(id, datos)
}

// Deja al entrenador con EXACTAMENTE esas categorías. Las que no se envían quedan sin entrenador; las que hoy tiene otro
// entrenador responden 409 (campos: ['categorias']) hasta repetir con reasignar = true; un inactivo responde 409 (['activo']).
export async function asignarCategorias(id, categorias, reasignar = false) {
  return usarApi ? api.asignarCategorias(id, categorias, reasignar) : asignarCategoriasMock(id, categorias, reasignar)
}

// Desactivar deja sin entrenador sus categorías; reactivar no se las devuelve.
export async function cambiarEstado(id, activo) {
  return usarApi ? api.cambiarEstado(id, activo) : cambiarEstadoMock(id, activo)
}

// Las categorías en el orden oficial; `entrenador` es null si no tiene.
export async function listarCategorias() {
  return usarApi ? api.listarCategorias() : listarCategoriasMock()
}

// Datos y categorías de una vez. Si el segundo paso falla, el entrenador queda guardado y el error trae `parcial` (el
// entrenador con sus datos) para que quien llama pueda reintentar solo la asignación.
async function conCategorias(guardado, categorias) {
  if (!categorias?.length) return guardado
  try {
    return await asignarCategorias(guardado.id, categorias, false)
  } catch (error) {
    throw Object.assign(error, { parcial: guardado })
  }
}

export async function crear(datos) {
  if (!usarApi) return crearMock(datos)
  return conCategorias(await api.crearDatos(datos), datos.categorias)
}

export async function editar(id, datos) {
  if (!usarApi) return editarMock(id, datos)
  return conCategorias(await api.editarDatos(id, datos), datos.categorias)
}
