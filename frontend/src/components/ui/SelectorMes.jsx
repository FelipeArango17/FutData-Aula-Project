import Icon from '../Icon.jsx'
import { OPCIONES_MES, opcionesAnio, partesDePeriodo, periodoTrasCambio } from '../../utils/periodo.js'
import { hoy } from '../../utils/hoy.js'

// Selector doble Mes + Año. Recibe y emite "AAAA-MM" (o '' sin periodo), igual que un <input type="month">.
// label: nombre del campo (leyenda del grupo). describedBy: id de una nota que lo describe. disabled desactiva ambas listas.
const clase = 'min-h-11 w-full rounded-md border bg-surface px-3 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

export default function SelectorMes({ id, label, value = '', onChange, disabled = false, error, describedBy, requerido }) {
  const { mes, anio } = partesDePeriodo(value)
  const errorId = error ? `${id}-error` : undefined
  const cambiar = (campo) => (e) => onChange(periodoTrasCambio(value, campo, e.target.value, hoy()))
  const anioActual = Number(hoy().slice(0, 4))
  return (
    <fieldset
      id={id}
      disabled={disabled}
      aria-describedby={[errorId, describedBy].filter(Boolean).join(' ') || undefined}
      aria-invalid={error ? true : undefined}
      className="flex min-w-0 flex-col gap-1"
    >
      <legend className="text-body-medium">
        {label}
        {requerido && <span aria-hidden="true" className="text-danger"> *</span>}
      </legend>
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label htmlFor={`${id}-mes`} className="sr-only">Mes</label>
          <select id={`${id}-mes`} value={mes} onChange={cambiar('mes')} aria-required={requerido ? true : undefined} className={`${clase} ${error ? 'border-danger' : ''}`}>
            <option value="">Mes</option>
            {OPCIONES_MES.map((o) => <option key={o.valor} value={o.valor}>{o.texto}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-anio`} className="sr-only">Año</label>
          <select id={`${id}-anio`} value={anio} onChange={cambiar('anio')} aria-required={requerido ? true : undefined} className={`${clase} ${error ? 'border-danger' : ''}`}>
            <option value="">Año</option>
            {opcionesAnio(value, anioActual).map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </div>
      </div>
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-small text-danger">
          <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="min-w-0 break-words">{error}</span>
        </p>
      )}
    </fieldset>
  )
}
