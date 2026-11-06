import { useEffect, useRef, useState } from 'react'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import AvisoEstado from '../../components/ui/AvisoEstado.jsx'
import SelectorDeportista from '../../components/pagos/SelectorDeportista.jsx'
import DocumentosDeportista from '../../components/deportistas/DocumentosDeportista.jsx'
import { useActor } from '../../context/useActor.js'
import { listarDeportistas } from '../../services/deportistasService.js'

// Documentos (administrador): se elige un deportista (búsqueda por nombre o documento) y se ven, suben, aprueban y rechazan sus documentos,
// con la API (módulo "documentos") o en memoria. El backend no tiene una cola de pendientes de todo el club: la revisión es por deportista.
export default function Documentos() {
  const actor = useActor()
  const tituloRef = useRef(null)
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: -1, lista: [], error: false })
  const [idDeportista, setIdDeportista] = useState('')

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])
  useEffect(() => {
    let activo = true
    listarDeportistas({ estado: 'activos' })
      .then((lista) => activo && setCarga({ intento, lista, error: false }))
      .catch(() => activo && setCarga({ intento, lista: [], error: true }))
    return () => {
      activo = false
    }
  }, [intento])

  const cargando = carga.intento !== intento
  const elegido = carga.lista.find((d) => d.id === idDeportista)

  return (
    <Page title="Documentación" subtitle="Revisión de los documentos de cada deportista" tituloRef={tituloRef}>
      <Card className="flex w-full max-w-2xl flex-col gap-3">
        <SelectorDeportista deportistas={carga.lista} value={idDeportista} onChange={setIdDeportista} cargando={cargando} errorDeCarga={!cargando && carga.error} id="documentos-deportista" />
        {!cargando && carga.error && (
          <AvisoEstado
            aviso={{ texto: 'No se pudieron cargar los deportistas.', accion: { texto: 'Reintentar', onClick: () => setIntento((n) => n + 1) } }}
            rol="alert" tono="danger" enfocar={false}
          />
        )}
      </Card>
      {elegido ? (
        <DocumentosDeportista key={elegido.id} idDeportista={elegido.id} actor={actor} />
      ) : (
        !cargando && !carga.error && <p className="break-words text-body text-secondary">Elige un deportista para ver y revisar sus documentos.</p>
      )}
    </Page>
  )
}
