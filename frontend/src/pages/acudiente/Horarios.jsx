import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import Icon from '../../components/Icon.jsx'

const jugadores = ['Santiago · Sub-10', 'Valeria · Sub-9']
const dias = [
  { dia: 'Lunes 19 de octubre', jugador: jugadores[0], hora: '7:00 p. m. – 8:00 p. m.', lugar: 'Sub-10 · UVA El Paraíso · Entrenador: Dairo', tipo: 'Entrenamiento' },
  { dia: 'Martes 20 de octubre', jugador: jugadores[0], hora: '7:00 p. m. – 8:00 p. m.', lugar: 'Sub-10 · UVA El Paraíso · Entrenador: Dairo', tipo: 'Entrenamiento' },
  { dia: 'Viernes 23 de octubre', jugador: jugadores[0], hora: '7:00 p. m. – 8:00 p. m.', lugar: 'Sub-10 · UVA El Paraíso · Entrenador: Dairo', tipo: 'Entrenamiento' },
  { dia: 'Sábado 24 de octubre', jugador: jugadores[0], hora: '8:30 a. m. – 10:00 a. m.', lugar: 'Sub-10 · UVA El Paraíso · Entrenador: Dairo', tipo: 'Entrenamiento' },
  { dia: 'Viernes 23 de octubre', jugador: jugadores[1], hora: '7:00 p. m. – 8:00 p. m.', lugar: 'Sub-9 · UVA El Paraíso · Entrenador: Nicolás', tipo: 'Entrenamiento' },
  { dia: 'Sábado 24 de octubre', jugador: jugadores[1], hora: '8:30 a. m. – 10:00 a. m.', lugar: 'Sub-9 · UVA El Paraíso · Entrenador: Nicolás', tipo: 'Entrenamiento' },
]

export default function Horarios() {
  const [jugador, setJugador] = useState(jugadores[0])
  const visibles = dias.filter((d) => d.jugador === jugador)

  return (
    <Page title="Horarios" subtitle="Semana del 19 al 24 de octubre">
      <FilterChips label="Jugador" options={jugadores} value={jugador} onChange={setJugador} />
      <div role="status" className="flex items-start gap-2 rounded-lg bg-warning p-4 text-warning">
        <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
        <p className="text-body-medium">Cambio de horario Sub-9: el viernes 23 oct pasa a las 7:30 p. m.</p>
      </div>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((d) => (
          <Card as="li" key={d.dia + d.jugador} className="flex flex-col gap-2">
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
