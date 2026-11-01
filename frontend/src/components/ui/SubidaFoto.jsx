import { useEffect, useId, useRef, useState } from 'react'
import Avatar from './Avatar.jsx'
import Button from './Button.jsx'
import DialogoConfirmacion from './DialogoConfirmacion.jsx'
import Icon from '../Icon.jsx'
import { resolverEleccionDeFoto } from '../../utils/eleccionFoto.js'

// Foto con vista previa. No guarda por sí mismo: llama a onGuardar(archivo) y onQuitar(); si fallan, muestra el mensaje del error.
// props: nombre (para el alt y las iniciales), foto (URL de la foto guardada o null), onGuardar(archivo), onQuitar() (opcional:
// sin ella no hay "Quitar foto"), guardarAlElegir (si es true, un archivo válido se entrega a onGuardar apenas se elige, sin el
// botón "Guardar foto": lo usa el asistente de inscripción; la ficha del deportista no lo usa y sigue con "Guardar foto"), deshabilitado, errorInicial (mensaje con el que arranca, para mostrar o probar el estado de error).
// La vista previa usa URL.createObjectURL; se revoca al elegir otro archivo y al desmontar.
export default function SubidaFoto({ nombre, foto = null, onGuardar, onQuitar, guardarAlElegir = false, deshabilitado = false, errorInicial = null }) {
  const id = useId()
  const errorId = `${id}-error`
  const entrada = useRef(null)
  const [archivo, setArchivo] = useState(null)
  const [vistaPrevia, setVistaPrevia] = useState(null)
  const [error, setError] = useState(errorInicial)
  const [ocupado, setOcupado] = useState(false)
  const [confirmarQuitar, setConfirmarQuitar] = useState(false)

  // Al cambiar la vista previa (o al desmontar) se libera la anterior
  useEffect(() => () => {
    if (vistaPrevia) URL.revokeObjectURL(vistaPrevia)
  }, [vistaPrevia])

  function limpiar() {
    setArchivo(null)
    setVistaPrevia(null)
    if (entrada.current) entrada.current.value = ''
  }

  async function elegir(event) {
    const elegido = event.target.files?.[0]
    if (!elegido) return
    if (guardarAlElegir) setOcupado(true)
    const { error: motivo, pendiente } = await resolverEleccionDeFoto(elegido, { guardarAlElegir, onGuardar })
    setError(motivo)
    if (guardarAlElegir) {
      // Sin segundo botón: ya se entregó (o falló). Quien lo usa muestra la foto con la prop `foto`
      setOcupado(false)
      return limpiar()
    }
    if (!pendiente) return limpiar()
    setArchivo(elegido)
    setVistaPrevia(URL.createObjectURL(elegido))
  }

  async function guardar() {
    setOcupado(true)
    setError(null)
    try {
      await onGuardar(archivo)
      limpiar()
    } catch (e) {
      setError(e.message || 'No se pudo guardar la foto. Intenta de nuevo.')
    } finally {
      setOcupado(false)
    }
  }

  function cancelar() {
    setError(null)
    limpiar()
  }

  async function quitar() {
    setConfirmarQuitar(false)
    setOcupado(true)
    setError(null)
    try {
      await onQuitar()
    } catch (e) {
      setError(e.message || 'No se pudo quitar la foto. Intenta de nuevo.')
    } finally {
      setOcupado(false)
    }
  }

  const mostrada = vistaPrevia ?? foto
  const inactivo = deshabilitado || ocupado

  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-wrap items-center gap-4">
        {mostrada ? (
          <img src={mostrada} alt={`Foto de ${nombre}`} className="h-24 w-24 shrink-0 rounded-full border object-cover" />
        ) : (
          <Avatar name={nombre} className="h-24 w-24 text-h3" />
        )}
        <div className="flex min-w-0 flex-1 basis-48 flex-col gap-2">
          {/* El input está oculto a la vista pero se enfoca con el teclado; la etiqueta hace de botón y muestra el foco */}
          <input
            ref={entrada}
            id={id}
            type="file"
            accept="image/jpeg,image/png"
            onChange={elegir}
            disabled={inactivo}
            aria-describedby={[error && errorId, `${id}-ayuda`].filter(Boolean).join(' ')}
            aria-invalid={error ? true : undefined}
            className="peer sr-only"
          />
          <label
            htmlFor={id}
            className="inline-flex min-h-11 cursor-pointer items-center justify-center rounded-md border bg-surface px-4 py-2 text-center text-body-medium text-primary hover:bg-app peer-focus-visible:ring-2 peer-focus-visible:ring-focus peer-focus-visible:ring-offset-2 peer-disabled:cursor-not-allowed peer-disabled:opacity-60"
          >
            Elegir foto
          </label>
          <p id={`${id}-ayuda`} className="break-words text-small text-secondary">JPG o PNG, máximo 5 MB.</p>
        </div>
      </div>

      <div role="alert">
        {error && (
          <p id={errorId} className="flex items-start gap-1 text-small text-danger">
            <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
            <span className="min-w-0 break-words">{error}</span>
          </p>
        )}
      </div>

      {archivo && (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button onClick={guardar} disabled={inactivo} aria-busy={ocupado} className="disabled:opacity-60">
            {ocupado ? 'Guardando…' : 'Guardar foto'}
          </Button>
          <Button variant="secondary" onClick={cancelar} disabled={inactivo}>Cancelar</Button>
        </div>
      )}
      {!archivo && foto && onQuitar && (
        <Button variant="danger" onClick={() => setConfirmarQuitar(true)} disabled={inactivo} className="sm:self-start">Quitar foto</Button>
      )}

      <DialogoConfirmacion
        open={confirmarQuitar}
        titulo="¿Quitar la foto?"
        descripcion={`Se borrará la foto de ${nombre}. Puedes subir otra cuando quieras.`}
        textoConfirmar="Sí, quitar"
        onConfirmar={quitar}
        onCancelar={() => setConfirmarQuitar(false)}
      />
    </div>
  )
}
