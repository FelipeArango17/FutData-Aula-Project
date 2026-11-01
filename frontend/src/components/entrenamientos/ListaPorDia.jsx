import Card from '../ui/Card.jsx'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import { ENTRENADORES, HOY_EJEMPLO } from '../../services/entrenamientosService.js'
import { diaLargo, formatHora } from '../../utils/fechas.js'

// Lista agrupada por día (móvil). Solo se dibujan los días que tienen entrenamientos.
// Cada tarjeta muestra categoría (y grupo), hora, sede y entrenador.
export default function ListaPorDia({ dias, entrenamientos, base, onEliminar }) {
  return (
    <div className="flex flex-col gap-4">
      {dias.map((dia) => {
        const delDia = entrenamientos.filter((e) => e.fecha === dia).sort((a, b) => a.hora.localeCompare(b.hora))
        if (delDia.length === 0) return null
        return (
          <section key={dia} aria-labelledby={`dia-${dia}`} className="flex flex-col gap-2">
            <h2 id={`dia-${dia}`} className="text-label uppercase text-secondary">
              {diaLargo(dia)}{dia === HOY_EJEMPLO ? ' · Hoy' : ''}
            </h2>
            <ul className="flex flex-col gap-2">
              {delDia.map((e) => {
                const categoria = e.grupo ? `${e.categoria} · grupo ${e.grupo}` : e.categoria
                const descripcion = `${categoria}, ${diaLargo(dia).toLowerCase()}, ${formatHora(e.hora)}`
                const entrenador = ENTRENADORES.find((x) => x.id === e.idEntrenador)?.nombre ?? 'Por turnos'
                return (
                  <Card as="li" key={e.id} className="flex flex-col gap-3">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-h3">{formatHora(e.hora)} – {formatHora(e.horaFin)}</p>
                        <p className="break-words text-small text-secondary">{categoria} · {e.sede}</p>
                        <p className="break-words text-small text-secondary">Entrenador: {entrenador}</p>
                      </div>
                      <Badge>Entrenamiento</Badge>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button variant="secondary" to={`${base}/${e.id}/editar`} aria-label={`Editar entrenamiento ${descripcion}`} className="grow">
                        Editar
                      </Button>
                      <Button variant="danger" onClick={() => onEliminar(e)} aria-label={`Eliminar entrenamiento ${descripcion}`} className="grow">
                        Eliminar
                      </Button>
                    </div>
                  </Card>
                )
              })}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
