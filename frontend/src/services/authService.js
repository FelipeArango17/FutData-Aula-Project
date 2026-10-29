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

import { accesoVigente } from './usuariosService.js'

export const RETARDO_MS = 400
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
let falloDeRedPendiente = true

// Estado: pendiente = solicitud de código vigente; bloqueos = { correo: hasta (ms) }; recordados = correos.
// Se guarda en sessionStorage para que el bloqueo siga al recargar la página.
function leer() {
  try {
    const guardado = JSON.parse(sessionStorage.getItem(CLAVE))
    if (guardado && typeof guardado === 'object') return { pendiente: null, bloqueos: {}, recordados: [], ...guardado }
  } catch {
    // sin almacenamiento o dato dañado: se empieza de cero
  }
  return { pendiente: null, bloqueos: {}, recordados: [] }
}
let estado = leer()

function guardar() {
  try {
    sessionStorage.setItem(CLAVE, JSON.stringify(estado))
  } catch {
    // sin almacenamiento: el estado dura mientras la página siga abierta
  }
}

const esperar = () => new Promise((resolver) => setTimeout(resolver, RETARDO_MS))
const fallo = (codigo, extra = {}) => Object.assign(new Error(codigo), { codigo, ...extra })
const normalizar = (correo) => String(correo ?? '').trim().toLowerCase()

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
  if (recordar && !estado.recordados.includes(correo)) estado.recordados.push(correo)
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

// Solo lectura: ¿se pidió recordar este equipo? (por ahora no cambia el flujo)
export const equipoRecordado = (correo) => estado.recordados.includes(normalizar(correo))
