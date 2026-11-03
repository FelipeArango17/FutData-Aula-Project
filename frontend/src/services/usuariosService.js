// Servicio de usuarios y roles (HU-004, RF008, CU-06). Por ahora trabaja sobre datos de ejemplo EN MEMORIA.
// Las funciones son async (con retardo simulado) y lanzan errores con `codigo`, para cambiarlas luego por
// Axios (GET/POST /usuarios, PUT /usuarios/:id/rol, POST /usuarios/:id/revocar y /restablecer) sin tocar las pantallas.
//
// Forma de datos propuesta para el frontend:
//   Usuario = { id, nombre, apellido, correo, rol, activo, creadoPor, creadoEn }
//     rol       'administrador' | 'tesorero' | 'entrenador' | 'acudiente'
//     activo    boolean: true = acceso vigente; false = acceso revocado (la cuenta NO se borra)
//     creadoPor correo de quien la creó (null en los de ejemplo); creadoEn: fecha y hora ISO
//   Historial = [{ fechaHora (ISO), responsable (correo), cambios: [{ campo: 'rol' | 'activo', antes, despues }] }]
//     más reciente primero; antes/despues ya son texto legible ("Tesorero", "Activo", "Acceso revocado").
//
// Errores (error.codigo): 'SIN_PERMISO' | 'USUARIO_NO_EXISTE' | 'VALIDACION' (con `errores`: { campo: mensaje }) |
//   'CORREO_DUPLICADO' | 'AUTOACCESO' | 'ULTIMO_ADMINISTRADOR' | 'SIN_CAMBIOS' | 'ERROR_DE_RED'.
// Orden de las comprobaciones en cada operación: permiso, existencia, validación, reglas de negocio, SIN_CAMBIOS.
//
// PENDIENTE (decisiones con Juan y servicios reales):
// - Nombre del rol: la historia dice "padre"; el sistema ya usa 'acudiente' (en pantalla "Acudiente"). Confirmar con Juan.
// - Crear un usuario NO envía invitación ni maneja contraseñas. En real: la cuenta se crea en Supabase Auth y el correo de
//   invitación (donde la persona crea su contraseña) lo envía un flujo de n8n llamado por el backend. La pantalla no dice
//   que se envió un correo.
// - El mockup (CU-06) trae estados "Invitado" y "Sin activar" y un botón "Reenviar": dependen de esa invitación y no se hicieron.
// - Un usuario con rol 'entrenador' NO está ligado todavía al registro de entrenadores (entrenadoresService): el vínculo
//   natural es el correo, pero hoy son dos listas independientes.
// - Revocar solo evita iniciar sesión DESPUÉS: no cierra sesiones ya abiertas (en real: revocar la sesión en Supabase Auth).
// - Permisos: aquí solo el administrador opera esto; en producción lo valida el backend con el JWT (el actor simula el token).
// - Autoacceso: se compara el correo del actor con el del usuario (sin importar mayúsculas).
// - Solo nombre, correo, rol y estado: sin teléfono ni documentos.
//
// Modo API (VITE_API_MODULOS con "usuarios"): ver el final del archivo. Habla con /api/usuarios; los ids son UUID. La API no ofrece
// historial de cambios, "creadoPor", estados Invitado/Sin activar ni "Reenviar": en modo API no se muestran. Las reglas de
// autoacceso y del último administrador las decide el backend (409) y se muestra su mensaje. Sin el interruptor (y siempre en las
// pruebas) todo sigue con los datos de ejemplo, con ids numéricos: los dos modos no se mezclan.
// PENDIENTE: un usuario 'entrenador' sigue sin ligarse al registro de entrenadores (módulo entrenadores).
import { roleLabels } from '../config/menus.js'
import { adaptarUsuario, cuerpoRol, cuerpoUsuario, errorDeUsuarios } from '../utils/usuariosApi.js'
import { apiRequest } from './apiClient.js'
import { usarApiDe } from './usarApi.js'
import { MENSAJES, enLista, validarCampos, validarCorreo, validarNombre } from '../utils/validaciones.js'

const usarApi = usarApiDe('usuarios')
export const usuariosPorApi = () => usarApi

export const ROLES = ['administrador', 'tesorero', 'entrenador', 'acudiente']
export const ETIQUETAS_ROL = roleLabels
// Qué puede hacer cada rol (una línea; se muestra al elegir el rol)
export const DESCRIPCION_ROL = {
  administrador: 'Gestiona deportistas, usuarios, pagos, reportes y respaldo.',
  tesorero: 'Registra pagos y consulta reportes y avisos de cobro.',
  entrenador: 'Toma asistencia y consulta sus grupos, horarios y torneos.',
  acudiente: 'Consulta la información, los pagos y los documentos de sus deportistas.',
}
export const ETIQUETA_ACTIVO = 'Activo'
export const ETIQUETA_REVOCADO = 'Acceso revocado'
export const ESTADOS_FILTRO = ['todos', 'activos', 'revocados']

// Datos de ejemplo ficticios (las 4 cuentas demo de authService + otras). id 6 está revocado (inactivo@pradofc.co).
const u = (id, nombre, apellido, correo, rol, activo = true) => ({ id, nombre, apellido, correo, rol, activo, creadoPor: null, creadoEn: '2026-08-01T14:00:00.000Z' })
let usuarios = [
  u(1, 'Carlos', 'Mendoza', 'administrador@pradofc.co', 'administrador'),
  u(2, 'Laura', 'Gómez', 'tesorero@pradofc.co', 'tesorero'),
  u(3, 'Nicolás', 'Ejemplo', 'entrenador@pradofc.co', 'entrenador'),
  u(4, 'María', 'Torres', 'acudiente@pradofc.co', 'acudiente'),
  u(5, 'David', 'Mesa', 'david.mesa@pradofc.co', 'entrenador'),
  u(6, 'Pedro', 'Londoño', 'inactivo@pradofc.co', 'acudiente', false),
  u(7, 'Sofía', 'Henao', 'sofia.henao@pradofc.co', 'acudiente'),
  u(8, 'Diana', 'Restrepo', 'diana.restrepo@pradofc.co', 'administrador'),
]
let siguienteId = usuarios.length + 1

// Historial por usuario (id -> lista de la más antigua a la más reciente; se entrega al revés). Se siembra el de Pedro (6).
const historiales = new Map([
  [6, [{ fechaHora: '2026-09-30T15:10:00.000Z', responsable: 'administrador@pradofc.co', cambios: [{ campo: 'activo', antes: ETIQUETA_ACTIVO, despues: ETIQUETA_REVOCADO }] }]],
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

const copiar = (x) => structuredClone(x)
const normalizar = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
const mismoCorreo = (a, b) => normalizar(a) === normalizar(b)

function exigirAdministrador(actor) {
  if (actor?.rol !== 'administrador') throw error('SIN_PERMISO', 'No tienes permiso para gestionar usuarios y roles.')
}

function buscar(id) {
  const encontrado = usuarios.find((x) => x.id === Number(id))
  if (!encontrado) throw error('USUARIO_NO_EXISTE', 'El usuario no existe.')
  return encontrado
}

const copiaHistorial = (id) => structuredClone(historiales.get(id) ?? []).reverse()

function registrar(id, actor, cambios) {
  historiales.set(id, [...(historiales.get(id) ?? []), { fechaHora: new Date().toISOString(), responsable: actor.correo ?? null, cambios }])
}

const respuesta = (usuario) => ({ usuario: copiar(usuario), historial: copiaHistorial(usuario.id) })

// Para authService: true / false si el correo es de un usuario de esta lista (acceso vigente o revocado); null si no existe.
// PENDIENTE: en real, Supabase Auth decide si una cuenta revocada puede entrar.
export function accesoVigente(correo) {
  const encontrado = usuarios.find((x) => mismoCorreo(x.correo, correo))
  return encontrado ? encontrado.activo : null
}

export const USUARIO_VACIO = { nombre: '', apellido: '', correo: '', rol: '' }

// Reglas de los datos de un usuario (las usa el formulario y el servicio) con los validadores compartidos.
export function validarDatosUsuario(datos) {
  return validarCampos(datos, {
    nombre: [(v) => validarNombre(v, { obligatorio: MENSAJES.nombresObligatorio, longitud: MENSAJES.nombresLongitud, caracteres: MENSAJES.nombresCaracteres })],
    apellido: [(v) => validarNombre(v, { obligatorio: MENSAJES.apellidosObligatorio, longitud: MENSAJES.apellidosLongitud, caracteres: MENSAJES.apellidosCaracteres })],
    correo: [validarCorreo],
    rol: [enLista(ROLES, MENSAJES.usuarioRol)],
  })
}

const fallaValidacion = (errores) => Object.assign(error('VALIDACION', 'Hay datos que corregir.'), { errores })

// texto: nombre, apellido o correo. rol: un rol o ''/'todos'. estado: 'todos' | 'activos' | 'revocados'.
export async function listarUsuarios({ texto, rol, estado = 'todos' } = {}, actor) {
  if (usarApi) return api.listar({ texto, rol, estado })
  await simularRed()
  exigirAdministrador(actor)
  let lista = usuarios
  if (rol && rol !== 'todos') lista = lista.filter((x) => x.rol === rol)
  if (estado === 'activos') lista = lista.filter((x) => x.activo)
  else if (estado === 'revocados') lista = lista.filter((x) => !x.activo)
  const buscado = normalizar(texto)
  if (buscado) lista = lista.filter((x) => normalizar(`${x.nombre} ${x.apellido}`).includes(buscado) || normalizar(x.correo).includes(buscado))
  return lista.map(copiar)
}

// { usuario, historial } (en modo API el historial va siempre vacío)
export async function obtenerUsuario(id, actor) {
  if (usarApi) return api.obtener(id)
  await simularRed()
  exigirAdministrador(actor)
  return respuesta(buscar(id))
}

export async function obtenerHistorial(id, actor) {
  if (usarApi) return [] // la API no tiene historial
  await simularRed()
  exigirAdministrador(actor)
  return copiaHistorial(buscar(id).id)
}

// Crea un usuario con acceso vigente. NO envía invitación ni guarda contraseña (ver PENDIENTE arriba).
export async function crearUsuario(datos, actor) {
  if (usarApi) return api.crear(datos)
  await simularRed()
  exigirAdministrador(actor)
  const errores = validarDatosUsuario(datos)
  if (Object.keys(errores).length > 0) throw fallaValidacion(errores)
  if (usuarios.some((x) => mismoCorreo(x.correo, datos.correo))) throw error('CORREO_DUPLICADO', 'Ya existe un usuario con ese correo.')
  const nuevo = {
    id: siguienteId++,
    nombre: datos.nombre.trim(),
    apellido: datos.apellido.trim(),
    correo: datos.correo.trim(),
    rol: datos.rol,
    activo: true,
    creadoPor: actor.correo ?? null,
    creadoEn: new Date().toISOString(),
  }
  usuarios = [...usuarios, nuevo]
  return respuesta(nuevo)
}

// Quitarle el acceso o el rol de administrador a `objetivo`: no a uno mismo y nunca al último administrador activo.
function protegerAdministrador(objetivo, actor, mensajeAutoacceso) {
  if (mismoCorreo(objetivo.correo, actor.correo)) throw error('AUTOACCESO', mensajeAutoacceso)
  if (objetivo.rol === 'administrador' && objetivo.activo && usuarios.filter((x) => x.rol === 'administrador' && x.activo).length <= 1) {
    throw error('ULTIMO_ADMINISTRADOR', 'No se puede: el sistema debe tener al menos un administrador activo.')
  }
}

function guardarCambio(actual, cambios, actor, detalle) {
  const actualizado = { ...actual, ...cambios }
  usuarios = usuarios.map((x) => (x.id === actual.id ? actualizado : x))
  registrar(actual.id, actor, detalle)
  return respuesta(actualizado)
}

export async function cambiarRol(id, rol, actor) {
  if (usarApi) return api.cambiarRol(id, rol)
  await simularRed()
  exigirAdministrador(actor)
  const actual = buscar(id)
  const errores = validarCampos({ rol }, { rol: [enLista(ROLES, MENSAJES.usuarioRol)] })
  if (errores.rol) throw fallaValidacion(errores)
  if (rol === actual.rol) throw error('SIN_CAMBIOS', 'El usuario ya tiene ese rol.')
  if (actual.rol === 'administrador') protegerAdministrador(actual, actor, 'No puedes quitarte tu propio rol de administrador.')
  return guardarCambio(actual, { rol }, actor, [{ campo: 'rol', antes: ETIQUETAS_ROL[actual.rol], despues: ETIQUETAS_ROL[rol] }])
}

// No borra nada: solo pone activo = false y lo deja en el historial.
export async function revocarAcceso(id, actor) {
  if (usarApi) return api.cambiarAcceso(id, 'revocar')
  await simularRed()
  exigirAdministrador(actor)
  const actual = buscar(id)
  if (!actual.activo) throw error('SIN_CAMBIOS', 'El acceso de este usuario ya está revocado.')
  protegerAdministrador(actual, actor, 'No puedes revocar tu propio acceso.')
  return guardarCambio(actual, { activo: false }, actor, [{ campo: 'activo', antes: ETIQUETA_ACTIVO, despues: ETIQUETA_REVOCADO }])
}

export async function restablecerAcceso(id, actor) {
  if (usarApi) return api.cambiarAcceso(id, 'restablecer')
  await simularRed()
  exigirAdministrador(actor)
  const actual = buscar(id)
  if (actual.activo) throw error('SIN_CAMBIOS', 'Este usuario ya tiene acceso.')
  return guardarCambio(actual, { activo: true }, actor, [{ campo: 'activo', antes: ETIQUETA_REVOCADO, despues: ETIQUETA_ACTIVO }])
}

// ---- API real ----
// Los errores de la API llegan con `estado`, `mensaje` (en español) y `campos`; errorDeUsuarios los deja con `codigo`, `message` y,
// si traen campos, `errores` ({ campo: texto }), que es lo que ya muestran las pantallas. El permiso lo valida el backend con el JWT.
const rutaDe = (id) => `/usuarios/${encodeURIComponent(id)}`
const conHistorial = (dto) => ({ usuario: adaptarUsuario(dto), historial: [] })

async function llamar(ruta, opciones) {
  try {
    return await apiRequest(ruta, opciones)
  } catch (error) {
    throw errorDeUsuarios(error)
  }
}

const api = {
  async listar({ texto, rol, estado }) {
    let lista = (await llamar('/usuarios')).map(adaptarUsuario)
    if (rol && rol !== 'todos') lista = lista.filter((x) => x.rol === rol)
    if (estado === 'activos') lista = lista.filter((x) => x.activo)
    else if (estado === 'revocados') lista = lista.filter((x) => !x.activo)
    const buscado = normalizar(texto)
    if (buscado) lista = lista.filter((x) => normalizar(`${x.nombre} ${x.apellido}`).includes(buscado) || normalizar(x.correo).includes(buscado))
    return lista
  },
  async obtener(id) {
    return conHistorial(await llamar(rutaDe(id)))
  },
  // Solo resuelve con la respuesta 201 de la API: ahí es cuando el backend ya invitó a la persona por correo.
  async crear(datos) {
    const errores = validarDatosUsuario(datos)
    if (Object.keys(errores).length > 0) throw fallaValidacion(errores)
    return conHistorial(await llamar('/usuarios', { method: 'POST', body: JSON.stringify(cuerpoUsuario(datos)) }))
  },
  async cambiarRol(id, rol) {
    return conHistorial(await llamar(`${rutaDe(id)}/rol`, { method: 'PUT', body: JSON.stringify(cuerpoRol(rol)) }))
  },
  async cambiarAcceso(id, accion) {
    return conHistorial(await llamar(`${rutaDe(id)}/${accion}`, { method: 'POST' }))
  },
}
