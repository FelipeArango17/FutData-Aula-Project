import Button from '../ui/Button.jsx'
import Icon from '../Icon.jsx'

// Cargando / error con "Reintentar" / vacío / contenido. Mismo estilo que TablaCobros. Solo dibuja lo que recibe.
//   cargando       muestra "texto" con role="status"
//   mensajeError   texto del error (role="alert") con el botón Reintentar si hay onReintentar
//   vacio          texto de estado vacío (por ejemplo "Aún no hay pagos registrados"); si es null se dibuja el contenido
export default function EstadoDeCarga({ cargando, mensajeError, onReintentar, vacio = null, texto = 'Cargando…', children }) {
  if (cargando) {
    return (
      <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <p role="status" className="min-w-0 break-words text-body">{texto}</p>
      </div>
    )
  }
  if (mensajeError) {
    return (
      <div role="alert" className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">
          <p>{mensajeError}</p>
          {onReintentar && <Button variant="secondary" onClick={onReintentar} className="sm:self-start">Reintentar</Button>}
        </div>
      </div>
    )
  }
  if (vacio) {
    return (
      <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="info" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <p className="min-w-0 break-words text-body">{vacio}</p>
      </div>
    )
  }
  return children
}
