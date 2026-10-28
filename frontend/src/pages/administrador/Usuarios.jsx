import { useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import FilterChips from '../../components/ui/FilterChips.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import { useEntrenadores } from '../../context/EntrenadoresContext.js'

const filtros = ['Todos', 'Entrenadores', 'Tesorería', 'Acudientes']
// Los entrenadores salen del servicio; los demás usuarios siguen siendo de ejemplo.
const usuarios = [
  { nombre: 'Carlos Mendoza', rol: 'Administrador (secretaría)', grupo: 'Administración', activo: true },
  { nombre: 'Laura Gómez', rol: 'Tesorero', grupo: 'Tesorería', activo: true },
  { nombre: 'María Torres', rol: 'Acudiente', grupo: 'Acudientes', activo: true },
  { nombre: 'Pedro Londoño', rol: 'Acudiente', grupo: 'Acudientes', activo: false },
]
const OTROS_USUARIOS = 10 // usuarios de ejemplo sin entrenadores (el subtítulo original decía 12 con 2 entrenadores)

export default function Usuarios() {
  const { entrenadores, aviso, cerrarAviso } = useEntrenadores()
  const [filtro, setFiltro] = useState('Todos')
  const visibles = usuarios.filter((u) => filtro === 'Todos' || u.grupo === filtro)
  const verEntrenadores = filtro === 'Todos' || filtro === 'Entrenadores'

  return (
    <Page title="Usuarios y roles" subtitle={`${OTROS_USUARIOS + entrenadores.length} usuarios del sistema`}>
      <AvisoEstado aviso={aviso} onCerrar={cerrarAviso} />
      <div className="flex flex-wrap gap-2 md:self-start">
        <Button className="grow sm:grow-0">Crear usuario</Button>
        <Button to="/administrador/usuarios/entrenadores/nuevo" variant="secondary" className="grow sm:grow-0">Registrar entrenador</Button>
      </div>
      <FilterChips label="Filtrar por rol" options={filtros} value={filtro} onChange={setFiltro} />
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {verEntrenadores && entrenadores.map((e) => {
          const nombre = `${e.nombre} ${e.apellido}`
          return (
            <Card as="li" key={`entrenador-${e.id}`} className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex min-w-0 items-center gap-3">
                  <Avatar name={nombre} />
                  <div className="min-w-0">
                    <h2 className="break-words text-body-medium">{nombre}</h2>
                    <p className="break-words text-small text-secondary">Entrenador · {e.especialidad}</p>
                  </div>
                </div>
                <Badge tone="success">Activo</Badge>
              </div>
              <p className="break-words text-small text-secondary">Categorías: {e.categorias.join(', ')}</p>
              <p className="break-words text-small text-secondary">Teléfono: {e.telefono}</p>
              <Button to={`/administrador/usuarios/entrenadores/${e.id}/editar`} variant="secondary" aria-label={`Editar a ${nombre}`} className="sm:self-start">
                Editar
              </Button>
            </Card>
          )
        })}
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
