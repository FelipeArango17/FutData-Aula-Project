import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import { claseEnlace } from '../../components/auth/enlaces.js'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Button from '../../components/ui/Button.jsx'
import Card from '../../components/ui/Card.jsx'
import Field from '../../components/ui/Field.jsx'
import { servicio } from '../../services/auth.js'
import { ESPERA_TRAS_ENVIO_S, ESPERA_TRAS_LIMITE_S, solicitarEnlace } from '../../utils/recuperacion.js'

// Paso 1 de recuperar la contraseña (Supabase Auth): pide el correo y manda el enlace. La respuesta es la misma exista o no
// la cuenta. Tras cada envío el botón queda deshabilitado unos segundos (Supabase limita el reenvío).
export default function Recuperar() {
  const titulo = useRef(null)
  const campo = useRef(null)
  const enviandoRef = useRef(false)
  const [correo, setCorreo] = useState('')
  const [error, setError] = useState(undefined)
  const [intentos, setIntentos] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [espera, setEspera] = useState(0) // segundos que faltan para poder enviar otra vez
  const [aviso, setAviso] = useState(null) // { id, texto, tono, rol }

  useEffect(() => titulo.current?.focus(), [])
  useEffect(() => {
    if (intentos > 0) campo.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])
  useEffect(() => {
    if (espera <= 0) return undefined
    const t = setTimeout(() => setEspera((s) => s - 1), 1000)
    return () => clearTimeout(t)
  }, [espera])

  async function enviar(event) {
    event?.preventDefault()
    if (enviandoRef.current || espera > 0) return
    enviandoRef.current = true
    setEnviando(true)
    setAviso(null)
    try {
      const resultado = await solicitarEnlace({ correo, solicitar: servicio.solicitarRecuperacion })
      if (resultado.tipo === 'errores') {
        setError(resultado.errores.correo)
        setIntentos((n) => n + 1)
        return
      }
      setError(undefined)
      if (resultado.tipo === 'enviado') {
        setAviso({ id: Date.now(), texto: resultado.texto, tono: 'success', rol: 'status' })
        setEspera(ESPERA_TRAS_ENVIO_S)
      } else {
        setAviso({ id: Date.now(), texto: resultado.texto, tono: 'danger', rol: 'alert' })
        if (resultado.limite) setEspera(ESPERA_TRAS_LIMITE_S)
      }
    } finally {
      enviandoRef.current = false
      setEnviando(false)
    }
  }

  return (
    <AuthLayout variante="centrado">
      <Card className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-display focus:outline-none">Recuperar contraseña</h1>
          <p className="break-words text-body text-secondary">Escribe el correo de tu cuenta y te enviaremos un enlace para crear una contraseña nueva.</p>
        </div>

        <AvisoEstado aviso={aviso} rol={aviso?.rol ?? 'status'} tono={aviso?.tono} onCerrar={() => setAviso(null)} />

        <form noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
          <div ref={campo}>
            <Field
              label="Correo electrónico"
              id="correo"
              type="email"
              autoComplete="email"
              inputMode="email"
              requerido
              value={correo}
              error={error}
              onChange={(e) => {
                setCorreo(e.target.value)
                if (error) setError(undefined)
              }}
            />
          </div>
          <Button type="submit" disabled={enviando || espera > 0}>{enviando ? 'Enviando…' : 'Enviar enlace'}</Button>
          {espera > 0 && <p role="status" className="text-small text-secondary">Podrás pedir otro enlace en {espera} s.</p>}
        </form>

        <Link to="/login" className={claseEnlace}>Volver al inicio de sesión</Link>
      </Card>
    </AuthLayout>
  )
}
