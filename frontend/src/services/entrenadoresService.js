// Servicio de entrenadores (HU-025). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET/POST/PUT /entrenadores) sin tocar las pantallas.
//
// Forma de datos propuesta para el frontend:
//   Entrenador = { id, nombre, apellido, tipoDocumento, documento, telefono, correo,
//                  especialidad, categorias, horarioTrabajo }
//     tipoDocumento   "Cédula de ciudadanía" | "Cédula de extranjería" | "Pasaporte"
//     documento       solo dígitos, 6 a 12
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
export const TIPOS_DOCUMENTO = ['Cédula de ciudadanía', 'Cédula de extranjería', 'Pasaporte']
export const ESPECIALIDADES = ['Porteros', 'Preparación física', 'Formación técnica', 'Táctica']

let entrenadores = [
  {
    id: 1,
    nombre: 'Andrés',
    apellido: 'Mora',
    tipoDocumento: 'Cédula de ciudadanía',
    documento: '1017234567',
    telefono: '3105558821',
    correo: 'andres.mora@pradofc.co',
    especialidad: 'Formación técnica',
    categorias: ['Sub-12'],
    horarioTrabajo: 'Lunes, miércoles y sábado, 4:00 p. m. a 7:00 p. m.',
  },
  {
    id: 2,
    nombre: 'Carlos',
    apellido: 'Díaz',
    tipoDocumento: 'Cédula de ciudadanía',
    documento: '71654321',
    telefono: '3156674410',
    correo: 'carlos.diaz@pradofc.co',
    especialidad: 'Preparación física',
    categorias: ['Sub-8', 'Sub-10', 'Sub-14', 'Sub-16'],
    horarioTrabajo: 'Lunes a viernes, 3:30 p. m. a 7:30 p. m.',
  },
]
let siguienteId = 3

const copiar = (e) => ({ ...e, categorias: [...e.categorias] })

// Deja los datos como se guardan: sin espacios sobrantes y con las categorías en el orden oficial
const datosLimpios = (d) => ({
  nombre: d.nombre.trim(),
  apellido: d.apellido.trim(),
  tipoDocumento: d.tipoDocumento,
  documento: d.documento.trim(),
  telefono: d.telefono.trim(),
  correo: d.correo.trim(),
  especialidad: d.especialidad.trim(),
  categorias: CATEGORIAS.filter((c) => d.categorias.includes(c)),
  horarioTrabajo: (d.horarioTrabajo ?? '').trim(),
})

export async function listar() {
  return entrenadores.map(copiar)
}

export async function obtener(id) {
  const encontrado = entrenadores.find((e) => e.id === Number(id))
  return encontrado ? copiar(encontrado) : null
}

// Duplicado = mismo documento (tipo + número) o mismo correo (sin distinguir mayúsculas) en OTRO entrenador.
// Devuelve { campo: 'documento' | 'correo' } o null. Al editar se pasa ignorarId para no chocar consigo mismo.
export async function detectarDuplicado(datos, ignorarId = null) {
  const documento = (datos.documento ?? '').trim()
  const correo = (datos.correo ?? '').trim().toLowerCase()
  const otros = entrenadores.filter((e) => e.id !== Number(ignorarId))
  if (documento && otros.some((e) => e.tipoDocumento === datos.tipoDocumento && e.documento === documento)) return { campo: 'documento' }
  if (correo && otros.some((e) => e.correo.toLowerCase() === correo)) return { campo: 'correo' }
  return null
}

async function validarDuplicado(datos, ignorarId) {
  const duplicado = await detectarDuplicado(datos, ignorarId)
  if (duplicado) throw Object.assign(new Error(`Entrenador duplicado: ${duplicado.campo}`), { duplicado })
}

export async function crear(datos) {
  await validarDuplicado(datos, null)
  const nuevo = { id: siguienteId++, ...datosLimpios(datos) }
  entrenadores = [...entrenadores, nuevo]
  return copiar(nuevo)
}

export async function editar(id, datos) {
  const actual = entrenadores.find((e) => e.id === Number(id))
  if (!actual) throw new Error('El entrenador no existe.')
  await validarDuplicado(datos, actual.id)
  const actualizado = { id: actual.id, ...datosLimpios(datos) }
  entrenadores = entrenadores.map((e) => (e.id === actual.id ? actualizado : e))
  return copiar(actualizado)
}
