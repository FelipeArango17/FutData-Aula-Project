// Formato de dinero en pesos colombianos (COP), enteros y sin decimales: 90000 -> "$ 90.000".
// Una sola utilidad para toda la app (estado de cuenta hoy; pagos del tesorero después).
const formato = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

export function formatearCOP(monto) {
  if (typeof monto !== 'number' || !Number.isFinite(monto)) throw new TypeError('El monto debe ser un número.')
  return formato.format(Math.round(monto))
}
