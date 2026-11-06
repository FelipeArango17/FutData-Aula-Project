import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import Icon from '../../components/Icon.jsx'
import DocumentosDelAcudiente from '../../components/deportistas/DocumentosDelAcudiente.jsx'
import { useActor } from '../../context/useActor.js'
import { documentosPorApi, listarDocumentos, listarJugadoresDelAcudiente, subirDocumento, verDocumento } from '../../services/documentosService.js'
import { mensajeDeError } from '../../utils/documentosApi.js'
import { abrirEnPestanaNueva } from '../../utils/abrirDocumento.js'

// Documentos de los jugadores del acudiente. El acudiente puede subir cualquiera de los 7 tipos (cargadoPor = "Acudiente"); si no
// puede, los sube el administrador desde la ficha. Aprobar o rechazar sigue siendo solo del administrador.
// PENDIENTE (backend): "Ver documento" con URL firmada; hoy se ve el nombre y el tamaño del archivo.
export default function Documentos() {
  const actor = useActor()
  const tituloRef = useRef(null)
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: -1, jugadores: null, error: false })
  const [exito, setExito] = useState(null)
  const [claves, setClaves] = useState({})

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  useEffect(() => {
    let activo = true
    ;(async () => {
      const propios = await listarJugadoresDelAcudiente(actor)
      return Promise.all(propios.map(async (j) => ({ ...j, ...(await listarDocumentos(j.id)) })))
    })()
      .then((jugadores) => activo && setCarga({ intento, jugadores, error: false }))
      .catch((e) => activo && setCarga({ intento, jugadores: null, error: e }))
    return () => {
      activo = false
    }
  }, [actor, intento])

  async function subir(idJugador, datos) {
    let nuevo
    try {
      nuevo = await subirDocumento(idJugador, datos, actor)
    } catch (e) {
      // 409: ya hay un documento activo de ese tipo; se refresca ese jugador para que "Subir" lo oculte
      if (e.codigo === 'DOCUMENTO_ACTIVO') {
        listarDocumentos(idJugador).then((r) => setCarga((previa) => ({ ...previa, jugadores: previa.jugadores.map((j) => (j.id === idJugador ? { ...j, ...r } : j)) }))).catch(() => {})
      }
      throw e
    }
    setCarga((previa) => ({ ...previa, jugadores: previa.jugadores.map((j) => (j.id === idJugador ? { ...j, documentos: [nuevo, ...j.documentos] } : j)) }))
    setClaves((previas) => ({ ...previas, [idJugador]: (previas[idJugador] ?? 0) + 1 }))
    setExito({ texto: 'Documento subido. Queda pendiente de revisión.' })
  }

  const cargando = carga.intento !== intento
  let contenido
  if (cargando) {
    contenido = (
      <div className="flex items-start gap-3 rounded-lg border bg-surface p-4">
        <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
        <p role="status" className="min-w-0 break-words text-body">Cargando los documentos…</p>
      </div>
    )
  } else if (carga.error) {
    contenido = (
      <AvisoEstado
        aviso={{ texto: mensajeDeError(carga.error, 'No se pudieron cargar los documentos.'), accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
        rol="alert" tono="danger" enfocar={false}
      />
    )
  } else if (carga.jugadores.length === 0) {
    contenido = <p className="break-words text-body text-secondary">Todavía no tienes jugadores vinculados.</p>
  } else {
    contenido = <DocumentosDelAcudiente
        jugadores={carga.jugadores} onSubir={subir} claves={claves}
        onVer={documentosPorApi() ? (documento) => abrirEnPestanaNueva(async () => (await verDocumento(documento.id, actor)).url) : undefined}
      />
  }

  return (
    <Page title="Documentos" subtitle="Documentos de tus jugadores" tituloRef={tituloRef}>
      <AvisoEstado aviso={exito} onCerrar={() => setExito(null)} />
      {contenido}
      <p className="break-words text-body text-secondary">
        Formatos PDF, JPG o PNG, máximo 5 MB por archivo. Un administrador revisa cada documento. Solo los 4 documentos obligatorios completan la inscripción; los de «Si aplica» se suben cuando corresponda. Si no puedes subirlos, el administrador puede hacerlo por ti.
      </p>
    </Page>
  )
}
