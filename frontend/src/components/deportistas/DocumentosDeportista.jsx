import { useEffect, useState } from 'react'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import Field from '../ui/Field.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import Icon from '../Icon.jsx'
import FormularioDocumento from './FormularioDocumento.jsx'
import {
  ETIQUETAS_ESTADO_DOCUMENTO, ETIQUETAS_TIPO_DOCUMENTO_JUGADOR, listarDocumentos, revisarDocumento, subirDocumento,
} from '../../services/documentosService.js'
import { formatearTamano } from '../../utils/archivos.js'
import { fechaLargaConAnio, partesMomento } from '../../utils/fechas.js'

// Documentos del deportista en la ficha (solo el administrador). Muestra la lista, deja subir uno y aprobar o rechazar los pendientes.
// PENDIENTE (backend): "Ver documento" con URL firmada de Supabase Storage; hoy solo se ve el nombre y el tamaño del archivo.
// PENDIENTE (backend): al rechazar, el acudiente recibe un correo (n8n); aquí solo se muestra el texto informativo.
const TONO = { PENDIENTE: 'warning', APROBADO: 'success', RECHAZADO: 'danger' }

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-app p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

function TarjetaDocumento({ documento: d, onRevisar }) {
  const [rechazando, setRechazando] = useState(false)
  const [comentario, setComentario] = useState('')
  const [error, setError] = useState(null)
  const [ocupado, setOcupado] = useState(false)

  async function revisar(estado) {
    setOcupado(true)
    setError(null)
    try {
      await onRevisar(d, { estado, comentario })
    } catch (e) {
      setError(e.errores?.comentario ?? (e.codigo === 'SIN_PERMISO' ? e.message : 'No se pudo guardar la revisión. Intenta de nuevo.'))
      setOcupado(false)
    }
  }

  return (
    <Card as="li" className="flex flex-col gap-3 bg-app">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h4 className="min-w-0 break-words text-body-medium">{ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo]}</h4>
        <Badge tone={TONO[d.estado]}>{ETIQUETAS_ESTADO_DOCUMENTO[d.estado]}</Badge>
      </div>
      <p className="break-words text-body">
        {d.nombreOriginal} <span className="text-secondary">· {formatearTamano(d.tamanoBytes)}</span>
      </p>
      <p className="break-words text-small text-secondary">
        Cargado el {fechaLargaConAnio(partesMomento(d.cargadoEn).fecha)}
        {d.fechaVencimiento && ` · Vence el ${fechaLargaConAnio(d.fechaVencimiento)}`}
      </p>
      {d.estado === 'RECHAZADO' && d.comentario && (
        <p className="break-words text-small text-danger">Comentario del administrador: {d.comentario}</p>
      )}

      {d.estado === 'PENDIENTE' && !rechazando && (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button onClick={() => revisar('APROBADO')} disabled={ocupado} aria-label={`Aprobar ${ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo].toLowerCase()}: ${d.nombreOriginal}`}>Aprobar</Button>
          <Button variant="secondary" onClick={() => setRechazando(true)} disabled={ocupado} aria-label={`Rechazar ${ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo].toLowerCase()}: ${d.nombreOriginal}`}>Rechazar</Button>
        </div>
      )}
      {d.estado === 'PENDIENTE' && rechazando && (
        <div className="flex flex-col gap-3">
          <Field
            as="textarea" rows={3} label="Motivo del rechazo" id={`comentario-${d.id}`} requerido
            value={comentario} error={error} onChange={(e) => { setComentario(e.target.value); setError(null) }}
            className={`w-full rounded-md border bg-surface px-3 py-2 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${error ? 'border-danger' : ''}`}
          />
          <p className="break-words text-small text-secondary">Se avisará al acudiente por correo.</p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button variant="danger" onClick={() => revisar('RECHAZADO')} disabled={ocupado} aria-busy={ocupado}>
              {ocupado ? 'Rechazando…' : 'Confirmar rechazo'}
            </Button>
            <Button variant="secondary" onClick={() => { setRechazando(false); setComentario(''); setError(null) }} disabled={ocupado}>Cancelar</Button>
          </div>
        </div>
      )}
      {d.estado === 'PENDIENTE' && !rechazando && error && <p role="alert" className="break-words text-small text-danger">{error}</p>}
    </Card>
  )
}

// Parte visual. documentos: lista, o null mientras carga. error: no se pudo cargar. exito: { texto } del último aviso.
export function PanelDocumentos({ documentos, error = false, onReintentar, exito = null, onCerrarExito, onSubir, onRevisar, claveFormulario = 0 }) {
  let lista
  if (error) {
    lista = (
      <AvisoEstado
        aviso={{ texto: 'No se pudieron cargar los documentos.', accion: { texto: 'Reintentar', onClick: onReintentar } }}
        rol="alert"
        tono="danger"
        enfocar={false}
      />
    )
  } else if (documentos === null) {
    lista = (
      <Mensaje icono="clock">
        <p role="status">Cargando los documentos…</p>
      </Mensaje>
    )
  } else if (documentos.length === 0) {
    lista = <Mensaje><p>Todavía no hay documentos de este deportista.</p></Mensaje>
  } else {
    lista = (
      <ul aria-label="Documentos del deportista" className="grid grid-cols-1 gap-3">
        {documentos.map((d) => <TarjetaDocumento key={`${d.id}-${d.estado}`} documento={d} onRevisar={onRevisar} />)}
      </ul>
    )
  }

  return (
    <Card aria-labelledby="documentos-titulo" className="flex w-full max-w-2xl flex-col gap-4">
      <h2 id="documentos-titulo" className="break-words text-h3">Documentos</h2>
      <AvisoEstado aviso={exito} onCerrar={onCerrarExito} />
      {lista}
      <section aria-labelledby="subir-documento-titulo" className="flex flex-col gap-3 border-t pt-4">
        <h3 id="subir-documento-titulo" className="break-words text-body-medium">Subir documento</h3>
        <FormularioDocumento key={claveFormulario} onSubir={onSubir} />
      </section>
    </Card>
  )
}

export default function DocumentosDeportista({ idDeportista, actor }) {
  const [intento, setIntento] = useState(0)
  // Resultado con el intento que lo produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1, items: null, error: false })
  const [exito, setExito] = useState(null)
  const [claveFormulario, setClaveFormulario] = useState(0)

  useEffect(() => {
    let activo = true
    listarDocumentos(idDeportista)
      .then((items) => activo && setCarga({ intento, items, error: false }))
      .catch(() => activo && setCarga({ intento, items: null, error: true }))
    return () => {
      activo = false
    }
  }, [idDeportista, intento])

  async function subir(datos) {
    const nuevo = await subirDocumento(idDeportista, datos, actor)
    setCarga((previa) => ({ ...previa, items: [nuevo, ...(previa.items ?? [])] }))
    setClaveFormulario((n) => n + 1)
    setExito({ texto: 'Documento subido. Queda pendiente de revisión.' })
  }

  async function revisar(documento, datos) {
    const revisado = await revisarDocumento(documento.id, datos, actor)
    setCarga((previa) => ({ ...previa, items: previa.items.map((x) => (x.id === revisado.id ? revisado : x)) }))
    setExito({ texto: revisado.estado === 'APROBADO' ? 'Documento aprobado.' : 'Documento rechazado. Se avisará al acudiente por correo.' })
  }

  const cargando = carga.intento !== intento
  return (
    <PanelDocumentos
      documentos={cargando ? null : carga.items}
      error={!cargando && carga.error}
      onReintentar={() => setIntento((n) => n + 1)}
      exito={exito}
      onCerrarExito={() => setExito(null)}
      onSubir={subir}
      onRevisar={revisar}
      claveFormulario={claveFormulario}
    />
  )
}
