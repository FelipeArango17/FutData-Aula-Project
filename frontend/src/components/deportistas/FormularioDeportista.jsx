import { useCallback, useEffect, useRef, useState } from 'react'
import Button from '../ui/Button.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import DialogoConfirmacion from '../ui/DialogoConfirmacion.jsx'
import Icon from '../Icon.jsx'
import CamposDeportista from './CamposDeportista.jsx'
import { categoriaPorFecha } from '../../utils/categorias.js'
import { DEPORTISTA_VACIO, aplicarCambio, validarDatosDeportista } from '../../services/deportistasService.js'

// Datos personales de un deportista (HU-008). Es reutilizable: la edición de la ficha usará el mismo formulario.
// No guarda por sí mismo: llama a onGuardar(valores) y, si el servicio rechaza, muestra el error en el campo que corresponde.
// props: inicial, categorias (las vigentes: [{ id, nombre }]), onGuardar, onCancelar, textoGuardar
// Los campos están en CamposDeportista (los comparte la inscripción en línea).
// La categoría no se elige: al guardar, idCategoria sale de la categoría que corresponde a la fecha de nacimiento (se busca por
// nombre en `categorias`; si no está en la lista queda sin asignar). No se guarda en el estado del formulario.
export default function FormularioDeportista({ inicial = DEPORTISTA_VACIO, categorias, onGuardar, onCancelar, textoGuardar = 'Guardar deportista' }) {
  const [valores, setValores] = useState(inicial)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [sinCambios, setSinCambios] = useState(false) // al editar: el servicio dijo que no hay cambios reales
  const [guardando, setGuardando] = useState(false)
  const [intentos, setIntentos] = useState(0)
  const [confirmarDescartar, setConfirmarDescartar] = useState(false)
  const formRef = useRef(null)
  const enVuelo = useRef(false)

  // Tras un envío con errores, el foco va al primer campo inválido
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])

  function cambiar(campo, valor) {
    setValores(aplicarCambio(valores, campo, valor))
    setSinCambios(false)
    if (errores[campo]) setErrores({ ...errores, [campo]: undefined })
  }

  const texto = (campo) => ({ value: valores[campo], error: errores[campo], onChange: (e) => cambiar(campo, e.target.value) })

  async function enviar(event) {
    event.preventDefault()
    if (enVuelo.current) return
    setErrorGeneral(null)
    setSinCambios(false)
    const encontrados = validarDatosDeportista(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enVuelo.current = true
    setGuardando(true)
    try {
      const categoria = categorias.find((c) => c.nombre === categoriaPorFecha(valores.fechaNacimiento))
      await onGuardar({ ...valores, idCategoria: categoria ? String(categoria.id) : '' })
    } catch (error) {
      if (error.codigo === 'VALIDACION') setErrores(error.errores)
      else if (error.codigo === 'DOCUMENTO_DUPLICADO') setErrores({ documento: error.message })
      else if (error.codigo === 'CATEGORIA_NO_VIGENTE' || error.codigo === 'CATEGORIA_NO_EXISTE') setErrores({ idCategoria: error.message })
      else if (error.codigo === 'SIN_CAMBIOS') {
        setSinCambios(true)
        return
      } else {
        setErrorGeneral(error.codigo === 'SIN_PERMISO' ? error.message : 'No se pudo guardar el deportista. Intenta de nuevo.')
        return
      }
      setIntentos((n) => n + 1)
    } finally {
      enVuelo.current = false
      setGuardando(false)
    }
  }

  const hayCambios = JSON.stringify(valores) !== JSON.stringify(inicial)
  const cerrarConfirmacion = useCallback(() => setConfirmarDescartar(false), [])
  const cancelar = () => (hayCambios ? setConfirmarDescartar(true) : onCancelar())

  const cantidadErrores = Object.values(errores).filter(Boolean).length
  const aviso = cantidadErrores > 0
    ? { texto: `Revisa los campos marcados: ${cantidadErrores} ${cantidadErrores === 1 ? 'campo necesita' : 'campos necesitan'} corrección.` }
    : errorGeneral ? { texto: errorGeneral } : null

  return (
    <form ref={formRef} noValidate onSubmit={enviar} className="flex flex-col gap-4">
      <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
      <CamposDeportista texto={texto} fechaNacimiento={valores.fechaNacimiento} />

      <div role="status">
        {sinCambios && (
          <p className="flex items-center gap-2 text-body-medium">
            <Icon name="info" className="h-5 w-5 shrink-0" />
            No hiciste ningún cambio.
          </p>
        )}
      </div>
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button type="submit" disabled={guardando} aria-busy={guardando} className="disabled:opacity-60">
          {guardando ? 'Guardando…' : textoGuardar}
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
