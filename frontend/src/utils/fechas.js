// Utilidades de fecha y hora para entrenamientos. Las fechas son texto AAAA-MM-DD y las horas HH:MM (24 h).
// Se calcula en UTC para que la zona horaria del navegador no mueva el día.
const DIAS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

const aDate = (fecha) => new Date(`${fecha}T00:00:00Z`)
const aTexto = (date) => date.toISOString().slice(0, 10)

export const esFechaValida = (fecha) => /^\d{4}-\d{2}-\d{2}$/.test(fecha) && aTexto(aDate(fecha)) === fecha
export const esHoraValida = (hora) => /^([01]\d|2[0-3]):[0-5]\d$/.test(hora)

export function sumarDias(fecha, dias) {
  const date = aDate(fecha)
  date.setUTCDate(date.getUTCDate() + dias)
  return aTexto(date)
}

// Lunes de la semana de `fecha`
export function lunesDe(fecha) {
  const dia = aDate(fecha).getUTCDay() // 0 = domingo
  return sumarDias(fecha, dia === 0 ? -6 : 1 - dia)
}

// Lunes a sábado de la semana que empieza en `lunes`
export const diasDeSemana = (lunes) => [0, 1, 2, 3, 4, 5].map((n) => sumarDias(lunes, n))

export const esDomingo = (fecha) => aDate(fecha).getUTCDay() === 0

const partes = (fecha) => {
  const date = aDate(fecha)
  return { dia: DIAS[date.getUTCDay()], numero: date.getUTCDate(), mes: MESES[date.getUTCMonth()] }
}

// "Lunes 19 de octubre"
export function diaLargo(fecha) {
  const { dia, numero, mes } = partes(fecha)
  return `${dia} ${numero} de ${mes}`
}

// "Lun 19"
export function diaCorto(fecha) {
  const { dia, numero } = partes(fecha)
  return `${dia.slice(0, 3)} ${numero}`
}

// "19 al 24 de octubre" (o "26 de octubre al 31 de octubre" si cruza de mes)
export function rangoSemana(lunes) {
  const inicio = partes(lunes)
  const fin = partes(sumarDias(lunes, 5))
  if (inicio.mes === fin.mes) return `${inicio.numero} al ${fin.numero} de ${fin.mes}`
  return `${inicio.numero} de ${inicio.mes} al ${fin.numero} de ${fin.mes}`
}

// "17:30" -> "5:30 p. m."
export function formatHora(hora) {
  const [h, m] = hora.split(':').map(Number)
  return `${h % 12 || 12}:${String(m).padStart(2, '0')} ${h >= 12 ? 'p. m.' : 'a. m.'}`
}

export function sumarMinutos(hora, minutos) {
  const [h, m] = hora.split(':').map(Number)
  const total = h * 60 + m + minutos
  if (total >= 24 * 60) return null
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

// "jueves 22 de octubre de 2026"
export const fechaLargaConAnio = (fecha) => `${diaLargo(fecha).toLowerCase()} de ${fecha.slice(0, 4)}`

// Fecha y hora LOCALES de un instante ISO (por ejemplo new Date().toISOString()): { fecha: "AAAA-MM-DD", hora: "HH:MM" }
export function partesMomento(iso) {
  const d = new Date(iso)
  const dos = (n) => String(n).padStart(2, '0')
  return { fecha: `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`, hora: `${dos(d.getHours())}:${dos(d.getMinutes())}` }
}

// "Jueves 22 de octubre de 2026, 4:05 p. m."
export function momentoLegible(iso) {
  const { fecha, hora } = partesMomento(iso)
  const f = fechaLargaConAnio(fecha)
  return `${f[0].toUpperCase()}${f.slice(1)}, ${formatHora(hora)}`
}
