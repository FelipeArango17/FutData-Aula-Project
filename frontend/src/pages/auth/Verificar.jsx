import { useEffect, useMemo, useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import AuthLayout from '../../components/auth/AuthLayout.jsx'
import CodigoInput from '../../components/auth/CodigoInput.jsx'
import { claseEnlace } from '../../components/auth/enlaces.js'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Button from '../../components/ui/Button.jsx'
import Card from '../../components/ui/Card.jsx'
import Icon from '../../components/Icon.jsx'
import { useAuth } from '../../context/AuthContext.js'
import { menus } from '../../config/menus.js'

// Primera pantalla del menú de cada rol (se va directo a ella para conservar el estado de la navegación)
const inicioDe = (rol) => menus[rol][0].items[0].to

const reloj = (segundos) => `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`

// Paso 2 del ingreso (HU-002): código de 6 dígitos enviado al correo. Pantalla pública, fuera del AppShell.
// Las reglas (vigencia, intentos, bloqueo, reenvío) las aplica el servicio; aquí solo se muestran.
export default function Verificar() {
  const { estado, usuario, verificarCodigo, reenviarCodigo, estadoCodigo, volverAlLogin } = useAuth()
  const navigate = useNavigate()
  const titulo = useRef(null)
  const primeraCasilla = useRef(null)
  const enviandoRef = useRef(false)
  const [info, setInfo] = useState(estadoCodigo)
  const [ahora, setAhora] = useState(() => Date.now())
  const [codigo, setCodigo] = useState('')
  const [recordar, setRecordar] = useState(false)
  const [errorCampo, setErrorCampo] = useState(null)
  const [aviso, setAviso] = useState(null) // error del servidor (alert)
  const [avisoOk, setAvisoOk] = useState(null) // "Te enviamos un nuevo código." (status)
  const [enviando, setEnviando] = useState(false)

  // Al abrir, el foco va al título (igual que en /login)
  useEffect(() => titulo.current?.focus(), [])

  // Un reloj para la cuenta regresiva del reenvío y del bloqueo
  useEffect(() => {
    const id = setInterval(() => setAhora(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const bloqueoRestante = info?.bloqueoHasta && info.bloqueoHasta > ahora ? Math.ceil((info.bloqueoHasta - ahora) / 1000) : 0
  const bloqueado = bloqueoRestante > 0
  const reenvioRestante = info ? Math.max(0, Math.ceil((info.reenvioDisponibleEn - ahora) / 1000)) : 0

  // El mensaje de bloqueo solo cambia cada minuto (es una región role="alert": no debe anunciarse cada segundo)
  const minutosBloqueo = Math.ceil(bloqueoRestante / 60)
  const avisoBloqueo = useMemo(
    () => (minutosBloqueo > 0
      ? { id: `bloqueo-${minutosBloqueo}`, texto: `Superaste el número de intentos. Intenta de nuevo en ${minutosBloqueo === 1 ? '1 minuto' : `${minutosBloqueo} minutos`}.` }
      : null),
    [minutosBloqueo],
  )

  // Para lectores de pantalla: la cuenta regresiva se anuncia cada 15 segundos, no cada segundo
  const anuncioReenvio = reenvioRestante === 0
    ? 'Ya puedes reenviar el código.'
    : `Podrás reenviar el código en ${Math.ceil(reenvioRestante / 15) * 15} segundos.`

  if (estado === 'verificada') return <Navigate to={inicioDe(usuario.rol)} state={{ enfocarTitulo: true }} replace />
  if (estado !== 'pendiente' || !info) return <Navigate to="/login" replace />

  const enfocarCodigo = () => setTimeout(() => primeraCasilla.current?.focus(), 0)

  function cambiarCodigo(nuevo) {
    setCodigo(nuevo)
    setErrorCampo(null)
  }

  async function enviar(event) {
    event.preventDefault()
    if (enviandoRef.current || bloqueado) return
    setAviso(null)
    setAvisoOk(null)
    // Validación local: no gasta ningún intento
    if (!/^\d{6}$/.test(codigo)) {
      setErrorCampo('Ingresa los 6 dígitos del código.')
      return enfocarCodigo()
    }

    enviandoRef.current = true
    setEnviando(true)
    try {
      const sesion = await verificarCodigo(codigo, recordar)
      navigate(inicioDe(sesion.rol), { state: { enfocarTitulo: true } })
    } catch (error) {
      setInfo(estadoCodigo())
      setAhora(Date.now())
      if (error.codigo === 'INCORRECTO') {
        const n = error.intentosRestantes
        setAviso({ id: Date.now(), texto: `Código incorrecto. Te quedan ${n === 1 ? '1 intento' : `${n} intentos`}.` })
        setCodigo('')
        enfocarCodigo()
      } else if (error.codigo === 'VENCIDO') {
        setAviso({ id: Date.now(), texto: 'El código venció. Solicita uno nuevo.' })
        setCodigo('')
        enfocarCodigo()
      } else if (error.codigo === 'FORMATO') {
        setErrorCampo('Ingresa los 6 dígitos del código.')
        enfocarCodigo()
      } else if (error.codigo === 'SIN_SOLICITUD') {
        volverAlLogin()
      } else if (error.codigo !== 'BLOQUEADO') {
        throw error
      }
      setCodigo((actual) => (error.codigo === 'BLOQUEADO' ? '' : actual))
    } finally {
      enviandoRef.current = false
      setEnviando(false)
    }
  }

  async function reenviar() {
    if (reenvioRestante > 0 || bloqueado || enviandoRef.current) return
    enviandoRef.current = true
    try {
      await reenviarCodigo()
      setAviso(null)
      setErrorCampo(null)
      setCodigo('')
      setAvisoOk({ id: Date.now(), texto: 'Te enviamos un nuevo código.' })
      enfocarCodigo()
    } catch (error) {
      if (error.codigo === 'SIN_SOLICITUD') volverAlLogin()
      else if (!['DEMASIADO_PRONTO', 'BLOQUEADO'].includes(error.codigo)) throw error
    } finally {
      setInfo(estadoCodigo())
      setAhora(Date.now())
      enviandoRef.current = false
    }
  }

  return (
    <AuthLayout variante="centrado">
      <Card className="flex flex-col gap-4 p-4 sm:p-6">
        <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-success text-success">
          <Icon name="mail" className="h-5 w-5" />
        </span>
        <div className="flex flex-col gap-2">
          <h1 ref={titulo} tabIndex={-1} className="break-words text-h2 focus:outline-none">Verifica tu identidad</h1>
          <p className="break-words text-body text-secondary">
            Enviamos un código de 6 dígitos a {info.correoEnmascarado}. Vence en 10 minutos.
          </p>
        </div>

        <AvisoEstado aviso={bloqueado ? avisoBloqueo : aviso} rol="alert" tono="danger" enfocar={false} />
        <AvisoEstado aviso={avisoOk} rol="status" enfocar={false} />
        {bloqueado && (
          <p className="break-words text-small text-secondary">Tiempo restante: {reloj(bloqueoRestante)}</p>
        )}

        <form noValidate onSubmit={enviar} aria-busy={enviando} className="flex flex-col gap-4">
          <div role="group" aria-label="Código de verificación de 6 dígitos" aria-describedby={errorCampo ? 'codigo-error' : undefined} className="flex flex-col gap-2">
            <span className="text-body-medium">Código</span>
            <CodigoInput
              value={codigo}
              onChange={cambiarCodigo}
              disabled={bloqueado}
              invalid={Boolean(errorCampo)}
              describedBy={errorCampo ? 'codigo-error' : undefined}
              inputRef={primeraCasilla}
            />
            {errorCampo && (
              <p id="codigo-error" className="flex items-start gap-1 text-small text-danger">
                <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
                <span className="min-w-0 break-words">{errorCampo}</span>
              </p>
            )}
          </div>

          <label className="flex min-h-11 cursor-pointer items-center gap-3 text-body">
            <input
              type="checkbox"
              checked={recordar}
              onChange={(e) => setRecordar(e.target.checked)}
              className="h-5 w-5 shrink-0 accent-brand-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
            />
            <span className="min-w-0 break-words">Recordar este equipo por 30 días</span>
          </label>

          {/* PENDIENTE: recordar el equipo será una cookie HttpOnly y Secure emitida por el backend */}
          <Button type="submit" disabled={enviando || bloqueado} className={`w-full ${enviando || bloqueado ? 'cursor-not-allowed opacity-70' : ''}`}>
            {enviando ? 'Verificando…' : 'Verificar'}
          </Button>
        </form>

        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
          <button type="button" disabled={reenvioRestante > 0 || bloqueado} onClick={reenviar} className={claseEnlace}>
            {reenvioRestante > 0 ? `Reenviar código (${reloj(reenvioRestante)})` : 'Reenviar código'}
          </button>
          <button type="button" onClick={volverAlLogin} className={claseEnlace}>Volver al inicio de sesión</button>
        </div>
        <p className="sr-only" aria-live="polite">{anuncioReenvio}</p>
      </Card>
    </AuthLayout>
  )
}
