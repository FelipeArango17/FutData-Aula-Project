import { useEffect, useReducer, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import IndicadorPasos from '../../components/inscribir/IndicadorPasos.jsx'
import PasoJugador from '../../components/inscribir/PasoJugador.jsx'
import PasoAcudiente from '../../components/inscribir/PasoAcudiente.jsx'
import PasoDocumentos from '../../components/inscribir/PasoDocumentos.jsx'
import PasoConfirmar from '../../components/inscribir/PasoConfirmar.jsx'
import InscribirConApi from '../../components/inscribir/InscribirConApi.jsx'
import { useActor } from '../../context/useActor.js'
import { ESTADO_INICIAL, PASOS, ULTIMO_PASO, reducirAsistente } from '../../services/asistenteInscripcion.js'
import { deportistasPorApi } from '../../services/deportistasService.js'
import { inscribirJugador } from '../../services/inscribirJugadorService.js'
import { TIPOS_DOCUMENTO_CLUB } from '../../utils/documentosClub.js'

// Inscribir jugador: el ÚNICO flujo para registrar a un jugador (reemplaza a "Nuevo deportista"). Ruta /administrador/deportistas/inscribir.
// Asistente de 4 pasos: 1 Jugador · 2 Acudiente · 3 Documentos (opcional) · 4 Confirmar. Valida cada paso antes de avanzar, manda el
// foco al primer error y conserva lo escrito al ir y volver. Al confirmar llama a inscribirJugador (services/inscribirJugadorService.js).
// PENDIENTE (backend): crear la cuenta del acudiente y enviarle un correo con enlace; subir los archivos a Storage.
const SUBTITULOS = [
  'Datos del jugador. Los campos con * son obligatorios.',
  'Datos de la persona responsable del jugador.',
  'Documentos y foto del jugador (opcional por ahora).',
  'Revisa los datos antes de inscribir al jugador.',
]
const VOLVER = '/administrador/deportistas'

// Cada "Inscribir otro jugador" empieza un asistente nuevo (cambia la clave), sin recargar la página: los datos en memoria se conservan
// Con la API (módulo "deportistas") no hay asistente: el backend solo acepta los datos del jugador y su foto (InscribirConApi).
export default function Inscribir() {
  return deportistasPorApi() ? <InscribirConApi /> : <InscribirConAsistente />
}

function InscribirConAsistente() {
  const [asistente, setAsistente] = useState(0)
  return <Asistente key={asistente} onOtro={() => setAsistente((n) => n + 1)} />
}

function Asistente({ onOtro }) {
  const actor = useActor()
  const tituloRef = useRef(null)
  const formRef = useRef(null)
  const [estado, dispatch] = useReducer(reducirAsistente, ESTADO_INICIAL)
  const [fotoPrevia, setFotoPrevia] = useState(null) // URL de la vista previa de la foto elegida
  const fotoPreviaRef = useRef(null)
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [resultado, setResultado] = useState(null) // { deportista, parcial }
  const { paso, jugador, acudiente, documentos, foto, errores, intentos } = estado

  // Al cambiar de paso (o al terminar) el foco va al título; tras un intento fallido, al primer campo con error
  useEffect(() => {
    tituloRef.current?.focus()
  }, [paso, resultado])
  useEffect(() => {
    if (intentos > 0) formRef.current?.querySelector('[aria-invalid="true"]')?.focus()
  }, [intentos])
  useEffect(() => () => {
    if (fotoPreviaRef.current) URL.revokeObjectURL(fotoPreviaRef.current)
  }, [])

  function elegirFoto(archivo) {
    if (fotoPreviaRef.current) URL.revokeObjectURL(fotoPreviaRef.current)
    fotoPreviaRef.current = archivo ? URL.createObjectURL(archivo) : null
    setFotoPrevia(fotoPreviaRef.current)
    dispatch({ tipo: 'foto', archivo })
  }

  async function confirmar() {
    if (enviando) return
    setEnviando(true)
    setErrorGeneral(null)
    try {
      const adjuntos = TIPOS_DOCUMENTO_CLUB.filter((t) => documentos[t.id]).map((t) => ({ tipo: t.id, archivo: documentos[t.id] }))
      const hecho = await inscribirJugador({ deportista: jugador, acudiente, documentos: adjuntos, foto }, actor)
      setResultado({ deportista: hecho.deportista, categoria: hecho.categoria, parcial: false })
    } catch (e) {
      if (e.paso && e.errores) dispatch({ tipo: 'errorDelServidor', paso: e.paso, errores: e.errores })
      else if (e.codigo === 'INSCRIPCION_PARCIAL') setResultado({ deportista: e.deportista, categoria: null, parcial: true })
      else setErrorGeneral(e.codigo === 'SIN_PERMISO' ? e.message : 'No se pudo inscribir al jugador. Intenta de nuevo; lo que escribiste no se perdió.')
    } finally {
      setEnviando(false)
    }
  }

  if (resultado) {
    const { deportista, categoria, parcial } = resultado
    return (
      <Page title={parcial ? 'Jugador registrado' : 'Jugador inscrito'} subtitle={`${deportista.nombre} ${deportista.apellido}`} tituloRef={tituloRef}>
        <AvisoEstado
          aviso={{ texto: parcial ? 'El jugador se registró, pero no se pudo terminar la inscripción (acudiente, documentos o foto). Complétala desde su ficha.' : 'Jugador inscrito.' }}
          rol={parcial ? 'alert' : 'status'}
          tono={parcial ? 'danger' : undefined}
        />
        <Card className="flex w-full max-w-2xl flex-col gap-3">
          {categoria && <p className="break-words text-body">Categoría: {categoria}</p>}
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
    <Page title="Inscribir jugador" subtitle={`Paso ${paso} de ${ULTIMO_PASO} · ${PASOS[paso - 1]}. ${SUBTITULOS[paso - 1]}`} tituloRef={tituloRef}>
      <IndicadorPasos paso={paso} />
      <Card className="w-full max-w-2xl">
        <form
          ref={formRef} noValidate aria-busy={enviando} className="flex flex-col gap-4"
          onSubmit={(e) => { e.preventDefault(); if (paso === ULTIMO_PASO) confirmar(); else dispatch({ tipo: 'continuar' }) }}
        >
          <AvisoEstado aviso={aviso} rol="alert" tono="danger" enfocar={false} />
          {paso === 1 && <PasoJugador jugador={jugador} errores={errores} onCambio={(campo, valor) => dispatch({ tipo: 'jugador', campo, valor })} />}
          {paso === 2 && <PasoAcudiente acudiente={acudiente} errores={errores} onCambio={(campo, valor) => dispatch({ tipo: 'acudiente', campo, valor })} />}
          {paso === 3 && (
            <PasoDocumentos
              documentos={documentos} foto={foto} fotoPrevia={fotoPrevia} nombreJugador={`${jugador.nombre} ${jugador.apellido}`.trim()} errores={errores}
              onDocumento={(idTipo, archivo) => dispatch({ tipo: 'documento', idTipo, archivo })} onFoto={elegirFoto}
            />
          )}
          {paso === 4 && <PasoConfirmar jugador={jugador} acudiente={acudiente} documentos={documentos} foto={foto} onEditar={(p) => dispatch({ tipo: 'ir', paso: p })} />}
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            {paso > 1 && <Button variant="secondary" onClick={() => dispatch({ tipo: 'atras' })} disabled={enviando}>Atrás</Button>}
            <Button type="submit" disabled={enviando} aria-busy={enviando} className="disabled:opacity-60">
              {paso === ULTIMO_PASO ? (enviando ? 'Inscribiendo…' : 'Inscribir jugador') : 'Continuar'}
            </Button>
            <Button to={VOLVER} variant="secondary">Cancelar</Button>
          </div>
        </form>
      </Card>
    </Page>
  )
}
