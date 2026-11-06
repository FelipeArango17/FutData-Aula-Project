// Traducción entre el contrato del horario de la semana del backend (HorarioSemanaController, bloque 4B-2) y las sesiones que usan las
// pantallas. Funciones puras, sin red.
//
//   GET    /api/horario-semana?fecha=AAAA-MM-DD -> { desde (lunes), hasta (sábado), sesiones: [SesionSemana] }
//   POST   /api/horario-semana/cancelaciones     { fecha, idFranja, motivo? }                -> 201 { sesion, avisoEnviado }
//   DELETE /api/horario-semana/cancelaciones/{idExcepcion}                                   -> 200 { sesion, avisoEnviado }
//   POST   /api/horario-semana/extras            { idCategoria, fecha, horaInicio, horaFin, sede, idEntrenador?, grupo?, motivo? } -> 201
//   DELETE /api/horario-semana/extras/{idExcepcion}                                          -> 200 { sesion: null, avisoEnviado }
//   No existe un endpoint para editar un extra (se elimina y se crea otro).
//
//   SesionSemana = { tipo 'FRANJA'|'EXTRA', estado 'ACTIVO'|'CANCELADO', idFranja|null, idExcepcion|null, fecha, dia (1..6), horaInicio,
//                    horaFin, idCategoria, categoria, sede, idEntrenador|null, entrenador|null, entrenadorActivo|null, grupo|null,
//                    motivo|null, puedeGestionar }
//   avisoEnviado: true = salió el correo; false = la operación quedó hecha pero no se pudo avisar; null = sin acudientes con correo.
//
// La sesión de pantalla es la de utils/semana.js (estado 'PROGRAMADO' | 'CANCELADO', `hora` en vez de horaInicio) más `puedeGestionar`,
// que el backend ya calcula (administrador: todo; entrenador: sus categorías; los demás, nunca; fechas pasadas, nunca).

// Qué acciones sobre las fechas ofrece el backend. Editar un extra no existe: queda oculto.
export const capacidadesApi = () => ({ cancelar: true, extras: true, editarExtra: false })

// El backend acepta cualquier día de la semana: se manda el lunes.
export const consultaSemana = (lunes) => `/horario-semana?fecha=${encodeURIComponent(lunes)}`

export function adaptarSesion(dto) {
  const esExtra = dto.tipo === 'EXTRA'
  return {
    clave: esExtra ? `x-${dto.idExcepcion}` : `f-${dto.idFranja}-${dto.fecha}`,
    tipo: esExtra ? 'EXTRA' : 'FRANJA',
    idFranja: dto.idFranja ?? null,
    idExcepcion: dto.idExcepcion ?? null,
    estado: dto.estado === 'CANCELADO' ? 'CANCELADO' : 'PROGRAMADO',
    motivo: dto.motivo ?? null,
    fecha: dto.fecha,
    hora: dto.horaInicio,
    horaFin: dto.horaFin,
    idCategoria: dto.idCategoria,
    categoria: dto.categoria ?? null,
    sede: dto.sede,
    idEntrenador: dto.idEntrenador ?? null,
    entrenador: dto.entrenador ?? null,
    entrenadorActivo: dto.idEntrenador ? dto.entrenadorActivo !== false : null,
    grupo: dto.grupo ?? null,
    puedeGestionar: dto.puedeGestionar === true,
  }
}

// La respuesta de GET /api/horario-semana -> lista de sesiones (el backend ya las ordena por fecha y hora)
export const adaptarSemana = (respuesta) => (respuesta?.sesiones ?? []).map(adaptarSesion)

// Respuesta de una escritura -> { sesion (o null), avisoEnviado (true | false | null) }
export const adaptarAccion = (respuesta) => ({
  sesion: respuesta?.sesion ? adaptarSesion(respuesta.sesion) : null,
  avisoEnviado: respuesta?.avisoEnviado ?? null,
})

const vacioANull = (valor) => (valor === '' || valor === null || valor === undefined ? null : valor)
const hora = (h) => String(h ?? '').trim().slice(0, 5)

export const cuerpoCancelacion = ({ fecha, idFranja, motivo }) => ({ fecha, idFranja, motivo: vacioANull(String(motivo ?? '').trim()) })

// datos: valores del formulario de extras (categoria en texto, hora, horaFin, idEntrenador '' = por turnos)
export const cuerpoExtra = (datos, idCategoria) => ({
  idCategoria,
  fecha: datos.fecha,
  horaInicio: hora(datos.hora),
  horaFin: hora(datos.horaFin),
  sede: datos.sede,
  idEntrenador: vacioANull(datos.idEntrenador),
  grupo: vacioANull(datos.grupo),
  motivo: vacioANull(String(datos.motivo ?? '').trim()),
})

// Campos del backend -> campos del formulario de extras
export const CAMPO_DE_FORMULARIO = { idCategoria: 'categoria', horaInicio: 'hora', horaFin: 'horaFin', fecha: 'fecha', sede: 'sede', idEntrenador: 'idEntrenador', grupo: 'grupo', motivo: 'motivo' }
