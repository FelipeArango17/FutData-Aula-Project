import { useCallback, useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import GrupoRoles from './GrupoRoles.jsx'
import { USUARIO_VACIO, validarDatosUsuario } from '../../services/usuariosService.js'

// Datos de un usuario nuevo: nombres, apellidos, correo y rol. No guarda por sí mismo: llama a onGuardar(valores) y,
// si el servicio rechaza, muestra el error donde corresponde. No pide contraseña (PENDIENTE: invitación por Supabase Auth).
export default function FormularioUsuario({ onGuardar, onCancelar }) {
  const [valores, setValores] = useState(USUARIO_VACIO)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const [confirmarDescartar, setConfirmarDescartar] = useState(false)
  const formRef = useRef(null)
  const enVuelo = useRef(false)

  // Tras un envío con errores, el foco va al primer campo inválido (en el orden de la pantalla)
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores({ ...valores, [campo]: valor })
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  const texto = (campo) => ({ value: valores[campo], error: errores[campo], onChange: (e) => cambiar(campo, e.target.value) })

  async function enviar(event) {
    event.preventDefault()
    if (enVuelo.current) return
    setErrorGeneral(null)
    const encontrados = validarDatosUsuario(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enVuelo.current = true
    setGuardando(true)
    try {
      await onGuardar(valores)
    } catch (error) {
      if (error.codigo === 'VALIDACION') setErrores(error.errores)
      else if (error.codigo === 'CORREO_DUPLICADO') setErrores({ correo: error.message })
      else {
        setErrorGeneral(error.codigo === 'SIN_PERMISO' ? error.message : 'No se pudo crear el usuario. Intenta de nuevo.')
        return
      }
      setIntentos((n) => n + 1)
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  const hayCambios = JSON.stringify(valores) !== JSON.stringify(USUARIO_VACIO)
  const cerrarConfirmacion = useCallback(() => setConfirmarDescartar(false), [])
  const cancelar = () => (hayCambios ? setConfirmarDescartar(true) : onCancelar())

  const cantidadErrores = Object.values(errores).filter(Boolean).length
  const aviso = cantidadErrores > 0
    ? { texto: `Revisa los campos marcados: ${cantidadErrores} ${cantidadErrores === 1 ? 'campo necesita' : 'campos necesitan'} corrección.` }
    : errorGeneral ? { texto: errorGeneral } : null

  return (
    <form ref={formRef} noValidate onSubmit={enviar} className="flex flex-col gap-4">
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
      <Field label="Nombres" id="nombre" autoComplete="off" {...texto('nombre')} />
      <Field label="Apellidos" id="apellido" autoComplete="off" {...texto('apellido')} />
      <Field label="Correo electrónico" id="correo" type="email" autoComplete="off" {...texto('correo')} />
      <GrupoRoles id="rol" value={valores.rol} error={errores.rol} onChange={(rol) => cambiar('rol', rol)} />
      {/* PENDIENTE: invitación por correo (Supabase Auth + n8n). Esta versión NO envía ningún correo. */}
      <p className="break-words text-small text-secondary">
        Esta versión crea la cuenta, pero todavía no envía ninguna invitación por correo.
      </p>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button type="submit" disabled={guardando} aria-busy={guardando} className="disabled:opacity-60">
          {guardando ? 'Creando…' : 'Crear usuario'}
        </Button>
        <Button variant="secondary" onClick={cancelar} disabled={guardando}>Cancelar</Button>
      </div>

      <DialogoConfirmacion
        open={confirmarDescartar}
        titulo="¿Descartar los cambios?"
        descripcion="Los datos que escribiste no se guardarán."
        textoConfirmar="Sí, descartar"
        onConfirmar={() => {
          setConfirmarDescartar(false)
          onCancelar()
        }}
        onCancelar={cerrarConfirmacion}
      />
    </form>
  )
}
