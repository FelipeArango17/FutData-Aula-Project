import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import CamposContrasenaNueva from '../../components/auth/CamposContrasenaNueva.jsx'
import { claseEnlace } from '../../components/auth/enlaces.js'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Button from '../../components/ui/Button.jsx'
import Card from '../../components/ui/Card.jsx'
import { servicio } from '../../services/auth.js'
import { MENSAJE_CONTRASENA_ACTUALIZADA, MENSAJE_RESTABLECER_ENLACE_INVALIDO, enviarRestablecer } from '../../utils/recuperacion.js'

// Vista de /restablecer según la fase: 'verificando' (esperando la sesión de recuperación) | 'invalido' | 'listo' (formulario).
// `restablecer(nueva)` guarda la contraseña; `onListo()` navega a /login. Separada de la pantalla para poder dibujar cada fase.
export function VistaRestablecer({ fase, restablecer, onListo, onEnlaceInvalido }) {
  const titulo = useRef(null)
  const formulario = useRef(null)
  const enviandoRef = useRef(false)
  const [valores, setValores] = useState({ contrasena: '', confirmar: '' })
  const [errores, setErrores] = useState({})
  const [intentos, setIntentos] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState(null)

  useEffect(() => titulo.current?.focus(), [fase])
  useEffect(() => {
    if (intentos > 0) formulario.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  async function enviar(event) {
    event?.preventDefault()
    if (enviandoRef.current) return // un envío a la vez
    enviandoRef.current = true
    setEnviando(true)
    setAviso(null)
    try {
      const resultado = await enviarRestablecer({ valores, restablecer })
      if (resultado.tipo === 'errores') {
        setErrores(resultado.errores)
        setIntentos((n) => n + 1)
      } else if (resultado.tipo === 'listo') {
        onListo()
      } else if (resultado.enlaceInvalido) {
        onEnlaceInvalido?.()
      } else {
        setAviso({ id: Date.now(), texto: resultado.texto })
      }
    } finally {
      enviandoRef.current = false
      setEnviando(false)
    }
  }

  if (fase === 'verificando') {
    return (
      <AuthLayout variante="centrado">
        <Card className="flex flex-col gap-4 p-6">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Restablecer contraseña</h1>
          <p role="status" className="break-words text-body text-secondary">Verificando enlace…</p>
        </Card>
      </AuthLayout>
    )
  }

  if (fase === 'invalido') {
    return (
      <AuthLayout variante="centrado">
        <Card className="flex flex-col gap-4 p-6">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Restablecer contraseña</h1>
          <AvisoEstado aviso={{ id: 'enlace-invalido', texto: MENSAJE_RESTABLECER_ENLACE_INVALIDO }} rol="alert" tono="danger" enfocar={false} />
          <Link to="/recuperar" className={claseEnlace}>Pedir un enlace nuevo</Link>
          <Link to="/login" className={claseEnlace}>Volver al inicio de sesión</Link>
        </Card>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout variante="centrado">
      <Card className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Restablecer contraseña</h1>
          <p className="break-words text-body text-secondary">Elige la contraseña nueva con la que vas a entrar a FutData.</p>
        </div>

        <AvisoEstado aviso={aviso} rol="alert" tono="danger" onCerrar={() => setAviso(null)} />

        <form ref={formulario} noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
          <CamposContrasenaNueva valores={valores} errores={errores} onCambiar={cambiar} />
          <Button type="submit" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar contraseña'}</Button>
        </form>
      </Card>
    </AuthLayout>
  )
}

// Destino del enlace del correo de Supabase. Pantalla pública, fuera del AppShell. La sesión de recuperación NO es un
// ingreso: la app no la restaura (ver authServiceReal.restaurarSesion) y al terminar se cierra y se vuelve a /login.
export default function Restablecer() {
  const navigate = useNavigate()
  const [fase, setFase] = useState('verificando')
  const montada = useRef(false)
  const terminada = useRef(false)

  useEffect(() => {
    montada.current = true
    let activo = true
    servicio.esperarRecuperacion().then((decision) => activo && setFase(decision), () => activo && setFase('invalido'))
    return () => {
      activo = false
      montada.current = false
      // Si se sale sin terminar, la sesión de recuperación no se deja abierta. El aplazo evita descartarla en el
      // desmontaje y remontaje simulado de StrictMode.
      setTimeout(() => {
        if (!montada.current && !terminada.current) servicio.descartarRecuperacion()
      }, 0)
    }
  }, [])

  return (
    <VistaRestablecer
      fase={fase}
      restablecer={servicio.restablecerContrasena}
      onEnlaceInvalido={() => setFase('invalido')}
      onListo={() => {
        terminada.current = true
        navigate('/login', { replace: true, state: { aviso: MENSAJE_CONTRASENA_ACTUALIZADA } })
      }}
    />
  )
}
