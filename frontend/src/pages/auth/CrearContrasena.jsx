import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import { claseEnlace } from '../../components/auth/enlaces.js'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import CamposContrasenaNueva from '../../components/auth/CamposContrasenaNueva.jsx'
import { crearContrasena } from '../../services/entrenadoresService.js'
import { MENSAJE_CONTRASENA_CREADA, MENSAJE_ENLACE_INVALIDO, enviarContrasena } from '../../utils/crearContrasena.js'

// Alta de entrenadores: el enlace del correo trae ?token=. Pantalla pública, fuera del AppShell. El token solo vive en
// memoria (estado): no se guarda en localStorage ni se escribe en consola.
export default function CrearContrasena() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [token] = useState(() => params.get('token')?.trim() || null)
  const titulo = useRef(null)
  const formulario = useRef(null)
  const enviandoRef = useRef(false)
  const [valores, setValores] = useState({ contrasena: '', confirmar: '' })
  const [errores, setErrores] = useState({})
  const [intentos, setIntentos] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState(null)

  useEffect(() => titulo.current?.focus(), [])
  useEffect(() => {
    if (intentos > 0) formulario.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  async function enviar(event) {
    event?.preventDefault()
    if (enviandoRef.current) return
    enviandoRef.current = true
    setEnviando(true)
    setAviso(null)
    try {
      const resultado = await enviarContrasena({ token, valores, crear: crearContrasena })
      if (resultado.tipo === 'errores') {
        setErrores(resultado.errores)
        setIntentos((n) => n + 1)
      } else if (resultado.tipo === 'listo') {
        navigate('/login', { replace: true, state: { aviso: MENSAJE_CONTRASENA_CREADA } })
      } else {
        setAviso({ id: Date.now(), texto: resultado.texto })
      }
    } finally {
      enviandoRef.current = false
      setEnviando(false)
    }
  }

  if (!token) {
    return (
      <AuthLayout variante="centrado">
        <Card className="flex flex-col gap-4 p-6">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Crear contraseña</h1>
          <AvisoEstado aviso={{ id: 'sin-token', texto: MENSAJE_ENLACE_INVALIDO }} rol="alert" tono="danger" enfocar={false} />
          <Link to="/login" className={claseEnlace}>Ir al inicio de sesión</Link>
        </Card>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout variante="centrado">
      <Card className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Crear contraseña</h1>
          <p className="break-words text-body text-secondary">Elige la contraseña con la que vas a entrar a FutData.</p>
        </div>

        <AvisoEstado aviso={aviso} rol="alert" tono="danger" onCerrar={() => setAviso(null)} />

        <form ref={formulario} noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
          <CamposContrasenaNueva valores={valores} errores={errores} onCambiar={cambiar} />
          <Button type="submit" disabled={enviando}>{enviando ? 'Guardando…' : 'Crear contraseña'}</Button>
        </form>
      </Card>
    </AuthLayout>
  )
}
