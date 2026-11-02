// Servicio de deportistas y grupos por categoría (HU-011). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async y devuelven lo que devolvería una API REST, para cambiarlas luego por
// Axios (GET /categorias, GET /deportistas?categoria=&texto=, PUT /deportistas/:id/categoria) sin tocar las pantallas.
// También lo reutilizarán la asistencia y el estado de cuenta.
//
// Forma de datos propuesta para el frontend:
//   Categoria  = { id, nombre, activa }
//   Deportista = { id, nombre, apellido, tipoDocumento, documento, fechaNacimiento, telefono, direccion, idCategoria, activo,
//                  acudiente, creadoPor?, creadoEn? }
//     fechaNacimiento  "AAAA-MM-DD"
//     telefono         10 dígitos (celular de contacto); direccion: texto de 5 a 120 caracteres
//     idCategoria      número (Deportista.id_categoria del diccionario de datos) o null si aún no tiene grupo
//     creadoPor        correo de quien lo registró; creadoEn: fecha y hora ISO del registro (solo los creados con crearDeportista)
//     activo           boolean
//     acudiente        correo de la cuenta del acudiente, o null (lo usará el estado de cuenta)
//     grupoEntrenamiento  'mañana' | 'tarde' | null. Solo Cachorros y Semillero lo tienen (y es obligatorio ahí); en las demás
//                      categorías es null. El club decidió que el grupo no se guarda en la base de datos: vive SOLO en este servicio
//                      de ejemplo en memoria, no está en ningún contrato de API ni en el modelo de datos.
//     posicion         una de POSICIONES o null (opcional; las pantallas muestran "Sin definir")
//
// Errores: Error con `codigo` -> 'CATEGORIA_NO_EXISTE' | 'CATEGORIA_NO_VIGENTE' | 'CATEGORIA_NO_CORRESPONDE_EDAD' |
//   'DEPORTISTA_NO_EXISTE' | 'ERROR_DE_RED' | 'SIN_PERMISO' | 'DOCUMENTO_DUPLICADO' | 'VALIDACION' (con `errores`: { campo: mensaje }) |
//   'SIN_CAMBIOS'.
//   CATEGORIA_NO_CORRESPONDE_EDAD: la categoría existe y está vigente, pero no es la que da categoriaPorFecha(fechaNacimiento)
//   (utils/categorias.js, regla provisional por año de nacimiento). Se comprueba DESPUÉS de CATEGORIA_NO_EXISTE / CATEGORIA_NO_VIGENTE.
//   La regla la aplican asignarCategoria, crearDeportista y actualizarDeportista (en esta última solo si cambió la categoría o la fecha).
//
// PENDIENTE: confirmar con el modelo de datos (docs/info-sprints/sprint-1/diccionario-datos.md):
// - D3: el deportista guarda su categoría (`idCategoria`, no se calcula por edad). Falta definir edad_minima/edad_maxima
//   y qué pasa al cambiar de año; este servicio NO valida que la edad encaje con la categoría.
// - "Vigente" = categoría activa (`activa`). `Categoria` aún no tiene ese campo en el diccionario.
// - Un deportista pertenece a UNA sola categoría a la vez.
// - `acudiente` como correo es un atajo de la demo: en el modelo, Acudiente referencia a Deportista (N:1).
// - Los datos de la demo son ficticios; no hay datos reales de menores. Teléfonos y direcciones también son inventados.
// - HU-008: la historia dice "administrador o secretaria"; hoy solo existe el rol administrador, así que solo él puede
//   registrar deportistas (crearDeportista lanza SIN_PERMISO para los demás roles).
// - HU-008 / D3: la historia dice que se "guarda la edad", pero la edad NO se guarda: se calcula siempre desde la fecha de
//   nacimiento (calcularEdad). Hay que confirmarlo con Juan.
// - Documento duplicado: se compara solo por número de documento (sin espacios ni puntos), sin importar el tipo. El mensaje
//   no revela quién es el otro deportista. En producción lo garantiza una restricción única en la base de datos.
// - Un deportista creado sin categoría (idCategoria null) todavía no aparece en ninguna lista por grupo.
// - Los tipos de documento de los deportistas son propios (no se mezclan con los de los entrenadores).
// - HU-010: ver y editar fichas es solo del administrador. Cada edición guarda { fechaHora (ISO), responsable (correo del actor),
//   cambios: [{ campo, antes, despues }] } con valores crudos (idCategoria como id); el historial nunca se borra.
//   Pasar un deportista a activo/inactivo desde la ficha NO se implementó (queda para decidir con Juan).
// - El historial lo devuelve en orden cronológico (de la edición más antigua a la más reciente); la pantalla lo muestra al revés.
import { CATEGORIAS } from './entrenamientosService.js'
import { apiListaPaginada, apiRequest } from './apiClient.js'
import { usarApiDe } from './usarApi.js'
import { paginar, TAMANO_PAGINA } from '../utils/paginacion.js'
import {
  adaptarDeportista, comoFormulario, cuerpoDeportista, errorDeDeportistas, filtrarPorTexto, paginaDelServidor, parametrosLista,
} from '../utils/deportistasApi.js'
import { HOY_EJEMPLO, hoy } from '../utils/hoy.js'
import { GRUPOS_ENTRENAMIENTO, categoriaPorFecha, categoriaRequiereGrupo, grupoSegunCategoria } from '../utils/categorias.js'
import { hoyLocal } from '../utils/fechas.js'
import {
  MENSAJES, enLista, validarCampos, validarAnioNacimientoDeportista, validarDireccion, validarDocumento, validarFechaNacimiento, validarNombre, validarTelefono,
} from '../utils/validaciones.js'

export { HOY_EJEMPLO }

// Interruptor por módulo (igual que entrenadoresService y pagosService): con "deportistas" en VITE_API_MODULOS las funciones de
// abajo hablan con /api/deportistas; sin él (y siempre en las pruebas) siguen con los datos de ejemplo EN MEMORIA. No se mezclan:
// los ids reales son UUID (texto) y los de ejemplo son números. Ver "Modo API" al final de este archivo.
const usarApi = usarApiDe('deportistas')
export const deportistasPorApi = () => usarApi
// Los deportistas son menores. La duplicidad se compara solo por número.
// No incluye cédula de ciudadanía (no aplica a menores). Los extranjeros usan cédula de extranjería o pasaporte.
export const TIPO_DOCUMENTO_SUGERIDO = 'Tarjeta de identidad'
export const TIPOS_DOCUMENTO_DEPORTISTA = [TIPO_DOCUMENTO_SUGERIDO, 'Registro civil', 'Cédula de extranjería', 'Pasaporte']
export const ACUDIENTE_DEMO = 'acudiente@pradofc.co'

// PENDIENTE: las categorías salen de la lista de entrenamientosService (CATEGORIAS) para no duplicarla.
// La última es un dato interno solo para poder probar CATEGORIA_NO_VIGENTE: está `oculta`, así que listarCategorias no la devuelve
// y no aparece en ningún filtro ni selector (se puede pedir por id, pero la interfaz no la ofrece).
let categorias = [
  ...CATEGORIAS.map((nombre, i) => ({ id: i + 1, nombre, activa: true })),
  { id: CATEGORIAS.length + 1, nombre: 'Categoría no vigente (solo pruebas)', activa: false, oculta: true },
]

// Posiciones del jugador (lista provisional del club: cámbiala aquí y el formulario, la tabla y la validación la siguen)
export const POSICIONES = ['Portero', 'Defensor', 'Mediocampista', 'Delantero']
export const TEXTO_SIN_POSICION = 'Sin definir'

const TI = 'Tarjeta de identidad'
// Teléfono y dirección ficticios: 3000000001, 3000000002... y calles inventadas
// La categoría de cada deportista de ejemplo se calcula con la regla (utils/categorias.js) y el "hoy" de la demo
const categoriaDeEjemplo = (fechaNacimiento) => categorias.find((c) => c.nombre === categoriaPorFecha(fechaNacimiento, hoy())).id
// Grupo de entrenamiento (solo Cachorros y Semillero; mezcla de mañana y tarde) y posición de cada deportista de ejemplo, por id.
// El grupo no se guarda en la base de datos: es un dato solo del frontend de ejemplo.
const GRUPO_Y_POSICION = {
  1: ['mañana', 'Delantero'], 2: ['tarde', 'Mediocampista'], 3: ['tarde', 'Portero'], 4: [null, null], 5: ['mañana', 'Defensor'],
  6: ['mañana', 'Portero'], 7: ['tarde', 'Delantero'], 8: [null, null], 9: [null, 'Mediocampista'], 10: ['tarde', 'Defensor'],
  11: [null, 'Defensor'], 12: [null, 'Mediocampista'], 13: [null, 'Delantero'], 14: [null, 'Portero'], 15: [null, 'Mediocampista'],
  16: [null, null], 17: [null, 'Defensor'], 18: [null, 'Delantero'], 19: [null, null], 20: [null, 'Mediocampista'], 21: [null, 'Portero'],
}
const d = (id, nombre, apellido, documento, fechaNacimiento, activo = true, acudiente = null) => ({
  id, nombre, apellido, tipoDocumento: TI, documento, fechaNacimiento,
  telefono: `300000${String(id).padStart(4, '0')}`,
  direccion: `Calle ${10 + id} # ${20 + id}-${10 + id}, Barrio Ejemplo`,
  idCategoria: categoriaDeEjemplo(fechaNacimiento), activo, acudiente,
  grupoEntrenamiento: GRUPO_Y_POSICION[id][0], posicion: GRUPO_Y_POSICION[id][1],
})

// Cada deportista está en la categoría que da su año de nacimiento (utils/categorias.js, con el "hoy" 2026-10-22).
let deportistas = [
  d(1, 'Santiago', 'Ríos Demo', '1100000001', '2018-03-14', true, ACUDIENTE_DEMO),
  d(2, 'Valeria', 'Ríos Demo', '1100000002', '2017-11-02', true, ACUDIENTE_DEMO),
  { ...d(3, 'Emilio', 'Pardo Ejemplo', '1100000003', '2018-07-21'), creadoPor: 'administrador@pradofc.co', creadoEn: '2026-09-14T15:20:00.000Z' },
  d(4, 'Isabella', 'Quintero Ejemplo', '1100000004', '2021-01-30'),
  d(5, 'Thiago', 'Marín Ejemplo', '1100000005', '2017-10-25', false),
  d(6, 'Mateo', 'Vélez Demo', '1100000006', '2016-05-09'),
  d(7, 'Juan Pablo', 'Cano Demo', '1100000007', '2016-09-17'),
  d(8, 'Samuel', 'Londoño Demo', '1100000008', '2022-02-12'),
  d(9, 'Sofía', 'Henao Ejemplo', '1100000009', '2015-12-04'),
  d(10, 'Dylan', 'Restrepo Ejemplo', '1100000010', '2016-06-28', false),
  d(11, 'Emmanuel', 'Arias Demo', '1100000011', '2014-08-19'),
  d(12, 'Tomás', 'Giraldo Demo', '1100000012', '2014-04-03'),
  d(13, 'Nicolás', 'Mejía Demo', '1100000013', '2014-10-11'),
  d(14, 'Mariana', 'Zapata Ejemplo', '1100000014', '2014-01-26'),
  d(15, 'Juan Sebastián Alejandro', 'Hernández de la Cruz Ejemplo', '1100000015', '2014-12-08'),
  d(16, 'Luciana', 'Osorio Ejemplo', '1100000016', '2013-11-30', false),
  d(17, 'Andrés', 'Cardona Ejemplo', '1100000017', '2010-06-15'),
  d(18, 'Camila', 'Betancur Ejemplo', '1100000018', '2011-09-22'),
  d(19, 'Daniel', 'Cifuentes Ejemplo', '1100000019', '2012-03-07'),
  d(20, 'Gabriela', 'Montoya Ejemplo', '1100000020', '2011-12-19'),
  d(21, 'Esteban', 'Ocampo Ejemplo', '1100000021', '2010-05-01'),
]
let siguienteId = deportistas.length + 1

// Ids de los deportistas de ejemplo de una categoría (por nombre), tal como se siembran. Sirve a otros datos de ejemplo (por ejemplo,
// el registro de asistencia sembrado) para no depender de números fijos. No refleja cambios hechos después.
const SEMBRADOS = deportistas.map(({ id, idCategoria }) => ({ id, idCategoria }))
export const idsDeEjemploDeCategoria = (nombre) => {
  const categoria = categorias.find((c) => c.nombre === nombre)
  return categoria ? SEMBRADOS.filter((x) => x.idCategoria === categoria.id).map((x) => x.id) : []
}

// Historial de ediciones por deportista (id -> lista, de la más antigua a la más reciente). Se siembra uno en Emilio (3)
// para probar la ficha: cambió el teléfono y pasó de Semillero a Cachorros.
const historiales = new Map([
  [3, [
    {
      fechaHora: '2026-09-20T16:05:00.000Z',
      responsable: 'administrador@pradofc.co',
      cambios: [
        { campo: 'telefono', antes: '3000000999', despues: '3000000003' },
        { campo: 'idCategoria', antes: 3, despues: 2 },
      ],
    },
  ]],
])

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

// Copia profunda (los deportistas de la inscripción en línea traen objetos anidados: contactoAcudiente y consentimiento)
const copiar = (x) => structuredClone(x)
// Sin mayúsculas ni tildes: "ríos" == "RIOS"
const normalizar = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()

// Edad en años cumplidos a la fecha `hoy` (por defecto el "hoy" de la demo). Se calcula sobre el texto AAAA-MM-DD.
export function calcularEdad(fechaNacimiento, fechaHoy = hoy()) {
  const [a, m, dia] = fechaNacimiento.split('-').map(Number)
  const [ah, mh, dh] = fechaHoy.split('-').map(Number)
  return ah - a - (mh < m || (mh === m && dh < dia) ? 1 : 0)
}

// Edad para mostrar: la que calcula el backend (`edad`) o, con los datos de ejemplo, la que sale de la fecha de nacimiento
export const edadDe = (deportista, fechaHoy) => deportista.edad ?? calcularEdad(deportista.fechaNacimiento, fechaHoy)

export async function listarCategorias() {
  if (usarApi) return listarCategoriasApi()
  await simularRed()
  return categorias.filter((c) => !c.oculta).map(copiar)
}

// Valor especial de idCategoria para listar solo a los deportistas sin grupo (idCategoria null).
export const SIN_CATEGORIA = 'sin-asignar'

// idCategoria: solo los de esa categoría (lanza CATEGORIA_NO_EXISTE si no existe) o SIN_CATEGORIA. texto: nombre o documento.
// estado: 'todos' (por defecto, como siempre: activos e inactivos) | 'activos' | 'inactivos'.
export async function listarDeportistas({ idCategoria, texto, estado = 'todos' } = {}) {
  if (usarApi) return listarDeportistasApi({ idCategoria, texto, estado })
  await simularRed()
  let lista = deportistas
  if (estado === 'activos') lista = lista.filter((x) => x.activo)
  else if (estado === 'inactivos') lista = lista.filter((x) => !x.activo)
  if (idCategoria === SIN_CATEGORIA) {
    lista = lista.filter((x) => x.idCategoria === null)
  } else if (idCategoria !== undefined && idCategoria !== null && idCategoria !== '') {
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
  if (usarApi) return obtenerDeportistaApi(id)
  await simularRed()
  const encontrado = deportistas.find((x) => x.id === Number(id))
  if (!encontrado) throw error('DEPORTISTA_NO_EXISTE', 'El deportista no existe.')
  return copiar(encontrado)
}

// La categoría debe existir y estar vigente (activa). Devuelve la categoría.
function categoriaVigente(idCategoria) {
  const categoria = categorias.find((c) => c.id === Number(idCategoria))
  if (!categoria) throw error('CATEGORIA_NO_EXISTE', 'La categoría no existe.')
  if (!categoria.activa) throw error('CATEGORIA_NO_VIGENTE', 'La categoría no está vigente.')
  return categoria
}

// La categoría debe ser la que corresponde al año de nacimiento (se comprueba después de categoriaVigente)
function exigirCategoriaDeLaFecha(categoria, fechaNacimiento) {
  if (categoria.nombre !== categoriaPorFecha(fechaNacimiento)) {
    throw error('CATEGORIA_NO_CORRESPONDE_EDAD', 'La categoría no corresponde al año de nacimiento del deportista.')
  }
}

// Sin pantalla en esta tarea: queda para reutilizar (por ejemplo al inscribir o cambiar de grupo).
export async function asignarCategoria(idDeportista, idCategoria) {
  if (usarApi) throw errorNoDisponible('La categoría la calcula el backend con el año de nacimiento.')
  await simularRed()
  const actual = deportistas.find((x) => x.id === Number(idDeportista))
  if (!actual) throw error('DEPORTISTA_NO_EXISTE', 'El deportista no existe.')
  const categoria = categoriaVigente(idCategoria)
  exigirCategoriaDeLaFecha(categoria, actual.fechaNacimiento)
  const actualizado = { ...actual, idCategoria: categoria.id }
  deportistas = deportistas.map((x) => (x.id === actual.id ? actualizado : x))
  return copiar(actualizado)
}

// Aplica el cambio de un campo a los valores del formulario. Al completar la fecha de nacimiento con el tipo de documento
// vacío, se sugiere la tarjeta de identidad (el usuario puede cambiarla).
export function aplicarCambio(valores, campo, valor) {
  const nuevos = { ...valores, [campo]: valor }
  if (campo === 'fechaNacimiento' && valor && !valores.tipoDocumento) nuevos.tipoDocumento = TIPO_DOCUMENTO_SUGERIDO
  // Si la fecha ya no da Cachorros ni Semillero, el grupo de entrenamiento se limpia
  if (campo === 'fechaNacimiento') nuevos.grupoEntrenamiento = grupoSegunCategoria(valores.grupoEntrenamiento, categoriaPorFecha(valor))
  return nuevos
}

// Formulario en blanco (idCategoria '' = sin asignar)
export const DEPORTISTA_VACIO = {
  nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', direccion: '', idCategoria: '',
  grupoEntrenamiento: '', posicion: '',
}

// Reglas de los datos personales (HU-008): las usan el formulario y crearDeportista, con los validadores compartidos.
// La fecha de nacimiento se valida contra `hoy` (por defecto hoyLocal(), igual que el max del input de fecha). Devuelve { campo: mensaje } solo con los campos con error.
// El grupo de entrenamiento es obligatorio solo si la categoría que da la fecha es Cachorros o Semillero; `grupo: false` lo omite
// (la inscripción en línea no lo pide). La posición es opcional, pero si viene debe ser una de POSICIONES.
export const MENSAJES_GRUPO = { obligatorio: 'Selecciona el grupo de entrenamiento: mañana o tarde.' }
export const MENSAJE_POSICION = 'Selecciona una posición de la lista.'
export function validarDatosDeportista(datos, hoy = hoyLocal(), { grupo = true } = {}) {
  const errores = validarCampos(datos, {
    nombre: [(v) => validarNombre(v, { obligatorio: MENSAJES.nombresObligatorio, longitud: MENSAJES.nombresLongitud, caracteres: MENSAJES.nombresCaracteres })],
    apellido: [(v) => validarNombre(v, { obligatorio: MENSAJES.apellidosObligatorio, longitud: MENSAJES.apellidosLongitud, caracteres: MENSAJES.apellidosCaracteres })],
    tipoDocumento: [enLista(TIPOS_DOCUMENTO_DEPORTISTA, MENSAJES.tipoDocumento)],
    documento: [validarDocumento],
    fechaNacimiento: [(v) => validarFechaNacimiento(v, hoy), (v) => validarAnioNacimientoDeportista(v, hoy)],
    telefono: [validarTelefono],
    direccion: [validarDireccion],
  })
  if (grupo && !errores.fechaNacimiento && categoriaRequiereGrupo(categoriaPorFecha(String(datos.fechaNacimiento).trim(), hoy)) && !GRUPOS_ENTRENAMIENTO.includes(datos.grupoEntrenamiento)) {
    errores.grupoEntrenamiento = MENSAJES_GRUPO.obligatorio
  }
  if (grupo && datos.posicion && !POSICIONES.includes(datos.posicion)) errores.posicion = MENSAJE_POSICION
  return errores
}

// Mismo número de documento, ignorando espacios y puntos en ambos lados (por si un dato llega de otra fuente)
const soloDocumento = (x) => String(x ?? '').replace(/[\s.]/g, '')
export const mismoDocumento = (a, b) => soloDocumento(a) !== '' && soloDocumento(a) === soloDocumento(b)

// Registra un deportista nuevo (activo, sin acudiente). Solo el administrador. La categoría es opcional ('' o null = sin asignar).
// Orden de las comprobaciones: permiso, datos, documento duplicado y categoría vigente; si algo falla no se crea nada.
export async function crearDeportista(datos, actor) {
  if (usarApi) return crearDeportistaApi(datos)
  await simularRed()
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para registrar deportistas.')
  const errores = validarDatosDeportista(datos)
  if (Object.keys(errores).length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })
  if (deportistas.some((x) => mismoDocumento(x.documento, datos.documento))) {
    throw error('DOCUMENTO_DUPLICADO', 'Ya existe un deportista registrado con este documento.')
  }
  const sinCategoria = datos.idCategoria === undefined || datos.idCategoria === null || datos.idCategoria === ''
  const categoria = sinCategoria ? null : categoriaVigente(datos.idCategoria)
  if (categoria) exigirCategoriaDeLaFecha(categoria, datos.fechaNacimiento.trim())

  const nuevo = {
    id: siguienteId++,
    nombre: datos.nombre.trim(),
    apellido: datos.apellido.trim(),
    tipoDocumento: datos.tipoDocumento,
    documento: datos.documento.trim(),
    fechaNacimiento: datos.fechaNacimiento.trim(),
    telefono: datos.telefono.trim(),
    direccion: datos.direccion.trim(),
    idCategoria: categoria ? categoria.id : null,
    grupoEntrenamiento: grupoGuardado(datos),
    posicion: datos.posicion || null,
    activo: true,
    acudiente: null,
    creadoPor: actor.correo ?? null,
    creadoEn: new Date().toISOString(),
  }
  deportistas = [...deportistas, nuevo]
  return copiar(nuevo)
}

// Grupo tal como se guarda: el elegido si la categoría que da la fecha lo exige, y null en las demás (se limpia)
const grupoGuardado = ({ fechaNacimiento, grupoEntrenamiento }) => grupoSegunCategoria(grupoEntrenamiento, categoriaPorFecha(String(fechaNacimiento).trim())) || null

export const ORIGEN_INSCRIPCION_EN_LINEA = 'Inscripción en línea'

// Deja el correo del acudiente en el deportista (`acudiente`), que es lo que usan el estado de cuenta y los documentos del acudiente.
// El vínculo completo (con el parentesco) está en acudientesService. Solo el administrador.
export async function vincularAcudiente(idDeportista, correo, actor) {
  if (usarApi) throw errorNoDisponible('El backend todavía no vincula acudientes.')
  await simularRed()
  exigirAdministrador(actor)
  const actual = buscarDeportista(idDeportista)
  const actualizado = { ...actual, acudiente: String(correo).trim() }
  deportistas = deportistas.map((x) => (x.id === actual.id ? actualizado : x))
  return copiar(actualizado)
}

// ¿Este correo es el del acudiente del deportista? (síncrona: la usan otros servicios para comprobar permisos)
export const esAcudienteDe = (correo, idDeportista) => {
  const d = deportistas.find((x) => x.id === Number(idDeportista))
  return Boolean(d?.acudiente && correo && d.acudiente.toLowerCase() === String(correo).toLowerCase())
}

// Registro que llega de la inscripción en línea (HU-009, services/inscripcionService.js). No tiene actor (es público):
// quien llama ya validó los datos. Nace SIN categoría, activo, sin acudiente vinculado y con origen "Inscripción en línea".
// Si el documento ya existe NO crea nada, y por la misma razón NO devuelve nada: quien llama no puede saber si hubo registro
// nuevo o no (no se revela qué documentos están registrados). Tarda lo mismo en los dos casos.
// El correo del acudiente NO se vincula con ninguna cuenta (PENDIENTE: lo hace el administrador o el backend).
export async function crearDeportistaDesdeInscripcion({ deportista, contactoAcudiente, consentimiento }) {
  await simularRed()
  if (deportistas.some((x) => mismoDocumento(x.documento, deportista.documento))) return
  deportistas = [
    ...deportistas,
    {
      id: siguienteId++,
      nombre: deportista.nombre.trim(),
      apellido: deportista.apellido.trim(),
      tipoDocumento: deportista.tipoDocumento,
      documento: deportista.documento.trim(),
      fechaNacimiento: deportista.fechaNacimiento.trim(),
      telefono: deportista.telefono.trim(),
      direccion: deportista.direccion.trim(),
      idCategoria: null,
      grupoEntrenamiento: null,
      posicion: null,
      activo: true,
      acudiente: null,
      origen: ORIGEN_INSCRIPCION_EN_LINEA,
      contactoAcudiente: { ...contactoAcudiente },
      consentimiento: { ...consentimiento },
      creadoPor: null,
      creadoEn: new Date().toISOString(),
    },
  ]
}

// ---- Ficha del deportista (HU-010): solo el administrador ----
export const CAMPOS_EDITABLES = ['nombre', 'apellido', 'tipoDocumento', 'documento', 'fechaNacimiento', 'telefono', 'direccion', 'idCategoria', 'grupoEntrenamiento', 'posicion']

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para ver ni editar fichas de deportistas.')
}

function buscarDeportista(id) {
  const encontrado = deportistas.find((x) => x.id === Number(id))
  if (!encontrado) throw error('DEPORTISTA_NO_EXISTE', 'El deportista no existe.')
  return encontrado
}

const copiaHistorial = (id) => structuredClone(historiales.get(id) ?? [])

// Ficha completa con su historial de cambios. Lanza SIN_PERMISO (si no es administrador) o DEPORTISTA_NO_EXISTE.
export async function obtenerFichaDeportista(id, actor) {
  if (usarApi) return obtenerFichaApi(id)
  await simularRed()
  exigirAdministrador(actor)
  const deportista = buscarDeportista(id)
  return { deportista: copiar(deportista), historial: copiaHistorial(deportista.id) }
}

export async function obtenerHistorial(id, actor) {
  if (usarApi) return [] // el backend todavía no guarda el historial de ediciones
  await simularRed()
  exigirAdministrador(actor)
  return copiaHistorial(buscarDeportista(id).id)
}

// Valor tal como se guarda: texto sin espacios en los bordes; la categoría, un número o null ('' y null son lo mismo)
function valorLimpio(campo, valor) {
  if (campo === 'idCategoria') return valor === undefined || valor === null || valor === '' ? null : Number(valor)
  if (campo === 'grupoEntrenamiento' || campo === 'posicion') return String(valor ?? '').trim() || null
  return String(valor ?? '').trim()
}

// Edita solo los campos del formulario (CAMPOS_EDITABLES); cualquier otro (id, acudiente, creadoPor, creadoEn, activo) se ignora.
// `cambios` puede traer solo algunos campos. Orden de las comprobaciones: permiso, existencia, validación, documento duplicado
// (en OTRO deportista), categoría vigente (solo si cambia) y, al final, SIN_CAMBIOS. Si algo falla no se guarda nada ni se
// agrega historial. Devuelve { deportista, historial }.
export async function actualizarDeportista(id, cambios, actor) {
  if (usarApi) return actualizarDeportistaApi(id, cambios)
  await simularRed()
  exigirAdministrador(actor)
  const actual = buscarDeportista(id)

  const propuesto = Object.fromEntries(CAMPOS_EDITABLES.map((campo) => [campo, campo in (cambios ?? {}) ? cambios[campo] : actual[campo]]))
  const errores = validarDatosDeportista(propuesto)
  if (Object.keys(errores).length > 0) throw Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })
  if (deportistas.some((x) => x.id !== actual.id && mismoDocumento(x.documento, propuesto.documento))) {
    throw error('DOCUMENTO_DUPLICADO', 'Ya existe un deportista registrado con este documento.')
  }

  const nuevos = Object.fromEntries(CAMPOS_EDITABLES.map((campo) => [campo, valorLimpio(campo, propuesto[campo])]))
  nuevos.grupoEntrenamiento = grupoGuardado({ fechaNacimiento: nuevos.fechaNacimiento, grupoEntrenamiento: nuevos.grupoEntrenamiento })
  const detalle = CAMPOS_EDITABLES
    .filter((campo) => nuevos[campo] !== valorLimpio(campo, actual[campo]))
    .map((campo) => ({ campo, antes: valorLimpio(campo, actual[campo]), despues: nuevos[campo] }))
  // Con categoría asignada, si cambió ella o la fecha de nacimiento debe seguir siendo la de ese año (un registro viejo sin
  // cambios en ninguno de los dos se puede seguir editando)
  if (nuevos.idCategoria !== null && detalle.some((c) => c.campo === 'idCategoria' || c.campo === 'fechaNacimiento')) {
    exigirCategoriaDeLaFecha(categoriaVigente(nuevos.idCategoria), nuevos.fechaNacimiento)
  }
  if (detalle.length === 0) throw error('SIN_CAMBIOS', 'No hiciste ningún cambio.')

  const actualizado = { ...actual, ...nuevos }
  deportistas = deportistas.map((x) => (x.id === actual.id ? actualizado : x))
  historiales.set(actual.id, [...(historiales.get(actual.id) ?? []), { fechaHora: new Date().toISOString(), responsable: actor.correo ?? null, cambios: detalle }])
  return { deportista: copiar(actualizado), historial: copiaHistorial(actual.id) }
}

// ================================================================================================================
// Modo API (módulo "deportistas"): GET/POST/PUT /api/deportistas y fotos (ver backend/README.md, "Deportistas").
// - Los ids son UUID: nunca se convierten a número. Los errores conservan { estado, mensaje, campos } del backend y llevan
//   además el `codigo` que ya entienden las pantallas (VALIDACION con `errores`, DOCUMENTO_DUPLICADO, SIN_PERMISO, DEPORTISTA_NO_EXISTE).
// - La categoría y el estado de la inscripción los calcula el backend al leer. La categoría ya no se elige: no se envía.
// - Permisos: la lista la reciben ADMINISTRADOR y ENTRENADOR (el entrenador, sin documento, teléfono, dirección ni estado de la
//   inscripción: esos campos simplemente no vienen); lo demás es solo del ADMINISTRADOR. Aquí NO se simulan: los valida el backend.
// - La lista del backend no tiene búsqueda por texto ni «sin categoría»: solo filtra por estado (activo) y categoría. Con texto o
//   «sin asignar» se recorren todas las páginas (de 100 en 100) y se filtra aquí.
// - PENDIENTE: el backend no guarda historial de ediciones, ni acudientes, ni documentos del deportista.
// ================================================================================================================
const TAMANO_LOTE = 100
const MAX_LOTES = 50 // tope de seguridad: 5.000 deportistas

const errorNoDisponible = (mensaje) => Object.assign(new Error(mensaje ?? 'Esta función necesita el módulo de deportistas sin conectar a la API.'), { codigo: 'NO_DISPONIBLE' })
const rutaDeportista = (id) => `/deportistas/${encodeURIComponent(id)}`

async function peticion(operacion, opciones) {
  try {
    return await operacion()
  } catch (error) {
    throw errorDeDeportistas(error, opciones)
  }
}

async function listarCategoriasApi() {
  const lista = await peticion(() => apiRequest('/categorias'), { recurso: 'categoria' })
  return lista.map(({ id, nombre }) => ({ id, nombre, activa: true }))
}

// Todas las páginas que cumplen los filtros del backend (estado y categoría por nombre)
async function recorrerTodas({ estado, categoria }) {
  const todos = []
  for (let pagina = 0; pagina < MAX_LOTES; pagina += 1) {
    const resultado = await peticion(() => apiListaPaginada('/deportistas', parametrosLista({ pagina, tamano: TAMANO_LOTE, estado, categoria })))
    todos.push(...resultado.items.map(adaptarDeportista))
    if ((pagina + 1) * resultado.tamano >= resultado.total || resultado.items.length === 0) break
  }
  return todos
}

// idCategoria (UUID) -> nombre de la categoría, que es lo que filtra el backend
async function nombreDeCategoria(idCategoria) {
  const categoria = (await listarCategoriasApi()).find((c) => String(c.id) === String(idCategoria))
  if (!categoria) throw Object.assign(new Error('La categoría no existe.'), { codigo: 'CATEGORIA_NO_EXISTE' })
  return categoria.nombre
}

async function listarDeportistasApi({ idCategoria, texto, estado = 'todos' } = {}) {
  const sinCategoria = idCategoria === SIN_CATEGORIA
  const hayCategoria = idCategoria !== undefined && idCategoria !== null && idCategoria !== '' && !sinCategoria
  const categoria = hayCategoria ? await nombreDeCategoria(idCategoria) : undefined
  let lista = await recorrerTodas({ estado, categoria })
  if (sinCategoria) lista = lista.filter((d) => d.idCategoria === null)
  return filtrarPorTexto(lista, texto)
}

async function obtenerDeportistaApi(id) {
  return adaptarDeportista(await peticion(() => apiRequest(rutaDeportista(id))))
}

async function obtenerFichaApi(id) {
  return { deportista: await obtenerDeportistaApi(id), historial: [] }
}

async function crearDeportistaApi(datos) {
  const creado = await peticion(() => apiRequest('/deportistas', { method: 'POST', body: JSON.stringify(cuerpoDeportista(datos)) }))
  return adaptarDeportista(creado)
}

// PUT reemplaza todos los campos editables y exige `activo`: se manda el actual (la ficha no cambia el estado). Si nada cambió
// no se llama al backend (SIN_CAMBIOS, como en el modo de ejemplo).
async function actualizarDeportistaApi(id, cambios) {
  const actual = await obtenerDeportistaApi(id)
  const propuesto = { ...comoFormulario(actual), ...Object.fromEntries(Object.entries(cambios ?? {}).filter(([campo]) => CAMPOS_EDITABLES.includes(campo))) }
  const cuerpo = cuerpoDeportista(propuesto, { activo: actual.activo })
  if (JSON.stringify(cuerpo) === JSON.stringify(cuerpoDeportista(comoFormulario(actual), { activo: actual.activo }))) {
    throw Object.assign(new Error('No hiciste ningún cambio.'), { codigo: 'SIN_CAMBIOS' })
  }
  const guardado = await peticion(() => apiRequest(rutaDeportista(id), { method: 'PUT', body: JSON.stringify(cuerpo) }))
  return { deportista: adaptarDeportista(guardado), historial: [] }
}

// Una página de la lista de la pantalla del administrador: { items, pagina, totalPaginas, desde, hasta, total } (`pagina` desde 1).
// Con la API y sin texto ni «sin asignar», la paginación y el filtro (estado, categoría) son del backend; en los demás casos, y en el
// modo de ejemplo, se filtra aquí y se pagina con paginar().
export async function listarPaginaDeportistas({ pagina = 1, tamano = TAMANO_PAGINA, texto, estado = 'todos', idCategoria } = {}) {
  const hayTexto = Boolean(String(texto ?? '').trim())
  if (!usarApi || hayTexto || idCategoria === SIN_CATEGORIA) {
    return paginar(await listarDeportistas({ idCategoria, texto, estado }), pagina, tamano)
  }
  const categoria = idCategoria ? await nombreDeCategoria(idCategoria) : undefined
  const pedir = (numero) => peticion(() => apiListaPaginada('/deportistas', parametrosLista({ pagina: numero - 1, tamano, estado, categoria })))
  let resultado = await pedir(pagina)
  const totalPaginas = Math.max(1, Math.ceil(resultado.total / tamano))
  let actual = pagina
  if (resultado.items.length === 0 && resultado.total > 0 && pagina > totalPaginas) {
    actual = totalPaginas // una página después de la última: se muestra la última
    resultado = await pedir(actual)
  }
  return paginaDelServidor({ items: resultado.items.map(adaptarDeportista), total: resultado.total, pagina: Math.max(1, actual), tamano })
}

// Totales de la cabecera: { total, activos }. Con la API son dos consultas de una fila (solo se lee totalElements).
export async function contarDeportistas() {
  if (!usarApi) {
    const todos = await listarDeportistas()
    return { total: todos.length, activos: todos.filter((d) => d.activo).length }
  }
  const [todos, activos] = await Promise.all(['todos', 'activos'].map((estado) =>
    peticion(() => apiListaPaginada('/deportistas', parametrosLista({ pagina: 0, tamano: 1, estado })))))
  return { total: todos.total, activos: activos.total }
}
