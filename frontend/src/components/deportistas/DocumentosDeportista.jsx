import { useEffect, useState } from 'react'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import Field from '../ui/Field.jsx'
import AvisoEstado from '../ui/AvisoEstado.jsx'
import Icon from '../Icon.jsx'
import FormularioDocumento from './FormularioDocumento.jsx'
import ListaVerificacionDocumentos from './ListaVerificacionDocumentos.jsx'
import {
  COMENTARIO_MAXIMO, ETIQUETAS_ESTADO_DOCUMENTO, ETIQUETAS_TIPO_DOCUMENTO_JUGADOR, aprobarDocumento, documentosPorApi, listarDocumentos,
  rechazarDocumento, subirDocumento, verDocumento,
} from '../../services/documentosService.js'
import { formatearTamano } from '../../utils/archivos.js'
import { abrirEnPestanaNueva } from '../../utils/abrirDocumento.js'
import { avisoDeRechazo, mensajeDeError } from '../../utils/documentosApi.js'
import { TIPOS_DOCUMENTO_CLUB, calcularInscripcion, tiposParaSubir } from '../../utils/documentosClub.js'
import { fechaLargaConAnio, partesMomento } from '../../utils/fechas.js'

// Documentos del deportista en la ficha (solo el administrador). Muestra la lista, deja subir uno y aprobar o rechazar los pendientes.
// Con la API (módulo "documentos"): "Ver documento" pide la URL firmada (5 minutos) al tocarlo y la abre en otra pestaña; aprobar y rechazar
// refrescan el estado de la inscripción con la respuesta; al rechazar se muestra si el correo al acudiente salió (true), falló (false) o no había
// acudientes con correo (null). Un solo documento activo por tipo: "Subir" solo ofrece los tipos sin documento activo.
const TONO = { PENDIENTE: 'warning', APROBADO: 'success', RECHAZADO: 'danger' }

function Mensaje({ icono = 'info', rol, children }) {
  return (
    <div role={rol} className="flex items-start gap-3 rounded-lg border bg-app p-4">
      <Icon name={icono} className="mt-0.5 h-5 w-5 shrink-0 text-secondary" />
      <div className="flex min-w-0 flex-1 flex-col gap-3 break-words text-body">{children}</div>
    </div>
  )
}

function TarjetaDocumento({ documento: d, onAprobar, onRechazar, onVer }) {
  const [rechazando, setRechazando] = useState(false)
  const [comentario, setComentario] = useState('')
  const [error, setError] = useState(null)
  const [errorComentario, setErrorComentario] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [viendo, setViendo] = useState(false)
  const nombreTipo = ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo]
  const obligatorio = TIPOS_DOCUMENTO_CLUB.find((t) => t.id === d.tipo)?.obligatorio

  async function ver() {
    setViendo(true)
    setError(null)
    try {
      await onVer(d)
    } catch (e) {
      setError(mensajeDeError(e, 'No se pudo abrir el documento. Intenta de nuevo.'))
    } finally {
      setViendo(false)
    }
  }

  async function revisar(accion) {
    setError(null)
    setErrorComentario(null)
    if (accion === 'rechazar') {
      const texto = comentario.trim()
      if (!texto) return setErrorComentario('Escribe el motivo del rechazo.')
      if (texto.length > COMENTARIO_MAXIMO) return setErrorComentario(`El comentario no puede superar los ${COMENTARIO_MAXIMO} caracteres.`)
    }
    setOcupado(true)
    try {
      await (accion === 'aprobar' ? onAprobar(d) : onRechazar(d, comentario))
    } catch (e) {
      if (e.errores?.comentario) setErrorComentario(e.errores.comentario)
      else setError(mensajeDeError(e, 'No se pudo guardar la revisión. Intenta de nuevo.'))
      setOcupado(false)
    }
  }

  return (
    <Card as="li" className="flex flex-col gap-3 bg-app">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h4 className="min-w-0 break-words text-body-medium">{nombreTipo} <span className="text-small text-secondary">· {obligatorio ? 'Obligatorio' : 'Si aplica'}</span></h4>
        <Badge tone={TONO[d.estado]}>{ETIQUETAS_ESTADO_DOCUMENTO[d.estado]}</Badge>
      </div>
      <p className="break-words text-body">
        {d.nombreOriginal} <span className="text-secondary">· {formatearTamano(d.tamanoBytes)}</span>
      </p>
      <p className="break-words text-small text-secondary">
        Cargado el {fechaLargaConAnio(partesMomento(d.cargadoEn).fecha)}
        {d.cargadoPor && ` · Cargado por: ${d.cargadoPor}`}
        {d.fechaVencimiento && ` · Vence el ${fechaLargaConAnio(d.fechaVencimiento)}`}
      </p>
      {d.estado === 'RECHAZADO' && d.comentario && (
        <p className="break-words text-small text-danger">Comentario del administrador: {d.comentario}</p>
      )}
      {onVer && (
        <div>
          <Button variant="secondary" onClick={ver} disabled={viendo} aria-busy={viendo} aria-label={`Ver documento: ${nombreTipo.toLowerCase()}, ${d.nombreOriginal}`}>
            {viendo ? 'Abriendo…' : 'Ver documento'}
          </Button>
        </div>
      )}

      {d.estado === 'PENDIENTE' && !rechazando && (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button onClick={() => revisar('aprobar')} disabled={ocupado} aria-busy={ocupado} aria-label={`Aprobar ${nombreTipo.toLowerCase()}: ${d.nombreOriginal}`}>{ocupado ? 'Aprobando…' : 'Aprobar'}</Button>
          <Button variant="secondary" onClick={() => setRechazando(true)} disabled={ocupado} aria-label={`Rechazar ${nombreTipo.toLowerCase()}: ${d.nombreOriginal}`}>Rechazar</Button>
        </div>
      )}
      {d.estado === 'PENDIENTE' && rechazando && (
        <div className="flex flex-col gap-3">
          <Field
            as="textarea" rows={3} label="Motivo del rechazo" id={`comentario-${d.id}`} requerido maxLength={COMENTARIO_MAXIMO}
            describedBy={`contador-${d.id}`}
            value={comentario} error={errorComentario} onChange={(e) => { setComentario(e.target.value); setErrorComentario(null) }}
            className={`w-full rounded-md border bg-surface px-3 py-2 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${errorComentario ? 'border-danger' : ''}`}
          />
          <p id={`contador-${d.id}`} className="break-words text-small text-secondary">{comentario.length} de {COMENTARIO_MAXIMO} caracteres. Si el deportista tiene acudientes con correo, se les avisa.</p>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button variant="danger" onClick={() => revisar('rechazar')} disabled={ocupado} aria-busy={ocupado}>
              {ocupado ? 'Rechazando…' : 'Confirmar rechazo'}
            </Button>
            <Button variant="secondary" onClick={() => { setRechazando(false); setComentario(''); setError(null); setErrorComentario(null) }} disabled={ocupado}>Cancelar</Button>
          </div>
        </div>
      )}
      <div role="alert">{error && <p className="break-words text-small text-danger">{error}</p>}</div>
    </Card>
  )
}

// Parte visual. documentos: lista, o null mientras carga. error: no se pudo cargar. exito: { texto, tono? } del último aviso.
// onVer es opcional: sin él no hay botón "Ver documento" (el modo en memoria no tiene archivos).
export function PanelDocumentos({
  documentos, estadoInscripcion, documentosFaltantes = [], tieneFoto = false, rutaFoto, error = false, onReintentar, exito = null, onCerrarExito,
  onSubir, onAprobar, onRechazar, onVer, claveFormulario = 0,
}) {
  let lista
  if (error) {
    lista = (
      <AvisoEstado
        aviso={{ texto: typeof error === 'string' ? error : 'No se pudieron cargar los documentos.', accion: { texto: 'Reintentar', onClick: onReintentar } }}
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
        {documentos.map((d) => <TarjetaDocumento key={`${d.id}-${d.estado}`} documento={d} onAprobar={onAprobar} onRechazar={onRechazar} onVer={onVer} />)}
      </ul>
    )
  }

  return (
    <Card aria-labelledby="documentos-titulo" className="flex w-full max-w-2xl flex-col gap-4">
      <h2 id="documentos-titulo" className="break-words text-h3">Documentos</h2>
      <AvisoEstado aviso={exito} tono={exito?.tono} rol={exito?.tono === 'warning' ? 'alert' : 'status'} onCerrar={onCerrarExito} />
      {!error && documentos !== null && <ListaVerificacionDocumentos documentos={documentos} tieneFoto={tieneFoto} enlazarFoto rutaFoto={rutaFoto} estadoInscripcion={estadoInscripcion} documentosFaltantes={documentosFaltantes} />}
      <h3 className="break-words text-body-medium">Documentos cargados</h3>
      {lista}
      <section aria-labelledby="subir-documento-titulo" className="flex flex-col gap-3 border-t pt-4">
        <h3 id="subir-documento-titulo" className="break-words text-body-medium">Subir documento</h3>
        <FormularioDocumento key={claveFormulario} onSubir={onSubir} tipos={tiposParaSubir(documentos ?? [])} />
      </section>
    </Card>
  )
}

// rutaFoto: solo fuera de la ficha (pantalla Documentación): el enlace de la foto lleva a esa ruta, a la tarjeta de Foto.
export default function DocumentosDeportista({ idDeportista, actor, tieneFoto = false, rutaFoto }) {
  const porApi = documentosPorApi()
  const [intento, setIntento] = useState(0)
  // Resultado con el intento que lo produjo: si no coincide, está cargando (sin setState síncrono en el efecto)
  const [carga, setCarga] = useState({ intento: -1, items: null, estadoInscripcion: undefined, documentosFaltantes: [], error: false })
  const [exito, setExito] = useState(null)
  const [claveFormulario, setClaveFormulario] = useState(0)

  useEffect(() => {
    let activo = true
    listarDocumentos(idDeportista)
      .then((r) => activo && setCarga({ intento, items: r.documentos, estadoInscripcion: r.estadoInscripcion, documentosFaltantes: r.documentosFaltantes, error: false }))
      .catch((e) => activo && setCarga({ intento, items: null, estadoInscripcion: undefined, documentosFaltantes: [], error: mensajeDeError(e, true) }))
    return () => {
      activo = false
    }
  }, [idDeportista, intento])

  // Cambia un documento y el estado de la inscripción con lo que respondió el backend. Los faltantes salen de la lista actualizada
  // (la respuesta de aprobar y rechazar no los trae); si la respuesta dice COMPLETA no falta nada.
  const aplicarRevision = ({ documento, estadoInscripcion }) => setCarga((previa) => {
    const items = previa.items.map((x) => (String(x.id) === String(documento.id) ? documento : x))
    return { ...previa, items, estadoInscripcion, documentosFaltantes: estadoInscripcion === 'COMPLETA' ? [] : calcularInscripcion(items).documentosFaltantes }
  })

  async function subir(datos) {
    try {
      const nuevo = await subirDocumento(idDeportista, datos, actor)
      setCarga((previa) => ({ ...previa, items: [nuevo, ...(previa.items ?? [])] }))
      setClaveFormulario((n) => n + 1)
      setExito({ texto: 'Documento subido. Queda pendiente de revisión.' })
    } catch (e) {
      // 409: ya hay un documento activo de ese tipo (lo subió otra persona): se refresca la lista para que "Subir" lo oculte
      if (e.codigo === 'DOCUMENTO_ACTIVO') listarDocumentos(idDeportista).then((r) => setCarga((previa) => ({ ...previa, items: r.documentos, estadoInscripcion: r.estadoInscripcion, documentosFaltantes: r.documentosFaltantes }))).catch(() => {})
      throw e
    }
  }

  async function aprobar(documento) {
    aplicarRevision(await aprobarDocumento(documento.id, actor))
    setExito({ texto: 'Documento aprobado.' })
  }

  async function rechazar(documento, comentario) {
    const respuesta = await rechazarDocumento(documento.id, { comentario }, actor)
    aplicarRevision(respuesta)
    setExito(avisoDeRechazo(respuesta.avisoEnviado))
  }

  const cargando = carga.intento !== intento
  return (
    <PanelDocumentos
      documentos={cargando ? null : carga.items}
      estadoInscripcion={cargando ? undefined : carga.estadoInscripcion}
      documentosFaltantes={carga.documentosFaltantes}
      tieneFoto={tieneFoto}
      rutaFoto={rutaFoto}
      error={!cargando && carga.error}
      onReintentar={() => setIntento((n) => n + 1)}
      exito={exito}
      onCerrarExito={() => setExito(null)}
      onSubir={subir}
      onAprobar={aprobar}
      onRechazar={rechazar}
      onVer={porApi ? (documento) => abrirEnPestanaNueva(async () => (await verDocumento(documento.id, actor)).url) : undefined}
      claveFormulario={claveFormulario}
    />
  )
}
