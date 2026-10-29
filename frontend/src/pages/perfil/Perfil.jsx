import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../../components/ui/DialogoConfirmacion.jsx'
import Correo from '../../components/usuarios/Correo.jsx'
import { roleLabels, sampleUsers } from '../../config/menus.js'
import { useAuth } from '../../context/AuthContext.js'
import { useActor } from '../../context/useActor.js'
import { useRole } from '../../context/RoleContext.js'
import { fechaLargaConAnio, partesMomento } from '../../utils/fechas.js'

// Perfil (HU-003): datos de la cuenta en solo lectura y la sección "Sesión y equipos". Ruta /perfil dentro del AppShell,
// para los 4 roles. No hay campos editables ni otras funciones: cambiar nombre o contraseña no existe todavía.
// Sin sesión verificada (desarrollo con el selector de rol) muestra la cuenta de demostración del rol activo.
// PENDIENTE (Juan): "cerrar sesión en todos los equipos" y el equipo de confianza son una simulación local; la revocación
// real de sesiones en otros dispositivos y el token del equipo de confianza deben vivir en el backend (JWT / refresh token).

function Dato({ etiqueta, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-small text-secondary">{etiqueta}</dt>
      <dd className="break-words text-body">{children}</dd>
    </div>
  )
}

export default function Perfil() {
  const navigate = useNavigate()
  const { role } = useRole()
  const actor = useActor()
  const { estado, usuario, cerrarSesionGlobal, estadoEquipo, dejarDeRecordarEquipo } = useAuth()
  const tituloRef = useRef(null)
  const estadoEquipoRef = useRef(null)
  const enVuelo = useRef(false)
  const [equipo, setEquipo] = useState(() => estadoEquipo(actor.correo))
  const [dialogo, setDialogo] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [exito, setExito] = useState(null)
  const [fallo, setFallo] = useState(null)

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  const conSesion = estado === 'verificada' && usuario?.rol === role
  const nombre = conSesion ? usuario.nombre : sampleUsers[role]
  const cerrarDialogo = useCallback(() => setDialogo(false), [])

  function dejarDeRecordar() {
    dejarDeRecordarEquipo(actor.correo)
    setEquipo(estadoEquipo(actor.correo))
    setExito({ texto: 'Este equipo ya no está marcado como de confianza.' })
    // el botón desaparece: el foco pasa al estado del equipo
    setTimeout(() => estadoEquipoRef.current?.focus(), 0)
  }

  async function cerrarEnTodos() {
    setDialogo(false)
    if (enVuelo.current) return
    enVuelo.current = true
    setOcupado(true)
    setFallo(null)
    try {
      await cerrarSesionGlobal(actor.correo)
      navigate('/login', { replace: true, state: { aviso: 'Cerraste sesión en todos los equipos' } })
    } catch {
      setFallo({ texto: 'No se pudo cerrar la sesión en todos los equipos. Intenta de nuevo.' })
      enVuelo.current = false
      setOcupado(false)
    }
  }

  return (
    <Page title="Mi perfil" subtitle="Los datos de tu cuenta y la seguridad de tu sesión" tituloRef={tituloRef}>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      <AvisoEstado aviso={fallo} onCerrar={() => setFallo(null)} rol="alert" tono="danger" />

      <Card aria-labelledby="datos-cuenta" className="flex w-full max-w-2xl flex-col gap-4">
        <h2 id="datos-cuenta" className="break-words text-h3">Datos de la cuenta</h2>
        <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4">
          <Dato etiqueta="Nombre">{nombre}</Dato>
          <Dato etiqueta="Correo electrónico"><Correo correo={actor.correo} /></Dato>
          <Dato etiqueta="Rol">{roleLabels[role]}</Dato>
        </dl>
        <p className="break-words text-small text-secondary">Estos datos solo se pueden consultar. Si algo está mal, avisa al administrador del club.</p>
      </Card>

      <Card aria-labelledby="sesion-equipos" className="flex w-full max-w-2xl flex-col gap-4">
        <h2 id="sesion-equipos" className="break-words text-h3">Sesión y equipos</h2>

        <div className="flex flex-col items-start gap-2">
          <h3 className="break-words text-body-medium">Este equipo</h3>
          <div ref={estadoEquipoRef} tabIndex={-1} className="flex flex-col items-start gap-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
            {equipo ? (
              <>
                <Badge tone="success">Equipo de confianza</Badge>
                <p className="break-words text-body">
                  No te pediremos el código de verificación aquí hasta el {fechaLargaConAnio(partesMomento(equipo.expiraEn).fecha)}.
                </p>
              </>
            ) : (
              <>
                <Badge tone="neutral">No es de confianza</Badge>
                <p className="break-words text-body">Aquí te pediremos el código de verificación cada vez que inicies sesión.</p>
              </>
            )}
          </div>
          {equipo && <Button variant="secondary" onClick={dejarDeRecordar}>Dejar de recordar este equipo</Button>}
        </div>

        <div className="flex flex-col items-start gap-2">
          <h3 className="break-words text-body-medium">Todos los equipos</h3>
          <p className="break-words text-body text-secondary">
            Si perdiste un equipo o crees que alguien más entró a tu cuenta, cierra tu sesión en todos.
          </p>
          <Button variant="danger" disabled={ocupado} aria-busy={ocupado} onClick={() => setDialogo(true)} className="disabled:opacity-60">
            {ocupado ? 'Cerrando sesión…' : 'Cerrar sesión en todos los equipos'}
          </Button>
        </div>
      </Card>

      <DialogoConfirmacion
        open={dialogo}
        titulo="¿Cerrar sesión en todos los equipos?"
        descripcion="Se cerrará tu sesión en todos los equipos donde la tengas abierta, también en este, y se olvidarán los equipos de confianza. Para volver a entrar tendrás que iniciar sesión y verificar el código de nuevo."
        textoConfirmar="Sí, cerrar en todos"
        onConfirmar={cerrarEnTodos}
        onCancelar={cerrarDialogo}
      />
    </Page>
  )
}
