import Avatar from '../ui/Avatar.jsx'
import Card from '../ui/Card.jsx'
import Field from '../ui/Field.jsx'
import Icon from '../Icon.jsx'
import { ESTADOS_ASISTENCIA, ORDEN_ESTADOS } from './estados.js'
import { MAX_OBSERVACION } from '../../services/asistenciaService.js'

// Un deportista de la lista editable: fieldset con legend (nombre) y tres opciones tipo radio
// (Presente / Ausente / Justificado, con texto + ícono) y una observación opcional de hasta 200 caracteres.
// El avatar va encima del nombre cuando no hay ancho (flex-wrap en rem), para no partir nombres largos.
export default function FilaAsistencia({ deportista, nombre, foto, valor, error, obsAbierta, deshabilitado, onEstado, onObservacion, onAlternarObs }) {
  const { id } = deportista
  const errorId = error ? `asistencia-${id}-error` : undefined
  const obsId = `observacion-${id}`
  const panelId = `${obsId}-panel`
  const contadorId = `${obsId}-contador`
  const botonObs = obsAbierta ? 'Ocultar observación' : valor.observacion ? 'Editar observación' : 'Agregar observación'

  return (
    <Card as="li" className={error ? 'border-danger' : ''}>
      <div className="flex flex-wrap items-start gap-3">
        <Avatar name={nombre} foto={foto} />
        <div className="flex min-w-0 grow basis-[14rem] flex-col gap-3">
          <fieldset aria-describedby={errorId} disabled={deshabilitado} className="flex min-w-0 flex-col gap-2">
            <legend className="mb-1 break-words text-body-medium">{nombre}</legend>
            <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,7.5rem),1fr))] gap-2">
              {ORDEN_ESTADOS.map((estado) => {
                const { texto, icono, clases } = ESTADOS_ASISTENCIA[estado]
                const marcado = valor.estado === estado
                return (
                  <label
                    key={estado}
                    className={`flex min-h-11 cursor-pointer items-center justify-center gap-1.5 rounded-md border px-2 text-center text-body-medium focus-within:ring-2 focus-within:ring-focus focus-within:ring-offset-2 ${
                      marcado ? `${clases} font-semibold` : error ? 'border-danger bg-surface text-primary' : 'bg-surface text-primary hover:bg-app'
                    }`}
                  >
                    <input
                      id={`radio-${id}-${estado}`}
                      type="radio"
                      name={`asistencia-${id}`}
                      value={estado}
                      checked={marcado}
                      onChange={() => onEstado(id, estado)}
                      aria-describedby={errorId}
                      className="sr-only"
                    />
                    {marcado && <Icon name={icono} className="h-4 w-4 shrink-0" />}
                    <span className="min-w-0 break-words">{texto}</span>
                  </label>
                )
              })}
            </div>
            {error && (
              <p id={errorId} className="flex items-start gap-1 text-small text-danger">
                <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
                <span className="min-w-0 break-words">{error}</span>
              </p>
            )}
          </fieldset>
          <button
            type="button"
            aria-expanded={obsAbierta}
            aria-controls={panelId}
            onClick={() => onAlternarObs(id)}
            disabled={deshabilitado}
            className="flex min-h-11 max-w-full items-center self-start rounded-md px-2 text-left text-body-medium underline [overflow-wrap:anywhere] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          >
            {botonObs}
          </button>
          <div id={panelId} className={obsAbierta ? 'flex flex-col gap-1' : 'hidden'}>
            {obsAbierta && (
              <>
                <Field
                  label={`Observación de ${nombre} (opcional)`}
                  id={obsId}
                  as="textarea"
                  rows={3}
                  maxLength={MAX_OBSERVACION}
                  value={valor.observacion}
                  disabled={deshabilitado}
                  describedBy={contadorId}
                  onChange={(e) => onObservacion(id, e.target.value)}
                  className="min-h-11 w-full rounded-md border bg-surface px-3 py-2 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                />
                <p id={contadorId} className="text-small text-secondary">
                  {valor.observacion.length} de {MAX_OBSERVACION} caracteres
                </p>
              </>
            )}
          </div>
        </div>
      </div>
    </Card>
  )
}
