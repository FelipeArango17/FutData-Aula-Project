// Traducción entre el contrato de asistencia del backend y lo que usa la pantalla. Funciones puras, sin red.
//
//   GET /api/asistencia/sesion?fecha=AAAA-MM-DD&idFranja=UUID   (o &idExcepcion=UUID para un extra; exactamente uno)
//     -> { sesion: { fecha, horaInicio, horaFin, idCategoria, categoria, sede, grupo, idEntrenador, entrenador }, puedeRegistrar,
//          deportistas: [{ idDeportista, nombre, apellido, estado 'PRESENTE'|'AUSENTE'|'JUSTIFICADO'|null, observaciones }] }
//   PUT /api/asistencia/sesion  { fecha, idFranja | idExcepcion, registros: [{ idDeportista, estado, observaciones? (máx. 300) }] }
//     -> 200 con la forma del GET, ya guardado (upsert: se puede volver a guardar para corregir).
//   Errores: 400 (fecha futura, campos ["fecha"]; registros inválidos, campos ["registros"]), 403, 404, 409 (cancelada, campos ["fecha"];
//   o solape con otro entrenamiento, sin campos).
//
// En pantalla los estados van en minúscula ('presente' | 'ausente' | 'justificado', los de components/asistencia/estados.js) y la
// observación se llama `observacion`; el contrato usa mayúsculas y `observaciones`.

import { esDeEntrenador } from './horarioEntrenador.js'

export const MAX_OBSERVACION_API = 300

const ESTADO_DE_API = { PRESENTE: 'presente', AUSENTE: 'ausente', JUSTIFICADO: 'justificado' }
const ESTADO_A_API = { presente: 'PRESENTE', ausente: 'AUSENTE', justificado: 'JUSTIFICADO' }

// El identificador de la sesión en la semana: una franja o un extra (exactamente uno)
const identificador = (sesion) => (sesion.idFranja ? { idFranja: sesion.idFranja } : { idExcepcion: sesion.idExcepcion })

export function consultaSesion(sesion) {
  const [[campo, valor]] = Object.entries(identificador(sesion))
  return `/asistencia/sesion?fecha=${encodeURIComponent(sesion.fecha)}&${campo}=${encodeURIComponent(valor)}`
}

// Solo viajan los registros con estado; la observación vacía no se manda
export function cuerpoGuardar(sesion, registros) {
  return {
    fecha: sesion.fecha,
    ...identificador(sesion),
    registros: registros
      .filter((r) => ESTADO_A_API[r.estado])
      .map((r) => {
        const observaciones = String(r.observacion ?? '').trim()
        return { idDeportista: r.idDeportista, estado: ESTADO_A_API[r.estado], ...(observaciones ? { observaciones } : {}) }
      }),
  }
}

export function adaptarSesionAsistencia(respuesta) {
  const s = respuesta?.sesion ?? {}
  return {
    sesion: {
      fecha: s.fecha,
      hora: s.horaInicio,
      horaFin: s.horaFin,
      idCategoria: s.idCategoria ?? null,
      categoria: s.categoria ?? null,
      sede: s.sede ?? null,
      grupo: s.grupo ?? null,
      idEntrenador: s.idEntrenador ?? null,
      entrenador: s.entrenador ?? null,
    },
    puedeRegistrar: respuesta?.puedeRegistrar === true,
    deportistas: (respuesta?.deportistas ?? []).map((d) => ({
      id: d.idDeportista,
      nombre: d.nombre,
      apellido: d.apellido,
      estado: ESTADO_DE_API[d.estado] ?? '',
      observacion: d.observaciones ?? '',
    })),
  }
}

// Qué sesiones de la semana se muestran para pasar lista. No depende de `puedeGestionar` (es el del horario: en fechas pasadas viene en
// false). Administrador: todas. Entrenador: las de sus categorías o las franjas que dirige (con su perfil de /usuarios/yo; sin perfil,
// ninguna). El backend sigue siendo quien autoriza.
export function sesionesParaLista(sesiones, { rol, perfil = null }) {
  if (rol === 'administrador') return sesiones
  if (rol === 'entrenador') return sesiones.filter((s) => esDeEntrenador(s, perfil))
  return []
}

// ¿Esa sesión ofrece "Pasar lista"? Hoy o pasada (el backend solo prohíbe las futuras) y no cancelada, y es del usuario según su rol.
export const puedePasarLista = (sesion, hoy, contexto) =>
  sesion.fecha <= hoy && sesion.estado !== 'CANCELADO' && sesionesParaLista([sesion], contexto).length > 0

// Lo que el usuario marcó en esta visita -> registros a enviar. `marcas`: { [idDeportista]: { estado, observacion } }.
// Solo cuentan los que tienen estado (el backend lo exige).
export const registrosAEnviar = (marcas) =>
  Object.entries(marcas)
    .filter(([, m]) => ESTADO_A_API[m.estado])
    .map(([idDeportista, m]) => ({ idDeportista, estado: m.estado, observacion: m.observacion ?? '' }))

// Deportistas con observación escrita pero sin estado: no se pueden enviar (el backend exige el estado)
export const conObservacionSinEstado = (valores) => valores.filter((v) => String(v.observacion ?? '').trim() && !v.estado)

// Mensaje para el usuario según el fallo de la API. Nunca expone endpoints, ids ni nombres de campos.
//   accion: 'volver' (403/404: la sesión no está disponible), 'revisar' (400/409: se queda en la lista con lo marcado) o 'reintentar'.
export function describirFallo(fallo, { alGuardar = false } = {}) {
  const campos = Array.isArray(fallo?.campos) ? fallo.campos : []
  switch (fallo?.estado) {
    case 403:
      return { accion: 'volver', texto: 'No tienes permiso para pasar lista en esta sesión.' }
    case 404:
      return { accion: 'volver', texto: 'Esta sesión ya no existe. Revisa la semana.' }
    case 409:
      return { accion: 'revisar', texto: fallo.mensaje || 'No se pudo guardar la asistencia de esta sesión.' }
    case 400:
      if (campos.includes('registros')) {
        // El backend dice "registro N: ..." con N = posición (desde 1) dentro de los registros enviados; el texto no se muestra
        const posicion = Number(/registro (\d+):/.exec(String(fallo.mensaje ?? ''))?.[1]) || null
        return { accion: 'revisar', posicion, texto: 'Revisa la asistencia marcada: hay datos que no se pueden guardar.' }
      }
      if (campos.includes('fecha')) return { accion: 'revisar', texto: 'Esta sesión todavía no ocurre, no se puede pasar lista.' }
      return { accion: 'revisar', texto: 'Revisa la asistencia marcada e intenta de nuevo.' }
    default:
      return { accion: 'reintentar', texto: alGuardar ? 'No se pudo guardar la asistencia. Intenta de nuevo.' : 'No se pudo cargar la lista. Intenta de nuevo.' }
  }
}
