import { MESES } from './fechas.js'

// Periodo "AAAA-MM" <-> par de listas (mes, año). Sin valor o con un valor incompleto, el periodo es '' (sin filtro).
const PERIODO = /^(\d{4})-(0[1-9]|1[0-2])$/

export const OPCIONES_MES = MESES.map((nombre, i) => ({ valor: String(i + 1).padStart(2, '0'), texto: nombre.charAt(0).toUpperCase() + nombre.slice(1) }))

export function partesDePeriodo(periodo) {
  const m = PERIODO.exec(String(periodo ?? ''))
  return m ? { anio: m[1], mes: m[2] } : { anio: '', mes: '' }
}

// Del año anterior al siguiente al actual; si el valor actual cae fuera de ese rango, también se ofrece (no se pierde el dato).
export function opcionesAnio(periodo, anioActual) {
  const anios = [anioActual - 1, anioActual, anioActual + 1]
  const { anio } = partesDePeriodo(periodo)
  if (anio && !anios.includes(Number(anio))) anios.push(Number(anio))
  return anios.sort((a, b) => a - b).map(String)
}

// Cambia el mes o el año. Si se elige uno y falta el otro, se completa con el mes/año de hoy; si se vacía alguno, el periodo queda vacío.
export function periodoTrasCambio(periodo, campo, nuevo, hoy) {
  if (!nuevo) return ''
  const actual = partesDePeriodo(periodo)
  const anio = campo === 'anio' ? nuevo : (actual.anio || hoy.slice(0, 4))
  const mes = campo === 'mes' ? nuevo : (actual.mes || hoy.slice(5, 7))
  return `${anio}-${mes}`
}
