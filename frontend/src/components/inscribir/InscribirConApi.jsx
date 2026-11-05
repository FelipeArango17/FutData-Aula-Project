import { useEffect, useRef, useState } from 'react'
import Page from '../ui/Page.jsx'
import Card from '../ui/Card.jsx'
import Button from '../ui/Button.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import SubidaFoto from '../ui/SubidaFoto.jsx'
import CamposDeportista from '../deportistas/CamposDeportista.jsx'
import { useActor } from '../../context/useActor.js'
import { DEPORTISTA_VACIO, aplicarCambio, crearDeportista, validarDatosDeportista } from '../../services/deportistasService.js'
import { guardarFoto } from '../../services/fotosService.js'

// Inscribir jugador con la API (módulo "deportistas"): un solo formulario con lo que acepta POST /api/deportistas
// (DeportistaRequestDTO: datos personales, posición y grupo) y, si se elige, la foto (POST /api/deportistas/{id}/foto).
// El backend aún no tiene endpoints de acudiente (cuenta y vínculo) ni de documentos, así que NO se piden aquí y la pantalla lo dice.
// La categoría la calcula el backend con el año de nacimiento. 400 -> mensaje por campo; 409 -> documento repetido; 403 -> sin permiso.
const VOLVER = '/administrador/deportistas'

export default function InscribirConApi() {
  const [intento, setIntento] = useState(0)
  return <Formulario key={intento} onOtro={() => setIntento((n) => n + 1)} />
}

function Formulario({ onOtro }) {
  const actor = useActor()
  const tituloRef = useRef(null)
  const formRef = useRef(null)
  const enVuelo = useRef(false)
  const [valores, setValores] = useState(DEPORTISTA_VACIO)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [foto, setFoto] = useState(null) // File elegido; se sube después de crear al jugador
  const [fotoPrevia, setFotoPrevia] = useState(null)
  const [intentos, setIntentos] = useState(0)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState(null) // { deportista, errorFoto }

  useEffect(() => {
    tituloRef.current?.focus()
  }, [resultado])
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])
  useEffect(() => () => {
    if (fotoPrevia) URL.revokeObjectURL(fotoPrevia)
  }, [fotoPrevia])

  const texto = (campo) => ({
    value: valores[campo],
    error: errores[campo],
    onChange: (e) => {
      setValores((previos) => aplicarCambio(previos, campo, e.target.value))
      setErrores((previos) => ({ ...previos, [campo]: undefined }))
    },
  })

  const elegirFoto = (archivo) => {
    setFoto(archivo)
    setFotoPrevia(archivo ? URL.createObjectURL(archivo) : null)
  }

  async function enviar(event) {
    event.preventDefault()
    if (enVuelo.current) return
    setErrorGeneral(null)
    const encontrados = validarDatosDeportista(valores)
    setErrores(encontrados)
    if (Object.keys(encontrados).length > 0) return setIntentos((n) => n + 1)

    enVuelo.current = true
    setEnviando(true)
    try {
      const deportista = await crearDeportista(valores, actor)
      let errorFoto = null
      if (foto) {
        try {
          await guardarFoto(deportista.id, foto, actor)
        } catch (e) {
          errorFoto = e.message || 'No se pudo subir la foto.'
        }
      }
      setResultado({ deportista, errorFoto })
    } catch (e) {
      if (e.codigo === 'VALIDACION') setErrores(e.errores)
      else if (e.codigo === 'DOCUMENTO_DUPLICADO') setErrores({ documento: e.message })
      else if (e.codigo === 'SIN_PERMISO') setErrorGeneral('No tienes permiso para inscribir jugadores.')
      else setErrorGeneral(e.estado ? e.message : 'No se pudo inscribir al jugador. Intenta de nuevo; lo que escribiste no se perdió.')
      if (e.codigo === 'VALIDACION' || e.codigo === 'DOCUMENTO_DUPLICADO') setIntentos((n) => n + 1)
    } finally {
      enVuelo.current = false
      setEnviando(false)
    }
  }

  if (resultado) {
    const { deportista, errorFoto } = resultado
    return (
      <Page title="Jugador inscrito" subtitle={`${deportista.nombre} ${deportista.apellido}`} tituloRef={tituloRef}>
        <AvisoEstado aviso={{ texto: 'Jugador inscrito.' }} rol="status" />
        {errorFoto && <AvisoEstado aviso={{ texto: `El jugador quedó registrado, pero la foto no se subió: ${errorFoto} Puedes subirla desde su ficha.` }} rol="alert" tono="danger" enfocar={false} />}
        <Card className="flex w-full max-w-2xl flex-col gap-3">
          {deportista.categoria && <p className="break-words text-body">Categoría: {deportista.categoria}</p>}
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button to={`${VOLVER}/${deportista.id}`}>Ver ficha del deportista</Button>
            <Button variant="secondary" onClick={onOtro}>Inscribir otro jugador</Button>
            <Button to={VOLVER} variant="secondary">Ver deportistas</Button>
          </div>
        </Card>
      </Page>
    )
  }

  const cantidad = Object.values(errores).filter(Boolean).length
  const aviso = cantidad > 0
    ? { texto: `Revisa los campos marcados: ${cantidad} ${cantidad === 1 ? 'campo necesita' : 'campos necesitan'} corrección.` }
    : errorGeneral ? { texto: errorGeneral } : null

  return (
    <Page title="Inscribir jugador" subtitle="Datos del jugador y su foto. Los campos con * son obligatorios." tituloRef={tituloRef}>
      <Card className="w-full max-w-2xl">
        <form ref={formRef} noValidate aria-busy={enviando} onSubmit={enviar} className="flex flex-col gap-4">
          <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
          <p className="break-words text-small text-secondary">
            Por ahora solo se registran los datos del jugador y su foto. El acudiente (su cuenta y el vínculo con el jugador) y los documentos todavía no se pueden cargar desde aquí.
          </p>
          <CamposDeportista texto={texto} fechaNacimiento={valores.fechaNacimiento} />
          <section aria-labelledby="foto-inscribir" className="flex min-w-0 flex-col gap-3">
            <h2 id="foto-inscribir" className="text-body-medium">Foto del jugador (opcional)</h2>
            <SubidaFoto
              nombre={`${valores.nombre} ${valores.apellido}`.trim() || 'el jugador'}
              foto={foto ? fotoPrevia : null}
              guardarAlElegir
              deshabilitado={enviando}
              onGuardar={async (archivo) => elegirFoto(archivo)}
              onQuitar={async () => elegirFoto(null)}
            />
          </section>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button type="submit" disabled={enviando} aria-busy={enviando} className="disabled:opacity-60">{enviando ? 'Inscribiendo…' : 'Inscribir jugador'}</Button>
            <Button to={VOLVER} variant="secondary">Cancelar</Button>
          </div>
        </form>
      </Card>
    </Page>
  )
}
