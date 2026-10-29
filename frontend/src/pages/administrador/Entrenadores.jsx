import { useEffect, useRef } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import { useEntrenadores } from '../../context/EntrenadoresContext.js'

// Lista de entrenadores (HU-025): era una parte de la maqueta de /administrador/usuarios y se movió aquí sin cambiar sus
// tarjetas ni el flujo (registrar y editar siguen en /administrador/usuarios/entrenadores/...).
// PENDIENTE: un usuario con rol 'entrenador' (usuariosService) aún no está ligado a un entrenador de esta lista.
export default function Entrenadores() {
  const { entrenadores, aviso, cerrarAviso } = useEntrenadores()
  const titulo = useRef(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  return (
    <Page title="Entrenadores" subtitle={`${entrenadores.length} entrenadores registrados`} tituloRef={titulo}>
      <AvisoEstado aviso={aviso} onCerrar={cerrarAviso} />
      <div className="flex flex-wrap gap-2 md:self-start">
        <Button to="/administrador/usuarios/entrenadores/nuevo" className="grow sm:grow-0">Registrar entrenador</Button>
        <Button to="/administrador/usuarios" variant="secondary" className="grow sm:grow-0">Volver a usuarios y roles</Button>
      </div>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {entrenadores.map((e) => {
          const nombre = `${e.nombre} ${e.apellido}`
          return (
            <Card as="li" key={`entrenador-${e.id}`} className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex min-w-0 flex-col items-start gap-2 min-[22rem]:flex-row min-[22rem]:items-center min-[22rem]:gap-3">
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
      </ul>
    </Page>
  )
}
