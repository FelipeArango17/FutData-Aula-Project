import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import Icon from '../../components/Icon.jsx'

// Cachorros y Semillero tienen el mismo horario en la plantilla del club: martes y jueves hay dos sesiones el mismo día (grupo
// mañana y grupo tarde, según el horario del colegio; el niño asiste a la de su grupo) y el sábado una sola, en Horizontes.
// El grupo de cada jugador es de ejemplo (en el servicio de deportistas: Santiago, mañana; Valeria, tarde) y NO se guarda en la
// base de datos. La sesión de su grupo se destaca con "Tu grupo".
const jugadores = ['Santiago · Cachorros', 'Valeria · Semillero']
const GRUPO = { [jugadores[0]]: 'mañana', [jugadores[1]]: 'tarde' }
const sesiones = (jugador, categoria, sabado) => {
  const lugar = `${categoria} · UVA El Paraíso · Entrenador: por turnos`
  const entre = (dia, hora, grupo) => ({ dia, jugador, hora, lugar: `${lugar} · grupo ${grupo}`, tipo: 'Entrenamiento', grupo })
  return [
    entre('Martes 20 de octubre', '7:30 a. m. – 9:00 a. m.', 'mañana'),
    entre('Martes 20 de octubre', '4:30 p. m. – 6:00 p. m.', 'tarde'),
    entre('Jueves 22 de octubre', '7:30 a. m. – 9:00 a. m.', 'mañana'),
    entre('Jueves 22 de octubre', '4:30 p. m. – 6:00 p. m.', 'tarde'),
    { dia: 'Sábado 24 de octubre', jugador, hora: sabado, lugar: `${categoria} · Horizontes · Entrenador: por turnos`, tipo: 'Entrenamiento', grupo: null },
  ]
}
const dias = [
  ...sesiones(jugadores[0], 'Cachorros', '9:00 a. m. – 10:00 a. m.'), // con el cambio de horario del aviso
  ...sesiones(jugadores[1], 'Semillero', '8:30 a. m. – 10:00 a. m.'),
]

export default function Horarios() {
  const [jugador, setJugador] = useState(jugadores[0])
  const visibles = dias.filter((d) => d.jugador === jugador)

  return (
    <Page title="Horarios" subtitle="Semana del 19 al 24 de octubre">
      <FilterChips label="Jugador" options={jugadores} value={jugador} onChange={setJugador} />
      {jugador === jugadores[0] && (
        <div role="status" className="flex items-start gap-2 rounded-lg bg-warning p-4 text-warning">
          <Icon name="alert" className="mt-0.5 h-5 w-5 shrink-0" />
          <p className="text-body-medium">Cambio de horario Cachorros: el sábado 24 oct empieza a las 9:00 a. m. (antes 8:30 a. m.).</p>
        </div>
      )}
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((d) => (
          <Card as="li" key={d.dia + d.jugador + d.hora} className="flex flex-col gap-2">
            <h2 className="text-label uppercase text-secondary">{d.dia}</h2>
            <p className="text-h3">{d.hora}</p>
            <p className="break-words text-body text-secondary">{d.lugar}</p>
            <div className="flex flex-wrap gap-2">
              <Badge tone="neutral">{d.tipo}</Badge>
              {d.grupo && d.grupo === GRUPO[d.jugador] && <Badge tone="success">Tu grupo</Badge>}
            </div>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
