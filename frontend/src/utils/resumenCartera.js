// Totales de las pantallas de resumen (inicio del tesorero, dashboard del administrador, reportes y inicio del acudiente).
// Funciones PURAS sobre los cobros ya adaptados (utils/pagosApi.js: adaptarCobro / adaptarMisPagos). No tocan red, reloj ni DOM:
// la fecha de hoy ("AAAA-MM-DD") entra por parámetro (en la app, hoy() de utils/hoy.js).
//
// PENDIENTE (backend): hoy el frontend recorre GET /api/pagos y suma aquí. Lo ideal a futuro es un endpoint de resumen en el
// backend (por ejemplo GET /api/pagos/resumen) que devuelva estos totales ya calculados, sin traer todos los cobros.
//
// Reglas:
//   recaudo del mes        cobros APROBADOS cuya fechaPago cae en el mes de hoy (todos los tipos)
//   cartera vencida        cobros con estado 'vencido' (PENDIENTE con vencimiento anterior a hoy, según el backend)
//   por cobrar del mes     cobros 'pendiente' (PENDIENTE no vencido) cuyo vencimiento cae en el mes de hoy
//   últimos pagos          cobros APROBADOS, del fechaPago más reciente al más antiguo
//   recaudo por mes        suma de APROBADOS agrupada por el mes de fechaPago
// Los meses se comparan como texto "AAAA-MM" (sin Date), así la zona horaria del navegador no mueve ningún día.

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

export const MESES_DE_HISTORIAL = 6
export const ULTIMOS_PAGOS = 5

const suma = (lista) => lista.reduce((total, c) => total + c.monto, 0)
const mesDe = (fecha) => (fecha ? String(fecha).slice(0, 7) : null)
const aprobados = (cobros) => cobros.filter((c) => c.estado === 'pagado' && c.fechaPago)

// "2026-11" -> n meses antes ("2026-09" con n = 2). Cruza el cambio de año.
export function mesAnterior(mes, n = 1) {
  const [anio, numero] = mes.split('-').map(Number)
  const indice = anio * 12 + (numero - 1) - n
  return `${Math.floor(indice / 12)}-${String((indice % 12) + 1).padStart(2, '0')}`
}

// "2026-09" -> "sep. 2026"
export function etiquetaMes(mes) {
  const [anio, numero] = mes.split('-').map(Number)
  return `${MESES_CORTOS[numero - 1]}. ${anio}`
}

const recaudoDelMes = (cobros, mes) => {
  const lista = aprobados(cobros).filter((c) => mesDe(c.fechaPago) === mes)
  return { total: suma(lista), cantidad: lista.length }
}

// Recaudo de los últimos `cantidad` meses (el actual al final), con 0 en los meses sin pagos.
export function recaudoPorMes(cobros, fechaHoy, cantidad = MESES_DE_HISTORIAL) {
  const actual = mesDe(fechaHoy)
  const meses = Array.from({ length: cantidad }, (_, i) => mesAnterior(actual, cantidad - 1 - i))
  return meses.map((mes) => ({ mes, ...recaudoDelMes(cobros, mes) }))
}

// Pagos recibidos más recientes primero; a igual fecha, por id para que el orden sea estable.
export function ultimosPagosRecibidos(cobros, limite = ULTIMOS_PAGOS) {
  return [...aprobados(cobros)]
    .sort((a, b) => b.fechaPago.localeCompare(a.fechaPago) || String(b.id).localeCompare(String(a.id)))
    .slice(0, limite)
}

// Cartera vencida agrupada por deportista, de mayor a menor deuda.
export function carteraVencidaPorDeportista(cobros, limite = 5) {
  const porDeportista = new Map()
  for (const c of cobros.filter((x) => x.estado === 'vencido')) {
    const previo = porDeportista.get(c.idDeportista) ?? { idDeportista: c.idDeportista, nombre: c.nombreDeportista, total: 0, cantidad: 0 }
    previo.total += c.monto
    previo.cantidad += 1
    porDeportista.set(c.idDeportista, previo)
  }
  return [...porDeportista.values()].sort((a, b) => b.total - a.total || a.nombre.localeCompare(b.nombre)).slice(0, limite)
}

export function resumenCartera(cobros, fechaHoy) {
  const mes = mesDe(fechaHoy)
  const previo = mesAnterior(mes)
  const recaudo = recaudoDelMes(cobros, mes)
  const recaudoPrevio = recaudoDelMes(cobros, previo)
  const vencidos = cobros.filter((c) => c.estado === 'vencido')
  const porCobrar = cobros.filter((c) => c.estado === 'pendiente' && mesDe(c.fechaVencimiento) === mes)
  return {
    mes,
    hayCobros: cobros.length > 0,
    recaudoMes: recaudo,
    // Dato de referencia, sin porcentaje: comparar un mes en curso con uno completo engaña. Null si el mes anterior no tuvo pagos.
    recaudoMesAnterior: recaudoPrevio.cantidad === 0 ? null : { mes: previo, total: recaudoPrevio.total },
    carteraVencida: { total: suma(vencidos), cantidad: vencidos.length },
    porCobrarMes: { total: suma(porCobrar), cantidad: porCobrar.length },
    ultimosPagos: ultimosPagosRecibidos(cobros),
    recaudoPorMes: recaudoPorMes(cobros, fechaHoy),
    vencidosPorDeportista: carteraVencidaPorDeportista(cobros),
  }
}

// Acudiente: a partir del estado de cuenta (adaptarMisPagos). Próximos pagos = sin pagar, el vencimiento más cercano primero
// (los ya vencidos quedan arriba). Últimos pagados = fechaPago más reciente primero. Cada fila lleva el nombre del deportista.
export function resumenAcudiente(estadoCuenta, limite = ULTIMOS_PAGOS) {
  const todos = (estadoCuenta?.deportistas ?? []).flatMap(({ deportista, pagos }) => pagos.map((p) => ({ ...p, nombreDeportista: deportista.nombre })))
  const proximos = todos
    .filter((p) => p.estado === 'pendiente' || p.estado === 'vencido')
    .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento) || String(a.id).localeCompare(String(b.id)))
  const pagados = todos
    .filter((p) => p.estado === 'pagado' && p.fechaPago)
    .sort((a, b) => b.fechaPago.localeCompare(a.fechaPago) || String(b.id).localeCompare(String(a.id)))
    .slice(0, limite)
  return { hayPagos: todos.length > 0, proximos, ultimosPagados: pagados, totalPendiente: estadoCuenta?.totalPendiente ?? 0, totalVencido: estadoCuenta?.totalVencido ?? 0 }
}

// CSV de los cobros cargados (para exportar en Reportes). Separador ';' (Excel en español), montos enteros, celdas neutralizadas
// contra fórmulas. El BOM se agrega al descargar (utils/descargas.js).
const ETIQUETA_ESTADO = { pagado: 'Pagado', pendiente: 'Pendiente', vencido: 'Vencido', rechazado: 'Rechazado' }
const neutralizar = (texto) => (/^[=+\-@\t\r]/.test(texto) ? `'${texto}` : texto)
const celda = (valor) => {
  const t = neutralizar(String(valor ?? ''))
  return /[";\r\n]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t
}
export function generarCsvCobros(cobros) {
  const columnas = ['Deportista', 'Tipo', 'Concepto', 'Monto', 'Vencimiento', 'Fecha de pago', 'Estado']
  const filas = cobros.map((c) => [c.nombreDeportista, c.tipo, c.concepto, c.monto, c.fechaVencimiento, c.fechaPago ?? '', ETIQUETA_ESTADO[c.estado] ?? c.estado])
  return [columnas, ...filas].map((fila) => fila.map(celda).join(';')).join('\r\n') + '\r\n'
}
