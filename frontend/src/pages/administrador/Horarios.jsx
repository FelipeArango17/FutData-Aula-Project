import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const filtros = ['Todas', 'Sub-8', 'Sub-10', 'Sub-12']
const sesiones = [
  { dia: 'Lunes 19', hora: '5:30 p. m.', lugar: 'Sub-12 · Cancha 2 · Entrenador: Andrés Mora', tipo: 'Entreno', cat: 'Sub-12' },
  { dia: 'Jueves 22 · Hoy', hora: '4:00 p. m.', lugar: 'Sub-10 · Cancha 1 · Entrenador: Carlos Díaz', tipo: 'Entreno', cat: 'Sub-10' },
  { dia: 'Sábado 24', hora: '9:00 a. m.', lugar: 'Sub-12 · Cancha 2', tipo: 'Entreno', cat: 'Sub-12' },
  { dia: 'Sábado 24', hora: '10:00 a. m.', lugar: 'Amistoso · Cancha 2', tipo: 'Conflicto', cat: 'Sub-12' },
]

export default function Horarios() {
  const [filtro, setFiltro] = useState('Todas')
  const visibles = sesiones.filter((s) => filtro === 'Todas' || s.cat === filtro)

  return (
    <Page title="Entrenamientos y horarios" subtitle="Todas las categorías · semana del 19 al 24 de octubre">
      <Button className="md:self-start">Nuevo entrenamiento</Button>
      <FilterChips label="Categoría" options={filtros} value={filtro} onChange={setFiltro} />
      {visibles.length === 0 && <p className="text-body text-secondary">No hay entrenamientos de ejemplo en esta categoría.</p>}
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
    </Page>
  )
}
