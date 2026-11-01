import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import Icon from '../../components/Icon.jsx'

const jugadores = ['Santiago · Sub-10', 'Valeria · Sub-9']
const dias = [
  { dia: 'Martes 20 de octubre', jugador: jugadores[0], hora: '4:00 p. m.', lugar: 'Sub-10 · Cancha 1', tipo: 'Entrenamiento' },
  { dia: 'Jueves 22 de octubre', jugador: jugadores[0], hora: '4:00 p. m.', lugar: 'Sub-10 · Cancha 1', tipo: 'Entrenamiento' },
  { dia: 'Viernes 24 de octubre', jugador: jugadores[1], hora: '5:30 p. m.', lugar: 'Sub-9 · Cancha 1', tipo: 'Entrenamiento' },
  { dia: 'Sábado 24 de octubre', jugador: jugadores[0], hora: '10:00 a. m.', lugar: 'Amistoso · Cancha 2', tipo: 'Amistoso' },
]

export default function Horarios() {
  const [jugador, setJugador] = useState(jugadores[0])
  const visibles = dias.filter((d) => d.jugador === jugador)

  return (
    <Page title="Horarios" subtitle="Semana del 19 al 24 de octubre">
      <FilterChips label="Jugador" options={jugadores} value={jugador} onChange={setJugador} />
      <div role="status" className="flex items-start gap-2 rounded-lg bg-warning p-4 text-warning">
        <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
        <p className="text-body-medium">Cambio de horario Sub-9: el vie 24 oct pasa a las 5:30 p. m.</p>
      </div>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((d) => (
          <Card as="li" key={d.dia} className="flex flex-col gap-2">
            <h2 className="text-label uppercase text-secondary">{d.dia}</h2>
            <p className="text-h3">{d.hora}</p>
            <p className="break-words text-body text-secondary">{d.lugar}</p>
            <div><Badge tone="neutral">{d.tipo}</Badge></div>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
