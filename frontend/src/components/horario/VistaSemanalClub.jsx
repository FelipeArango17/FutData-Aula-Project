import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import { NOMBRES_DIAS } from '../../services/horarioClubData.js'
import { categoriaConGrupo, nombreEntrenadorDe, rangoHoras } from './formatoFranja.js'
import { formatHora } from '../../utils/fechas.js'
import { franjasPorDia } from '../../utils/horarioClub.js'

// Plantilla semanal del club, de lunes a sábado. Dos presentaciones con los mismos datos:
// - cuadricula (escritorio): días como columnas y una fila por cada hora de inicio;
// - lista (móvil, tablet vertical o letra grande): una sección por día con tarjetas, sin scroll horizontal.
// Cada bloque muestra categoría (con grupo si aplica), hora, sede y el nombre del entrenador ("Todos los entrenadores" si no
// hay uno fijo). onEditar(franja) / onEliminar(franja) son opcionales: sin ellas la vista es de solo lectura.
const descripcion = (franja) => `${categoriaConGrupo(franja)}, ${NOMBRES_DIAS[franja.dia].toLowerCase()}, ${rangoHoras(franja)}, ${franja.sede}, entrenador: ${nombreEntrenadorDe(franja)}`

function Cuadricula({ franjas, onEditar }) {
  const porDia = franjasPorDia(franjas)
  const horas = [...new Set(franjas.map((f) => f.horaInicio))].sort()
  return (
    <div className="overflow-hidden rounded-lg border bg-surface">
      <table className="w-full table-fixed border-collapse text-small">
        <caption className="sr-only">Horario semanal del club, por día y hora de inicio</caption>
        <thead>
          <tr>
            <th scope="col" className="w-20 border-b p-2 text-left font-semibold"><span className="sr-only">Hora de inicio</span></th>
            {Object.entries(NOMBRES_DIAS).map(([dia, nombre]) => (
              <th key={dia} scope="col" className="border-b border-l p-2 text-left font-semibold">{nombre}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {horas.map((hora) => (
            <tr key={hora}>
              <th scope="row" className="border-b p-2 text-left align-top font-normal text-secondary">{formatHora(hora)}</th>
              {Object.keys(NOMBRES_DIAS).map((dia) => (
                <td key={dia} className="border-b border-l p-1 align-top">
                  <div className="flex flex-col gap-1">
                    {porDia[dia].filter((f) => f.horaInicio === hora).map((f) => {
                      const contenido = (
                        <>
                          <span className="block break-words font-semibold">{categoriaConGrupo(f)}</span>
                          <span className="block break-words">{rangoHoras(f)}</span>
                          <span className="block break-words">{f.sede}</span>
                          <span className="block break-words">{nombreEntrenadorDe(f)}</span>
                        </>
                      )
                      const clases = 'block min-h-11 w-full rounded-md bg-brand-primary p-2 text-left text-on-dark hover:bg-brand-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2'
                      return onEditar ? (
                        <button key={f.id} type="button" onClick={() => onEditar(f)} aria-label={`Editar franja: ${descripcion(f)}`} className={clases}>{contenido}</button>
                      ) : (
                        <div key={f.id} role="group" aria-label={descripcion(f)} className={clases}>{contenido}</div>
                      )
                    })}
                  </div>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ListaPorDiaClub({ franjas, onEditar, onEliminar }) {
  const porDia = franjasPorDia(franjas)
  return (
    <div className="flex flex-col gap-4">
      {Object.entries(NOMBRES_DIAS).map(([dia, nombre]) => {
        if (porDia[dia].length === 0) return null
        return (
          <section key={dia} aria-labelledby={`club-dia-${dia}`} className="flex flex-col gap-2">
            <h2 id={`club-dia-${dia}`} className="text-label uppercase text-secondary">{nombre}</h2>
            <ul className="flex flex-col gap-2">
              {porDia[dia].map((f) => (
                <Card as="li" key={f.id} className="flex flex-col gap-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="break-words text-h3">{rangoHoras(f)}</p>
                      <p className="break-words text-body-medium">{categoriaConGrupo(f)}</p>
                      <p className="break-words text-small text-secondary">{f.sede}</p>
                      <p className="break-words text-small text-secondary">Entrenador: {nombreEntrenadorDe(f)}</p>
                    </div>
                    {f.grupo && <Badge>Grupo {f.grupo}</Badge>}
                  </div>
                  {(onEditar || onEliminar) && (
                    <div className="flex flex-wrap gap-2">
                      {onEditar && <Button variant="secondary" onClick={() => onEditar(f)} aria-label={`Editar franja: ${descripcion(f)}`} className="grow">Editar</Button>}
                      {onEliminar && <Button variant="danger" onClick={() => onEliminar(f)} aria-label={`Eliminar franja: ${descripcion(f)}`} className="grow">Eliminar</Button>}
                    </div>
                  )}
                </Card>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

export default function VistaSemanalClub({ franjas, modo = 'cuadricula', onEditar, onEliminar }) {
  return modo === 'cuadricula'
    ? <Cuadricula franjas={franjas} onEditar={onEditar} />
    : <ListaPorDiaClub franjas={franjas} onEditar={onEditar} onEliminar={onEliminar} />
}
