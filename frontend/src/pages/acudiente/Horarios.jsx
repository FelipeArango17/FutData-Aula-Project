import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import Icon from '../../components/Icon.jsx'

const jugadores = ['Santiago · Semillero', 'Valeria · Semillero']
const UVA = 'Semillero · UVA El Paraíso · Entrenador: por turnos'
// Semillero (plantilla del club): martes y jueves hay dos sesiones el mismo día (grupo mañana y grupo tarde, según el horario del
// colegio; el niño asiste a una) y el sábado una sola, en Horizontes.
const semillero = (jugador) => [
  { dia: 'Martes 20 de octubre', jugador, hora: '7:30 a. m. – 9:00 a. m.', lugar: `${UVA} · grupo mañana`, tipo: 'Entrenamiento' },
  { dia: 'Martes 20 de octubre', jugador, hora: '4:30 p. m. – 6:00 p. m.', lugar: `${UVA} · grupo tarde`, tipo: 'Entrenamiento' },
  { dia: 'Jueves 22 de octubre', jugador, hora: '7:30 a. m. – 9:00 a. m.', lugar: `${UVA} · grupo mañana`, tipo: 'Entrenamiento' },
  { dia: 'Jueves 22 de octubre', jugador, hora: '4:30 p. m. – 6:00 p. m.', lugar: `${UVA} · grupo tarde`, tipo: 'Entrenamiento' },
  { dia: 'Sábado 24 de octubre', jugador, hora: '8:30 a. m. – 10:00 a. m.', lugar: 'Semillero · Horizontes · Entrenador: por turnos', tipo: 'Entrenamiento' },
]
const dias = [...semillero(jugadores[0]), ...semillero(jugadores[1])]

export default function Horarios() {
  const [jugador, setJugador] = useState(jugadores[0])
  const visibles = dias.filter((d) => d.jugador === jugador)

  return (
    <Page title="Horarios" subtitle="Semana del 19 al 24 de octubre">
      <FilterChips label="Jugador" options={jugadores} value={jugador} onChange={setJugador} />
      <div role="status" className="flex items-start gap-2 rounded-lg bg-warning p-4 text-warning">
        <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
        <p className="text-body-medium">Cambio de horario Semillero: el sábado 24 oct empieza a las 9:00 a. m. (antes 8:30 a. m.).</p>
      </div>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((d) => (
          <Card as="li" key={d.dia + d.jugador + d.hora} className="flex flex-col gap-2">
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
