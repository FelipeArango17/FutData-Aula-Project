// Servicio de autenticación (HU-001 login y HU-002 verificación en dos pasos). Por ahora trabaja EN MEMORIA
// con cuentas de demostración. Las funciones son async (con un retardo simulado) y lanzan errores con `codigo`,
// para cambiarlas luego por llamadas reales sin tocar las pantallas.
//
// PENDIENTE (servicios reales que lo reemplazarán):
// - Credenciales: Supabase Auth (signInWithPassword). Supabase también gestiona el BLOQUEO por intentos
//   fallidos de contraseña; el frontend NO lo implementa.
// - Generar, guardar y validar el código de 6 dígitos (vence en 10 min, máximo 5 intentos, bloqueo de 15 min,
//   un reenvío por minuto): backend Spring. Aquí se simula para poder probar la pantalla.
// - Enviar el código por correo: flujo de n8n llamado por el backend.
// - "Recordar este equipo 30 días": en real es una cookie HttpOnly y Secure emitida por el backend; aquí solo se
//   guarda un indicador en memoria.
// - Nada de este archivo es seguro: el código y las reglas viven en el navegador solo para la demostración.
//
// CUENTAS DE DEMOSTRACIÓN (todas con la misma contraseña demo "Demo2026!"):
//   acudiente@pradofc.co, entrenador@pradofc.co, tesorero@pradofc.co, administrador@pradofc.co
//   inactivo@pradofc.co  -> contraseña correcta, pero la cuenta no está activa
//   red@pradofc.co       -> el primer intento simula una falla de red; el siguiente funciona
// Código de verificación de la demostración: CODIGO_DEMO (no se muestra en la pantalla).
//
// EQUIPO DE CONFIANZA (HU-003): si en el paso del código se marca "Recordar este equipo por 30 días", el correo queda en
// localStorage ("futdata.equipoConfiable": lista de { correo, expiraEn }). En el siguiente inicio de sesión de ese correo,
// con la contraseña correcta, no se pide el código. Para reiniciarlo en la demo: borrar esa clave de localStorage.
// Si localStorage no está disponible (modo privado, bloqueado) la lista vive solo en memoria hasta recargar la página.
// PENDIENTE (Juan): en real, el equipo de confianza es un token/cookie HttpOnly emitida por el backend y la revocación de
// sesiones en otros dispositivos (cerrar sesión global) invalida el refresh token en el servidor (JWT / Supabase Auth).
// Aquí todo es una simulación local: localStorage lo puede borrar o inventar cualquiera, y "cerrar en todos los equipos"
// solo borra los equipos de confianza de ESTE navegador.

import { accesoVigente } from './usuariosService.js'

export const RETARDO_MS = 400
export const VIGENCIA_EQUIPO_MS = 30 * 24 * 60 * 60 * 1000
export const VIGENCIA_CODIGO_MS = 10 * 60 * 1000
export const MAX_INTENTOS = 5
export const BLOQUEO_MS = 15 * 60 * 1000
export const REENVIO_MS = 60 * 1000

// PENDIENTE: en real el backend genera un código aleatorio por solicitud y lo envía por correo.
const CODIGO_DEMO = '123456'
const PASSWORD_DEMO = 'Demo2026!'

const CUENTAS = [
  { correo: 'acudiente@pradofc.co', rol: 'acudiente', nombre: 'María Torres', activa: true },
  { correo: 'entrenador@pradofc.co', rol: 'entrenador', nombre: 'Andrés Mora', activa: true },
  { correo: 'tesorero@pradofc.co', rol: 'tesorero', nombre: 'Laura Gómez', activa: true },
  { correo: 'administrador@pradofc.co', rol: 'administrador', nombre: 'Carlos Mendoza', activa: true },
  { correo: 'inactivo@pradofc.co', rol: 'acudiente', nombre: 'Pedro Londoño', activa: false },
  { correo: 'red@pradofc.co', rol: 'tesorero', nombre: 'Laura Gómez', activa: true },
]

const CLAVE = 'futdata.auth.servicio'
const CLAVE_EQUIPOS = 'futdata.equipoConfiable'
let falloDeRedPendiente = true

// Estado: pendiente = solicitud de código vigente; bloqueos = { correo: hasta (ms) }.
// Se guarda en sessionStorage para que el bloqueo siga al recargar la página.
function leer() {
  try {
    const guardado = JSON.parse(sessionStorage.getItem(CLAVE))
    if (guardado && typeof guardado === 'object') return { pendiente: null, bloqueos: {}, ...guardado }
  } catch {
    // sin almacenamiento o dato dañado: se empieza de cero
  }
  return { pendiente: null, bloqueos: {} }
}
let estado = leer()

function guardar() {
  try {
    sessionStorage.setItem(CLAVE, JSON.stringify(estado))
  } catch {
    // sin almacenamiento: el estado dura mientras la página siga abierta
  }
}

// Reloj del servicio: devuelve un Date. Las pruebas lo cambian con configurarSimulacion({ ahora: () => new Date(...) }).
let reloj = () => new Date()
export function configurarSimulacion({ ahora } = {}) {
  if (ahora !== undefined) reloj = ahora ?? (() => new Date())
}
const ahoraMs = () => reloj().getTime()

const esperar = () => new Promise((resolver) => setTimeout(resolver, RETARDO_MS))
const fallo = (codigo, extra = {}) => Object.assign(new Error(codigo), { codigo, ...extra })
const normalizar = (correo) => String(correo ?? '').trim().toLowerCase()

// ---- Equipos de confianza (ver el bloque EQUIPO DE CONFIANZA arriba) ----
let equiposMemoria = [] // respaldo si localStorage falla

const entradaValida = (e) => e && typeof e.correo === 'string' && typeof e.expiraEn === 'string' && Number.isFinite(Date.parse(e.expiraEn))
const vigente = (e, ahora) => Date.parse(e.expiraEn) > ahora

function leerEquipos() {
  try {
    const lista = JSON.parse(localStorage.getItem(CLAVE_EQUIPOS) ?? '[]')
    return Array.isArray(lista) ? lista.filter(entradaValida) : []
  } catch {
    return equiposMemoria
  }
}

function guardarEquipos(lista) {
  equiposMemoria = lista
  try {
    localStorage.setItem(CLAVE_EQUIPOS, JSON.stringify(lista))
  } catch {
    // sin almacenamiento: no se recuerda entre cargas de la página
  }
}

// Equipo de confianza vigente de ese correo. Si encuentra la entrada vencida la elimina (se pide el código como siempre).
function equipoDeConfianza(correo) {
  const clave = normalizar(correo)
  const lista = leerEquipos()
  const entrada = lista.find((e) => e.correo === clave)
  if (!entrada) return false
  if (vigente(entrada, ahoraMs())) return true
  guardarEquipos(lista.filter((e) => e !== entrada))
  return false
}

function recordarEquipo(correo) {
  const clave = normalizar(correo)
  const ahora = ahoraMs()
  const lista = leerEquipos().filter((e) => e.correo !== clave && vigente(e, ahora))
  guardarEquipos([...lista, { correo: clave, expiraEn: new Date(ahora + VIGENCIA_EQUIPO_MS).toISOString() }])
}

// Solo lectura: ¿este equipo es de confianza para ese correo? (no modifica nada)
export const equipoRecordado = (correo) => {
  const clave = normalizar(correo)
  return leerEquipos().some((e) => e.correo === clave && vigente(e, ahoraMs()))
}

// { expiraEn: ISO } si este equipo es de confianza para ese correo; null si no (o ya venció).
export function estadoEquipo(correo) {
  const clave = normalizar(correo)
  const entrada = leerEquipos().find((e) => e.correo === clave && vigente(e, ahoraMs()))
  return entrada ? { expiraEn: entrada.expiraEn } : null
}

// "Dejar de recordar este equipo": quita solo la entrada de este correo en este equipo. No cierra la sesión.
export function dejarDeRecordarEquipo(correo) {
  const clave = normalizar(correo)
  guardarEquipos(leerEquipos().filter((e) => e.correo !== clave))
}

// Versión de sesión simulada por correo (en memoria). En real el backend la sube y los tokens de otras sesiones dejan de valer.
const versionesSesion = {}
export const versionSesion = (correo) => versionesSesion[normalizar(correo)] ?? 0

// Cierre de sesión en todos los equipos: revoca los equipos de confianza DE ESE CORREO (los de otros correos no se tocan),
// sube la versión de sesión y descarta un código pendiente. Quien llama cierra además la sesión de este equipo.
export async function cerrarSesionGlobal(correo) {
  await esperar()
  const clave = normalizar(correo)
  guardarEquipos(leerEquipos().filter((e) => e.correo !== clave))
  versionesSesion[clave] = versionSesion(clave) + 1
  if (estado.pendiente?.correo === clave) estado.pendiente = null
  guardar()
  return { version: versionesSesion[clave] }
}

// "carlos@correo.com" -> "c***@correo.com"
export function enmascararCorreo(correo) {
  const [usuario = '', dominio = ''] = String(correo).split('@')
  return `${usuario.charAt(0)}***@${dominio}`
}

function bloqueoVigente(correo, ahora) {
  const hasta = estado.bloqueos[correo]
  return hasta && hasta > ahora ? hasta : null
}

// Paso 1: correo y contraseña. Si son válidos "envía" el código y deja la solicitud pendiente.
// Si este equipo es de confianza vigente para ese correo NO se pide el código: devuelve `verificada: true` y no deja solicitud.
// La contraseña siempre se pide, y el bloqueo y una cuenta inactiva o revocada se revisan ANTES (el equipo no los salta).
// Errores (error.codigo): RED, CREDENCIALES (mensaje genérico, no dice cuál falló), INACTIVA, BLOQUEADO.
export async function iniciarSesion(correo, contrasena) {
  await esperar()
  const clave = normalizar(correo)
  if (clave === 'red@pradofc.co' && falloDeRedPendiente) {
    falloDeRedPendiente = false
    throw fallo('RED')
  }
  const ahora = Date.now()
  const hasta = bloqueoVigente(clave, ahora)
  if (hasta) throw fallo('BLOQUEADO', { msRestantes: hasta - ahora })

  const cuenta = CUENTAS.find((c) => c.correo === clave)
  if (!cuenta || contrasena !== PASSWORD_DEMO) throw fallo('CREDENCIALES')
  // Si la cuenta también es un usuario de usuariosService, manda su acceso (revocado = INACTIVA, igual que inactivo@).
  // PENDIENTE: en real lo decide Supabase Auth; revocar tampoco cierra sesiones ya abiertas.
  if (!(accesoVigente(clave) ?? cuenta.activa)) throw fallo('INACTIVA')

  if (equipoDeConfianza(clave)) {
    estado.pendiente = null
    guardar()
    return { correo: clave, rol: cuenta.rol, nombre: cuenta.nombre, verificada: true }
  }

  // Si ya hay un código enviado hace menos de un minuto no se genera otro: así volver a iniciar sesión
  // no sirve para recuperar los intentos del código.
  const previo = estado.pendiente
  if (!previo || previo.correo !== clave || ahora - previo.ultimoEnvio >= REENVIO_MS) {
    estado.pendiente = {
      correo: clave,
      rol: cuenta.rol,
      nombre: cuenta.nombre,
      expira: ahora + VIGENCIA_CODIGO_MS,
      intentos: 0,
      ultimoEnvio: ahora,
    }
  }
  guardar()
  return { correo: clave, rol: cuenta.rol, nombre: cuenta.nombre }
}

// Datos de la solicitud pendiente para pintar la pantalla del código (o null si no hay).
export function estadoCodigo() {
  const p = estado.pendiente
  if (!p) return null
  return {
    correo: p.correo,
    correoEnmascarado: enmascararCorreo(p.correo),
    rol: p.rol,
    nombre: p.nombre,
    intentosRestantes: Math.max(0, MAX_INTENTOS - p.intentos),
    reenvioDisponibleEn: p.ultimoEnvio + REENVIO_MS,
    bloqueoHasta: bloqueoVigente(p.correo, Date.now()),
  }
}

// Paso 2: código de 6 dígitos.
// Errores (error.codigo): SIN_SOLICITUD, BLOQUEADO, FORMATO (no gasta intento), VENCIDO,
// INCORRECTO (trae intentosRestantes). El 5.º fallo bloquea el ingreso 15 minutos.
export async function verificarCodigo(codigo, { recordar = false } = {}) {
  await esperar()
  const p = estado.pendiente
  if (!p) throw fallo('SIN_SOLICITUD')

  const ahora = Date.now()
  const hasta = bloqueoVigente(p.correo, ahora)
  if (hasta) throw fallo('BLOQUEADO', { msRestantes: hasta - ahora })
  if (estado.bloqueos[p.correo]) {
    // el bloqueo ya terminó: se limpian el bloqueo y los intentos
    delete estado.bloqueos[p.correo]
    p.intentos = 0
  }

  if (!/^\d{6}$/.test(codigo)) throw fallo('FORMATO')
  if (ahora >= p.expira) throw fallo('VENCIDO')

  if (codigo !== CODIGO_DEMO) {
    p.intentos += 1
    if (p.intentos >= MAX_INTENTOS) {
      estado.bloqueos[p.correo] = ahora + BLOQUEO_MS
      guardar()
      throw fallo('BLOQUEADO', { msRestantes: BLOQUEO_MS })
    }
    guardar()
    throw fallo('INCORRECTO', { intentosRestantes: MAX_INTENTOS - p.intentos })
  }

  const { correo, rol, nombre } = p
  estado.pendiente = null
  if (recordar) recordarEquipo(correo)
  guardar()
  return { correo, rol, nombre }
}

// Reenvío del código: como máximo uno por minuto. Reinicia la vigencia y los intentos del código.
// Errores: SIN_SOLICITUD, BLOQUEADO, DEMASIADO_PRONTO (trae msRestantes).
export async function reenviarCodigo() {
  await esperar()
  const p = estado.pendiente
  if (!p) throw fallo('SIN_SOLICITUD')
  const ahora = Date.now()
  const hasta = bloqueoVigente(p.correo, ahora)
  if (hasta) throw fallo('BLOQUEADO', { msRestantes: hasta - ahora })
  const faltan = p.ultimoEnvio + REENVIO_MS - ahora
  if (faltan > 0) throw fallo('DEMASIADO_PRONTO', { msRestantes: faltan })

  delete estado.bloqueos[p.correo]
  p.expira = ahora + VIGENCIA_CODIGO_MS
  p.intentos = 0
  p.ultimoEnvio = ahora
  guardar()
  return { reenvioDisponibleEn: p.ultimoEnvio + REENVIO_MS }
}

// "Volver al inicio de sesión": descarta la solicitud pendiente (el bloqueo, si lo hay, se conserva).
export function cancelarSolicitud() {
  estado.pendiente = null
  guardar()
}
