import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import Mensaje from '../../components/usuarios/Mensaje.jsx'
import { useEntrenadores } from '../../context/EntrenadoresContext.js'

// Lista de entrenadores (HU-025), con la API: activos primero y los inactivos aparte, con su estado en texto e ícono.
// Desactivar pide confirmación (sus categorías quedan sin entrenador); activar no, porque se puede deshacer.
// PENDIENTE: un usuario con rol 'entrenador' (usuariosService) aún no está ligado a un entrenador de esta lista.
const NUEVO = '/administrador/usuarios/entrenadores/nuevo'

function TarjetaEntrenador({ entrenador: e, ocupado, onDesactivar, onActivar }) {
  const nombre = `${e.nombre} ${e.apellido}`
  return (
    <Card as="li" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col items-start gap-2 min-[22rem]:flex-row min-[22rem]:items-center min-[22rem]:gap-3">
          <Avatar name={nombre} />
          <div className="min-w-0">
            <h3 className="break-words text-body-medium">{nombre}</h3>
            <p className="break-words text-small text-secondary">Entrenador · {e.especialidad}</p>
          </div>
        </div>
        <Badge tone={e.activo ? 'success' : 'neutral'}>{e.activo ? 'Activo' : 'Inactivo'}</Badge>
      </div>
      <p className="break-words text-small text-secondary">
        {e.activo
          ? `Categorías: ${e.categorias.length > 0 ? e.categorias.join(', ') : 'sin categorías asignadas'}`
          : 'Inactivo: no dirige categorías. Al activarlo hay que asignárselas de nuevo.'}
      </p>
      <p className="break-words text-small text-secondary">Teléfono: {e.telefono}</p>
      <div className="flex flex-wrap gap-2">
        <Button to={`/administrador/usuarios/entrenadores/${e.id}/editar`} variant="secondary" aria-label={`Editar a ${nombre}`}>
          Editar
        </Button>
        {e.activo ? (
          <Button variant="secondary" disabled={ocupado} onClick={() => onDesactivar(e)} aria-label={`Desactivar a ${nombre}`}>
            Desactivar
          </Button>
        ) : (
          <Button variant="secondary" disabled={ocupado} onClick={() => onActivar(e)} aria-label={`Activar a ${nombre}`}>
            Activar
          </Button>
        )}
      </div>
    </Card>
  )
}

export default function Entrenadores() {
  const { entrenadores, estadoCarga, errorCarga, reintentar, aviso, avisar, cerrarAviso, cambiarEstado } = useEntrenadores()
  const titulo = useRef(null)
  const enVuelo = useRef(false)
  const [porDesactivar, setPorDesactivar] = useState(null)
  const [ocupado, setOcupado] = useState(null) // id del entrenador con una operación en curso
  const [fallo, setFallo] = useState(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  async function cambiar(entrenador, activo) {
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado(entrenador.id)
    setFallo(null)
    cerrarAviso()
    const nombre = `${entrenador.nombre} ${entrenador.apellido}`
    try {
      await cambiarEstado(entrenador.id, activo)
      avisar(activo
        ? `${nombre} está activo. Sus categorías no se restauran: asígnalas desde Editar.`
        : `${nombre} está inactivo. Sus categorías quedaron sin entrenador.`)
    } catch (error) {
      setFallo({ id: Date.now(), texto: error.mensaje ?? error.message })
    } finally {
      enVuelo.current = false
      setOcupado(null)
    }
  }

  function confirmarDesactivar() {
    const entrenador = porDesactivar
    setPorDesactivar(null)
    cambiar(entrenador, false)
  }

  const activos = entrenadores.filter((e) => e.activo)
  const inactivos = entrenadores.filter((e) => !e.activo)
  const sinPermiso = errorCarga?.estado === 403

  let contenido
  if (estadoCarga === 'cargando') {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando entrenadores…</p>
      </Mensaje>
    )
  } else if (estadoCarga === 'error') {
    contenido = (
      <AvisoEstado
        aviso={{
          id: 'error-carga',
          texto: sinPermiso ? errorCarga.mensaje : 'No se pudo cargar la lista de entrenadores.',
          accion: sinPermiso ? undefined : { texto: 'Reintentar', onClick: reintentar },
        }}
        rol="alert"
        tono="danger"
        enfocar={false}
      />
    )
  } else if (entrenadores.length === 0) {
    contenido = (
      <Mensaje icono="info">
        <p>Todavía no hay entrenadores registrados.</p>
        <Button to={NUEVO}>Registrar entrenador</Button>
      </Mensaje>
    )
  } else {
    contenido = (
      <>
        <section aria-labelledby="entrenadores-activos" className="flex flex-col gap-3">
          <h2 id="entrenadores-activos" className="text-h3">Activos ({activos.length})</h2>
          {activos.length > 0 ? (
            <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {activos.map((e) => (
                <TarjetaEntrenador key={`entrenador-${e.id}`} entrenador={e} ocupado={ocupado === e.id} onDesactivar={setPorDesactivar} onActivar={(x) => cambiar(x, true)} />
              ))}
            </ul>
          ) : (
            <p className="text-body text-secondary">No hay entrenadores activos.</p>
          )}
        </section>
        {inactivos.length > 0 && (
          <section aria-labelledby="entrenadores-inactivos" className="flex flex-col gap-3">
            <h2 id="entrenadores-inactivos" className="text-h3">Inactivos ({inactivos.length})</h2>
            <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {inactivos.map((e) => (
                <TarjetaEntrenador key={`entrenador-${e.id}`} entrenador={e} ocupado={ocupado === e.id} onDesactivar={setPorDesactivar} onActivar={(x) => cambiar(x, true)} />
              ))}
            </ul>
          </section>
        )}
      </>
    )
  }

  const subtitulo = estadoCarga === 'listo'
    ? `${entrenadores.length} ${entrenadores.length === 1 ? 'entrenador registrado' : 'entrenadores registrados'}`
    : undefined
  const categoriasPorQuitar = porDesactivar?.categorias ?? []

  return (
    <Page title="Entrenadores" subtitle={subtitulo} tituloRef={titulo}>
      <AvisoEstado aviso={aviso} onCerrar={cerrarAviso} />
      <AvisoEstado aviso={fallo} onCerrar={() => setFallo(null)} rol="alert" tono="danger" />
      <div className="flex flex-wrap gap-2 md:self-start">
        <Button to={NUEVO} className="grow sm:grow-0">Registrar entrenador</Button>
        <Button to="/administrador/usuarios" variant="secondary" className="grow sm:grow-0">Volver a usuarios y roles</Button>
      </div>
      {contenido}
      <DialogoConfirmacion
        open={Boolean(porDesactivar)}
        titulo="¿Desactivar al entrenador?"
        descripcion={porDesactivar
          ? `${porDesactivar.nombre} ${porDesactivar.apellido} dejará de dirigir categorías${categoriasPorQuitar.length > 0 ? `: ${categoriasPorQuitar.join(', ')} quedarán sin entrenador` : ''}. Sus datos y su historial se conservan. Puedes activarlo después, pero habrá que asignarle las categorías de nuevo.`
          : ''}
        textoConfirmar="Sí, desactivar"
        onConfirmar={confirmarDesactivar}
        onCancelar={() => setPorDesactivar(null)}
      />
    </Page>
  )
}
