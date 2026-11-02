import Button from '../ui/Button.jsx'
import Icon from '../Icon.jsx'

// Lo que puede hacer el acudiente con un cobro, según su estado (siempre con texto, no solo color):
//   pendiente o vencido  -> "Pagar en línea" (abre el checkout de Wompi)
//   rechazado            -> "Reintentar" (el backend crea un cobro pendiente nuevo)
//   pagado               -> "Descargar comprobante" (si el cobro tiene comprobante)
// ocupado: id del cobro con una acción en curso (deshabilita sus botones); los demás siguen disponibles.
export default function AccionPago({ pago, ocupado = null, onPagar, onReintentar, onDescargar }) {
  const enCurso = ocupado === pago.id
  const nombre = `${pago.concepto}`
  if (pago.estado === 'pendiente' || pago.estado === 'vencido') {
    return (
      <Button onClick={() => onPagar(pago)} disabled={enCurso} aria-busy={enCurso} aria-label={`Pagar en línea: ${nombre}`} className="gap-2 disabled:opacity-60">
        <Icon name="wallet" className="h-5 w-5 shrink-0" />
        {enCurso ? 'Abriendo el pago…' : 'Pagar en línea'}
      </Button>
    )
  }
  if (pago.estado === 'rechazado') {
    return (
      <Button variant="secondary" onClick={() => onReintentar(pago)} disabled={enCurso} aria-busy={enCurso} aria-label={`Reintentar el pago: ${nombre}`} className="disabled:opacity-60">
        {enCurso ? 'Preparando…' : 'Reintentar'}
      </Button>
    )
  }
  if (pago.estado === 'pagado' && pago.tieneComprobante) {
    return (
      <Button variant="secondary" onClick={() => onDescargar(pago)} disabled={enCurso} aria-busy={enCurso} aria-label={`Descargar comprobante: ${nombre}`} className="gap-2 disabled:opacity-60">
        <Icon name="download" className="h-5 w-5 shrink-0" />
        {enCurso ? 'Descargando…' : 'Descargar comprobante'}
      </Button>
    )
  }
  return null
}
