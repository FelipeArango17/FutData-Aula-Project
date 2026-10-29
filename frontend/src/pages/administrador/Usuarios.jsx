import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import EstadoUsuario from '../../components/usuarios/EstadoUsuario.jsx'
import RolUsuario from '../../components/usuarios/RolUsuario.jsx'
import Correo from '../../components/usuarios/Correo.jsx'
import Mensaje from '../../components/usuarios/Mensaje.jsx'
import { useActor } from '../../context/useActor.js'
import { ETIQUETAS_ROL, ROLES, listarUsuarios } from '../../services/usuariosService.js'

// Usuarios y roles (HU-004, RF008, CU-06). Lista con búsqueda y filtros; crear y gestionar van en sus propias pantallas.
// Mockup CU-06 ("10 · Usuarios y permisos"): lista con rol y estado, "Nuevo usuario" y permisos por rol. Diferencias a propósito:
// el formulario va en su pantalla (/nuevo), no hay columna 2FA ni estado "Invitado" (dependen de la invitación, PENDIENTE) y el
// rol se llama "Acudiente" (el mockup dice "Padre": PENDIENTE confirmar con Juan).
// Los entrenadores (registro con documento, categorías, etc.) se gestionan en /administrador/usuarios/entrenadores.
const NUEVO = '/administrador/usuarios/nuevo'
const ENTRENADORES = '/administrador/usuarios/entrenadores'
const nombreCompleto = (u) => `${u.nombre} ${u.apellido}`

export default function Usuarios() {
  const actor = useActor()
  const navigate = useNavigate()
  const location = useLocation()
  const tituloRef = useRef(null)
  const zonaLista = useRef(null)
  const [conTabla, setConTabla] = useState(false)
  const [texto, setTexto] = useState('')
  const [rol, setRol] = useState('todos')
  const [estado, setEstado] = useState('todos')
  const [intento, setIntento] = useState(0)
  const [exito, setExito] = useState(() => (location.state?.aviso ? { texto: location.state.aviso } : null))
  // Resultado con la clave de la petición que lo produjo: si no coincide, está cargando
  const [carga, setCarga] = useState({ clave: null, items: [], error: null })

  useEffect(() => {
    tituloRef.current?.focus()
    // El aviso llegó por el estado de la ruta: se limpia para que no reaparezca al recargar
    if (location.state?.aviso) navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])

  // La tabla necesita unos 40 rem de ancho (mismo criterio que la lista por grupo): si la zona es más angosta, tarjetas.
  useEffect(() => {
    const zona = zonaLista.current
    const observador = new ResizeObserver(([entrada]) => {
      const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
      setConTabla(entrada.contentRect.width / rem >= 40)
    })
    observador.observe(zona)
    return () => observador.disconnect()
  }, [])

  const busqueda = texto.trim()
  const clave = `${intento}|${busqueda}|${rol}|${estado}`
  useEffect(() => {
    let activo = true
    listarUsuarios({ texto: busqueda, rol, estado }, actor)
      .then((items) => activo && setCarga({ clave, items, error: null }))
      .catch((error) => activo && setCarga({ clave, items: [], error }))
    return () => {
      activo = false
    }
  }, [busqueda, rol, estado, actor, clave])

  const cargando = carga.clave !== clave
  const filtrando = Boolean(busqueda) || rol !== 'todos' || estado !== 'todos'
  const limpiar = () => {
    setTexto('')
    setRol('todos')
    setEstado('todos')
  }
  const aviso = carga.error && !cargando
    ? carga.error.codigo === 'SIN_PERMISO'
      ? { texto: carga.error.message }
      : { texto: 'No se pudo cargar la lista de usuarios.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }
    : null

  let contenido
  if (aviso) {
    contenido = null
  } else if (cargando && carga.clave === null) {
    contenido = (
      <Mensaje icono="clock">
        <p role="status">Cargando usuarios…</p>
      </Mensaje>
    )
  } else if (carga.items.length === 0 && !cargando) {
    contenido = (
      <Mensaje icono="info">
        <p>{filtrando ? 'No hay usuarios que coincidan con la búsqueda o los filtros.' : 'Todavía no hay usuarios.'}</p>
        {filtrando && <Button variant="secondary" onClick={limpiar}>Limpiar búsqueda y filtros</Button>}
      </Mensaje>
    )
  } else {
    // Mientras llega una búsqueda nueva se conserva la lista anterior
    const items = carga.items
    contenido = (
      <div aria-busy={cargando}>
        {conTabla ? (
          <table className="w-full table-fixed border-collapse rounded-lg border bg-surface text-left text-body">
            <caption className="sr-only">Usuarios del sistema</caption>
            <thead>
              <tr className="border-b bg-app">
                <th scope="col" className="p-3 text-body-medium">Usuario</th>
                <th scope="col" className="w-[10.5rem] p-3 text-body-medium">Rol</th>
                <th scope="col" className="w-[11rem] p-3 text-body-medium">Estado</th>
                <th scope="col" className="w-[7.5rem] p-3 text-body-medium">Acción</th>
              </tr>
            </thead>
            <tbody>
              {items.map((u) => (
                <tr key={u.id} className="border-b last:border-b-0">
                  <th scope="row" className="p-3 font-normal">
                    <div className="flex min-w-0 items-center gap-3">
                      <Avatar name={nombreCompleto(u)} />
                      <div className="min-w-0">
                        <p className="break-words text-body-medium">{nombreCompleto(u)}</p>
                        <p className="break-words text-small text-secondary"><Correo correo={u.correo} /></p>
                      </div>
                    </div>
                  </th>
                  <td className="p-3"><RolUsuario rol={u.rol} /></td>
                  <td className="p-3"><EstadoUsuario activo={u.activo} /></td>
                  <td className="p-3">
                    <Button to={`/administrador/usuarios/${u.id}`} variant="secondary" aria-label={`Gestionar a ${nombreCompleto(u)}`}>Gestionar</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <ul className="grid grid-cols-1 gap-4" aria-label="Usuarios del sistema">
            {items.map((u) => (
              <Card as="li" key={u.id} className="flex flex-col gap-3">
                {/* Avatar arriba del nombre en pantallas angostas: con texto grande el nombre usa todo el ancho */}
                <div className="flex min-w-0 flex-col items-start gap-2 min-[22rem]:flex-row min-[22rem]:items-center min-[22rem]:gap-3">
                  <Avatar name={nombreCompleto(u)} />
                  <div className="min-w-0">
                    <p className="break-words text-body-medium">{nombreCompleto(u)}</p>
                    <p className="break-words text-small text-secondary"><Correo correo={u.correo} /></p>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <RolUsuario rol={u.rol} />
                  <EstadoUsuario activo={u.activo} />
                </div>
                <Button to={`/administrador/usuarios/${u.id}`} variant="secondary" aria-label={`Gestionar a ${nombreCompleto(u)}`} className="sm:self-start">Gestionar</Button>
              </Card>
            ))}
          </ul>
        )}
      </div>
    )
  }

  return (
    <Page title="Usuarios y roles" subtitle="Cuentas del club y permisos por rol" tituloRef={tituloRef}>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
      <div className="flex flex-wrap gap-2 md:self-start">
        <Button to={NUEVO} className="grow sm:grow-0">Nuevo usuario</Button>
        <Button to={ENTRENADORES} variant="secondary" className="grow sm:grow-0">Gestionar entrenadores</Button>
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Field label="Buscar por nombre o correo" id="buscar" type="search" autoComplete="off" value={texto} onChange={(e) => setTexto(e.target.value)} />
        <Field label="Rol" id="filtro-rol" as="select" value={rol} onChange={(e) => setRol(e.target.value)}>
          <option value="todos">Todos los roles</option>
          {ROLES.map((r) => <option key={r} value={r}>{ETIQUETAS_ROL[r]}</option>)}
        </Field>
        <Field label="Estado" id="filtro-estado" as="select" value={estado} onChange={(e) => setEstado(e.target.value)}>
          <option value="todos">Todos los estados</option>
          <option value="activos">Activos</option>
          <option value="revocados">Acceso revocado</option>
        </Field>
      </div>
      <p aria-live="polite" className="sr-only">
        {!cargando && !aviso ? `${carga.items.length} ${carga.items.length === 1 ? 'usuario' : 'usuarios'}` : ''}
      </p>
      <div ref={zonaLista} className="min-w-0">{contenido}</div>
    </Page>
  )
}
