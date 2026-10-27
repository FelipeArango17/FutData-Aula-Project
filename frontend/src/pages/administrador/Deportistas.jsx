import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'

const filtros = ['Todos', 'Sub-8', 'Sub-10', 'Sub-12']
const jugadores = [
  { nombre: 'Santiago Ríos', cat: 'Sub-10', acudiente: 'María Torres', estado: 'Al día', tone: 'success' },
  { nombre: 'Mateo Vélez', cat: 'Sub-10', acudiente: 'Ana Vélez', estado: 'Mora', tone: 'danger' },
  { nombre: 'Emmanuel Arias', cat: 'Sub-12', acudiente: 'Luis Arias', estado: 'Mora', tone: 'danger' },
  { nombre: 'Juan Pablo Cano', cat: 'Sub-10', acudiente: 'Rosa Cano', estado: 'Docs pendientes', tone: 'warning' },
  { nombre: 'Samuel Londoño', cat: 'Sub-10', acudiente: 'Pedro Londoño', estado: 'Al día', tone: 'success' },
]

export default function Deportistas() {
  const [filtro, setFiltro] = useState('Todos')
  const visibles = jugadores.filter((j) => filtro === 'Todos' || j.cat === filtro)

  return (
    <Page title="Deportistas" subtitle="86 jugadores inscritos">
      <Button to="/administrador/deportistas/inscribir" className="md:self-start">Inscribir jugador</Button>
      <FilterChips label="Categoría" options={filtros} value={filtro} onChange={setFiltro} />
      {visibles.length === 0 && <p className="text-body text-secondary">No hay jugadores de ejemplo en esta categoría.</p>}
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((j) => (
          <Card as="li" key={j.nombre} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="break-words text-h3">{j.nombre}</h2>
                <p className="text-body text-secondary">{j.cat} · Acudiente: {j.acudiente}</p>
              </div>
              <Badge tone={j.tone}>{j.estado}</Badge>
            </div>
            {/* La ficha del administrador no está en Figma todavía: botón sin destino */}
            <Button variant="secondary">Ver ficha</Button>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
