import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import Avatar from '../../components/ui/Avatar.jsx'

const filtros = ['Todos', 'Entrenadores', 'Tesorería', 'Acudientes']
const usuarios = [
  { nombre: 'Carlos Mendoza', rol: 'Administrador (secretaría)', grupo: 'Administración', activo: true },
  { nombre: 'Laura Gómez', rol: 'Tesorero', grupo: 'Tesorería', activo: true },
  { nombre: 'Carlos Díaz', rol: 'Entrenador · Sub-10', grupo: 'Entrenadores', activo: true },
  { nombre: 'Andrés Mora', rol: 'Entrenador · Sub-12', grupo: 'Entrenadores', activo: true },
  { nombre: 'María Torres', rol: 'Acudiente', grupo: 'Acudientes', activo: true },
  { nombre: 'Pedro Londoño', rol: 'Acudiente', grupo: 'Acudientes', activo: false },
]

export default function Usuarios() {
  const [filtro, setFiltro] = useState('Todos')
  const visibles = usuarios.filter((u) => filtro === 'Todos' || u.grupo === filtro)

  return (
    <Page title="Usuarios y roles" subtitle="12 usuarios del sistema">
      <Button className="md:self-start">Crear usuario</Button>
      <FilterChips label="Filtrar por rol" options={filtros} value={filtro} onChange={setFiltro} />
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {visibles.map((u) => (
          <Card as="li" key={u.nombre} className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <Avatar name={u.nombre} />
              <div className="min-w-0">
                <h2 className="break-words text-body-medium">{u.nombre}</h2>
                <p className="break-words text-small text-secondary">{u.rol}</p>
              </div>
            </div>
            <Badge tone={u.activo ? 'success' : 'danger'}>{u.activo ? 'Activo' : 'Suspendido'}</Badge>
          </Card>
        ))}
      </ul>
    </Page>
  )
}
