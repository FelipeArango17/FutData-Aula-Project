import { ESTADOS_ASISTENCIA } from './estados.js'

// Historial de ediciones, en orden cronológico (de la más antigua a la más reciente).
// momento(fechaHora) devuelve el texto de fecha y hora; nombres: { idDeportista: nombre completo }.
const verObs = (texto) => (texto ? `«${texto}»` : 'sin observación')

function textoCambio({ antes, despues }) {
  const partes = []
  if (antes.estado !== despues.estado) partes.push(`${ESTADOS_ASISTENCIA[antes.estado].texto} → ${ESTADOS_ASISTENCIA[despues.estado].texto}`)
  if (antes.observacion !== despues.observacion) partes.push(`Observación: ${verObs(antes.observacion)} → ${verObs(despues.observacion)}`)
  return partes.join(' · ')
}

export default function HistorialCambios({ historial, nombres, momento }) {
  return (
    <section aria-labelledby="titulo-historial" className="flex flex-col gap-3">
      <h2 id="titulo-historial" className="break-words text-h3">Historial de cambios</h2>
      {historial.length === 0 ? (
        <p className="text-body text-secondary">Todavía no se ha corregido esta asistencia.</p>
      ) : (
        <ol className="flex flex-col gap-3">
          {historial.map((entrada, i) => (
            <li key={`${entrada.fechaHora}-${i}`} className="flex flex-col gap-1 rounded-lg border bg-surface p-4">
              <p className="break-words text-body-medium">{momento(entrada.fechaHora)} · {entrada.responsable}</p>
              <ul className="flex flex-col gap-1">
                {entrada.cambios.map((c) => (
                  <li key={c.idDeportista} className="break-words text-body">
                    {nombres[c.idDeportista]}: {textoCambio(c)}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
