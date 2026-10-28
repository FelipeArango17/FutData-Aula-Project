// Servicio de deportistas y grupos por categoría (HU-011). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET /categorias, GET /deportistas?categoria=&texto=, PUT /deportistas/:id/categoria) sin tocar las pantallas.
// También lo reutilizarán la asistencia y el estado de cuenta.
//
// Forma de datos propuesta para el frontend:
//   Categoria  = { id, nombre, activa }
//   Deportista = { id, nombre, apellido, tipoDocumento, documento, fechaNacimiento, idCategoria, activo, acudiente }
//     fechaNacimiento  "AAAA-MM-DD"
//     idCategoria      número (Deportista.id_categoria del diccionario de datos)
//     activo           boolean
//     acudiente        correo de la cuenta del acudiente, o null (lo usará el estado de cuenta)
//
// Errores: Error con `codigo` -> 'CATEGORIA_NO_EXISTE' | 'CATEGORIA_NO_VIGENTE' | 'DEPORTISTA_NO_EXISTE' | 'ERROR_DE_RED'.
//
// PENDIENTE: confirmar con el modelo de datos (docs/info-sprints/sprint-1/diccionario-datos.md):
// - D3: el deportista guarda su categoría (`idCategoria`, no se calcula por edad). Falta definir edad_minima/edad_maxima
//   y qué pasa al cambiar de año; este servicio NO valida que la edad encaje con la categoría.
// - "Vigente" = categoría activa (`activa`). `Categoria` aún no tiene ese campo en el diccionario.
// - Un deportista pertenece a UNA sola categoría a la vez.
// - `acudiente` como correo es un atajo de la demo: en el modelo, Acudiente referencia a Deportista (N:1).
// - Los datos de la demo son ficticios; no hay datos reales de menores.
import { CATEGORIAS, HOY_EJEMPLO } from './entrenamientosService.js'

export { HOY_EJEMPLO }
export const ACUDIENTE_DEMO = 'acudiente@pradofc.co'

// PENDIENTE: las categorías salen de la lista de entrenamientosService (CATEGORIAS) para no duplicarla.
// Sub-18 existe solo para probar "categoría no vigente"; no está en CATEGORIAS ni tiene entrenador.
let categorias = [
  ...CATEGORIAS.map((nombre, i) => ({ id: i + 1, nombre, activa: true })),
  { id: CATEGORIAS.length + 1, nombre: 'Sub-18', activa: false },
]

const TI = 'Tarjeta de identidad'
const d = (id, nombre, apellido, documento, fechaNacimiento, idCategoria, activo = true, acudiente = null) => ({
  id, nombre, apellido, tipoDocumento: TI, documento, fechaNacimiento, idCategoria, activo, acudiente,
})

// Sub-16 (id 5) queda sin deportistas a propósito para probar el estado vacío.
let deportistas = [
  d(1, 'Santiago', 'Ríos Demo', '1100000001', '2018-03-14', 1, true, ACUDIENTE_DEMO),
  d(2, 'Valeria', 'Ríos Demo', '1100000002', '2017-11-02', 1, true, ACUDIENTE_DEMO),
  d(3, 'Emilio', 'Pardo Ejemplo', '1100000003', '2018-07-21', 1),
  d(4, 'Isabella', 'Quintero Ejemplo', '1100000004', '2018-01-30', 1),
  d(5, 'Thiago', 'Marín Ejemplo', '1100000005', '2017-10-25', 1, false),
  d(6, 'Mateo', 'Vélez Demo', '1100000006', '2016-05-09', 2),
  d(7, 'Juan Pablo', 'Cano Demo', '1100000007', '2016-09-17', 2),
  d(8, 'Samuel', 'Londoño Demo', '1100000008', '2016-02-12', 2),
  d(9, 'Sofía', 'Henao Ejemplo', '1100000009', '2015-12-04', 2),
  d(10, 'Dylan', 'Restrepo Ejemplo', '1100000010', '2016-06-28', 2, false),
  d(11, 'Emmanuel', 'Arias Demo', '1100000011', '2014-08-19', 3),
  d(12, 'Tomás', 'Giraldo Demo', '1100000012', '2014-04-03', 3),
  d(13, 'Nicolás', 'Mejía Demo', '1100000013', '2014-10-11', 3),
  d(14, 'Mariana', 'Zapata Ejemplo', '1100000014', '2014-01-26', 3),
  d(15, 'Juan Sebastián Alejandro', 'Hernández de la Cruz Ejemplo', '1100000015', '2014-12-08', 3),
  d(16, 'Luciana', 'Osorio Ejemplo', '1100000016', '2013-11-30', 3, false),
  d(17, 'Andrés', 'Cardona Ejemplo', '1100000017', '2012-06-15', 4),
  d(18, 'Camila', 'Betancur Ejemplo', '1100000018', '2012-09-22', 4),
  d(19, 'Daniel', 'Cifuentes Ejemplo', '1100000019', '2012-03-07', 4),
  d(20, 'Gabriela', 'Montoya Ejemplo', '1100000020', '2011-12-19', 4),
  d(21, 'Esteban', 'Ocampo Ejemplo', '1100000021', '2012-05-01', 4),
]

// Simulación de red: retardo y fallo se pueden cambiar (pruebas y verificación manual del estado de error).
let retardoMs = 250
let fallosPendientes = 0
export function configurarSimulacion({ retardo, fallos } = {}) {
  if (retardo !== undefined) retardoMs = retardo
  if (fallos !== undefined) fallosPendientes = fallos
}

const error = (codigo, mensaje) => Object.assign(new Error(mensaje), { codigo })
const esperar = () => new Promise((resolve) => setTimeout(resolve, retardoMs))

async function simularRed() {
  await esperar()
  if (fallosPendientes > 0) {
    fallosPendientes -= 1
    throw error('ERROR_DE_RED', 'No se pudo conectar con el servidor.')
  }
}

const copiar = (x) => ({ ...x })
// Sin mayúsculas ni tildes: "ríos" == "RIOS"
const normalizar = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

// Edad en años cumplidos a la fecha `hoy` (por defecto el "hoy" de la demo). Se calcula sobre el texto AAAA-MM-DD.
export function calcularEdad(fechaNacimiento, hoy = HOY_EJEMPLO) {
  const [a, m, dia] = fechaNacimiento.split('-').map(Number)
  const [ah, mh, dh] = hoy.split('-').map(Number)
  return ah - a - (mh < m || (mh === m && dh < dia) ? 1 : 0)
}

export async function listarCategorias() {
  await simularRed()
  return categorias.map(copiar)
}

// idCategoria: solo los de esa categoría (lanza CATEGORIA_NO_EXISTE si no existe). texto: nombre o documento.
export async function listarDeportistas({ idCategoria, texto } = {}) {
  await simularRed()
  let lista = deportistas
  if (idCategoria !== undefined && idCategoria !== null && idCategoria !== '') {
    if (!categorias.some((c) => c.id === Number(idCategoria))) throw error('CATEGORIA_NO_EXISTE', 'La categoría no existe.')
    lista = lista.filter((x) => x.idCategoria === Number(idCategoria))
  }
  const buscado = normalizar(texto)
  if (buscado) {
    lista = lista.filter((x) => normalizar(`${x.nombre} ${x.apellido}`).includes(buscado) || normalizar(x.documento).includes(buscado))
  }
  return lista.map(copiar)
}

export async function obtenerDeportista(id) {
  await simularRed()
  const encontrado = deportistas.find((x) => x.id === Number(id))
  if (!encontrado) throw error('DEPORTISTA_NO_EXISTE', 'El deportista no existe.')
  return copiar(encontrado)
}

// Sin pantalla en esta tarea: queda para reutilizar (por ejemplo al inscribir o cambiar de grupo).
export async function asignarCategoria(idDeportista, idCategoria) {
  await simularRed()
  const actual = deportistas.find((x) => x.id === Number(idDeportista))
  if (!actual) throw error('DEPORTISTA_NO_EXISTE', 'El deportista no existe.')
  const categoria = categorias.find((c) => c.id === Number(idCategoria))
  if (!categoria) throw error('CATEGORIA_NO_EXISTE', 'La categoría no existe.')
  if (!categoria.activa) throw error('CATEGORIA_NO_VIGENTE', 'La categoría no está vigente.')
  const actualizado = { ...actual, idCategoria: categoria.id }
  deportistas = deportistas.map((x) => (x.id === actual.id ? actualizado : x))
  return copiar(actualizado)
}
