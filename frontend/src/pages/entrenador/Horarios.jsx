import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const filtros = ['Todas', 'Sub-10', 'Sub-12']
const sesiones = [
  { dia: 'Lunes 19 de octubre', hora: '5:30 p. m.', lugar: 'Sub-12 · Cancha 2', tipo: 'Entrenamiento', cat: 'Sub-12' },
  { dia: 'Martes 20 de octubre', hora: '4:00 p. m.', lugar: 'Sub-10 · Cancha 1', tipo: 'Entrenamiento', cat: 'Sub-10' },
  { dia: 'Miércoles 21 de octubre', hora: '5:30 p. m.', lugar: 'Sub-12 · Cancha 2', tipo: 'Entrenamiento', cat: 'Sub-12' },
  { dia: 'Jueves 22 de octubre · Hoy', hora: '4:00 p. m.', lugar: 'Sub-10 · Cancha 1', tipo: 'Entrenamiento', cat: 'Sub-10' },
  { dia: 'Sábado 24 de octubre', hora: '9:00 a. m.', lugar: 'Sub-12 · Cancha 2', tipo: 'Entrenamiento', cat: 'Sub-12' },
  { dia: 'Sábado 24 de octubre', hora: '10:00 a. m.', lugar: 'Amistoso · Cancha 2', tipo: 'Conflicto', cat: 'Sub-12' },
]

export default function Horarios() {
  const [filtro, setFiltro] = useState('Todas')
  const visibles = sesiones.filter((s) => filtro === 'Todas' || s.cat === filtro)

  return (
    <Page title="Horarios" subtitle="Semana del 19 al 24 de octubre">
      <Button to="/entrenador/horarios/nuevo" className="md:self-start">Nuevo entrenamiento</Button>
      <FilterChips label="Categoría" options={filtros} value={filtro} onChange={setFiltro} />
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((s) => (
          <Card as="li" key={s.dia + s.hora} className="flex flex-col gap-2">
            <h2 className="text-label uppercase text-secondary">{s.dia}</h2>
            <p className="text-h3">{s.hora}</p>
            <p className="break-words text-body text-secondary">{s.lugar}</p>
            <div><Badge tone={s.tipo === 'Conflicto' ? 'danger' : 'neutral'}>{s.tipo}</Badge></div>
          </Card>
        ))}
      </ul>
      <p className="text-body text-secondary">
        Al guardar un cambio, el sistema notifica a jugadores y acudientes.
      </p>
    </Page>
  )
}
