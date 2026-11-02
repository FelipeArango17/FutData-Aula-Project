import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import { claseEnlace } from '../../components/auth/enlaces.js'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Button from '../../components/ui/Button.jsx'
import Field from '../../components/ui/Field.jsx'
import Icon from '../../components/Icon.jsx'
import { useAuth } from '../../context/AuthContext.js'
import { inicioDe } from '../../config/menus.js'
import { MENSAJE_SIN_CONFIGURACION, sinConfiguracion } from '../../services/auth.js'
import { rutaDeRetorno } from '../../utils/accesoRutas.js'
import { MENSAJES as MENSAJES_VALIDACION, requerido, validarCampos, validarCorreo } from '../../utils/validaciones.js'

function validar(valores) {
  return validarCampos(valores, {
    correo: [(correo) => validarCorreo(correo, { obligatorio: MENSAJES_VALIDACION.loginCorreoObligatorio, formato: MENSAJES_VALIDACION.loginCorreoFormato })],
    contrasena: [requerido(MENSAJES_VALIDACION.loginContrasenaObligatoria, { recortar: false })],
  })
}

// Un mismo mensaje para correo desconocido y contraseña incorrecta: no se revela cuál falló.
const MENSAJES = {
  CREDENCIALES: 'Correo o contraseña incorrectos.',
  INACTIVA: 'Tu cuenta no está activa. Contacta al administrador.',
  PERFIL: 'No pudimos cargar tu perfil. Contacta al administrador.',
  // 401 del backend: el token no sirve (por ejemplo, no trae el id de la sesión)
  SESION_INVALIDA: 'No pudimos validar tu sesión. Inicia sesión de nuevo.',
  // 403 del backend: la cuenta no tiene un rol en FutData o le revocaron el acceso
  SIN_ROL: 'Tu cuenta no tiene acceso a FutData: no tiene un rol asignado o su acceso fue revocado. Contacta al administrador.',
}

function textoBloqueo(msRestantes) {
  const minutos = Math.max(1, Math.ceil(msRestantes / 60000))
  return `Superaste el número de intentos. Intenta de nuevo en ${minutos === 1 ? '1 minuto' : `${minutos} minutos`}.`
}

// Paso 1 del ingreso (HU-001): correo y contraseña. Pantalla pública, fuera del AppShell.
// PENDIENTE: el bloqueo por intentos fallidos de CONTRASEÑA lo gestiona Supabase Auth; aquí no se implementa.
export default function Login() {
  const { estado, usuario, iniciarSesion, retomarCodigo } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const titulo = useRef(null)
  const formulario = useRef(null)
  const enviandoRef = useRef(false)
  const [valores, setValores] = useState({ correo: '', contrasena: '' })
  const [errores, setErrores] = useState({})
  const [intentos, setIntentos] = useState(0)
  const [verContrasena, setVerContrasena] = useState(false)
  const [enviando, setEnviando] = useState(false)
  // Error que llega desde /verificar cuando el backend rechaza la sesión (motivo: SESION_INVALIDA, SIN_ROL, INACTIVA)
  const [aviso, setAviso] = useState(() => (MENSAJES[location.state?.motivo] ? { id: 'motivo-ruta', texto: MENSAJES[location.state.motivo] } : null))
  // Aviso de éxito que llega al cerrar sesión ("Sesión cerrada"). El foco se queda en el título; el aviso es role="status".
  const [avisoOk, setAvisoOk] = useState(() => (location.state?.aviso ? { id: 'aviso-ruta', texto: location.state.aviso } : null))

  // Al abrir, el foco va al título (en /verificar también)
  useEffect(() => titulo.current?.focus(), [])

  // El aviso y el motivo viajan en el estado de la ruta: se limpian para que no reaparezcan al recargar
  useEffect(() => {
    if (location.state?.aviso || location.state?.motivo) navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formulario.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  // Producción sin configurar: ni formulario ni cuentas demo
  if (sinConfiguracion()) {
    return (
      <AuthLayout>
        <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Iniciar sesión</h1>
        <p role="alert" className="break-words text-body">{MENSAJE_SIN_CONFIGURACION}</p>
      </AuthLayout>
    )
  }

  if (estado === 'verificada') return <Navigate to={rutaDeRetorno(location.state?.desde, usuario.rol) ?? inicioDe(usuario.rol)} state={{ enfocarTitulo: true }} replace />
  // Sesión de Supabase restaurada sin 2FA en esta pestaña: se vuelve al paso del código, no se entra directo
  if (estado === 'pendiente' && retomarCodigo) return <Navigate to="/verificar" state={{ desde: location.state?.desde }} replace />

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  async function enviar(event) {
    event?.preventDefault()
    if (enviandoRef.current) return // evita el doble envío
    const encontrados = validar(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enviandoRef.current = true
    setEnviando(true)
    setAviso(null)
    try {
      setAvisoOk(null)
      const resultado = await iniciarSesion(valores.correo, valores.contrasena)
      // Equipo de confianza vigente: la sesión ya queda verificada y la guardia de arriba lleva al panel, sin pedir el código
      if (resultado === 'pendiente') navigate('/verificar', { state: { desde: location.state?.desde } })
    } catch (error) {
      const id = Date.now()
      if (error.codigo === 'RED') {
        setAviso({ id, texto: 'No pudimos conectar. Intenta de nuevo.', accion: { texto: 'Reintentar', onClick: () => enviar() } })
      } else if (error.codigo === 'BLOQUEADO') {
        setAviso({ id, texto: textoBloqueo(error.msRestantes) })
      } else if (MENSAJES[error.codigo]) {
        setAviso({ id, texto: MENSAJES[error.codigo] })
      } else {
        throw error
      }
    } finally {
      enviandoRef.current = false
      setEnviando(false)
    }
  }

  return (
    <AuthLayout>
      <div className="flex flex-col gap-1">
        <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Iniciar sesión</h1>
        <p className="break-words text-body text-secondary">Ingresa con la cuenta que te creó el administrador del club.</p>
      </div>

      <AvisoEstado aviso={avisoOk} rol="status" enfocar={false} onCerrar={() => setAvisoOk(null)} />
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" onCerrar={() => setAviso(null)} />

      <form ref={formulario} noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
        <Field
          label="Correo electrónico"
          id="correo"
          type="email"
          inputMode="email"
          autoComplete="username"
          value={valores.correo}
          error={errores.correo}
          onChange={(e) => cambiar('correo', e.target.value)}
        />
        <Field
          label="Contraseña"
          id="contrasena"
          type={verContrasena ? 'text' : 'password'}
          autoComplete="current-password"
          value={valores.contrasena}
          error={errores.contrasena}
          onChange={(e) => cambiar('contrasena', e.target.value)}
          accion={
            <button
              type="button"
              aria-pressed={verContrasena}
              aria-label="Mostrar contraseña"
              onClick={() => setVerContrasena(!verContrasena)}
              className={`flex min-h-11 shrink-0 items-center gap-1 rounded-md border px-3 text-body-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
                verContrasena ? 'bg-app' : 'bg-surface'
              }`}
            >
              <Icon name={verContrasena ? 'eyeOff' : 'eye'} className="h-5 w-5 shrink-0" />
              <span>Mostrar</span>
            </button>
          }
        />
        {/* PENDIENTE: la recuperación de contraseña será de Supabase Auth; por ahora la ruta es un marcador */}
        <Link to="/recuperar" className={`${claseEnlace} self-end`}>¿Olvidaste tu contraseña?</Link>
        <Button type="submit" disabled={enviando} className={`w-full ${enviando ? 'cursor-wait opacity-70' : ''}`}>
          {enviando ? 'Ingresando…' : 'Iniciar sesión'}
        </Button>
      </form>

      <p className="flex items-start gap-2 rounded-md border bg-surface p-3 text-small text-secondary">
        <Icon name="lock" className="mt-0.5 h-4 w-4 shrink-0" />
        <span className="min-w-0 break-words">
          Después de la contraseña te enviaremos un código de 6 dígitos a tu correo (verificación en dos pasos).
        </span>
      </p>
      <Link to="/inscripcion" className={`${claseEnlace} self-start`}>¿Quieres inscribir a un deportista?</Link>
    </AuthLayout>
  )
}
