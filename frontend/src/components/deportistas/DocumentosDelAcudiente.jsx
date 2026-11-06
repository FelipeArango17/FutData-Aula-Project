import { useState } from 'react'
import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import FormularioDocumento from './FormularioDocumento.jsx'
import ListaVerificacionDocumentos from './ListaVerificacionDocumentos.jsx'
import { ETIQUETAS_ESTADO_DOCUMENTO, ETIQUETAS_TIPO_DOCUMENTO_JUGADOR } from '../../services/documentosService.js'
import { formatearTamano } from '../../utils/archivos.js'
import { mensajeDeError } from '../../utils/documentosApi.js'
import { TIPOS_DOCUMENTO_CLUB, tiposParaSubir } from '../../utils/documentosClub.js'
import { fechaLargaConAnio, partesMomento } from '../../utils/fechas.js'

// Documentos de los jugadores del acudiente (parte visual). Por jugador: lista de verificación, documentos cargados con quién los
// subió ("Cargado por: Administrador" o "Acudiente") y el formulario para subir cualquiera de los 7 tipos. El acudiente solo sube:
// aprobar o rechazar es del administrador.
// Un solo documento activo por tipo: "Subir" solo ofrece los tipos sin documento activo; si uno fue rechazado, se vuelve a subir.
// Muestra estadoInscripcion y documentosFaltantes (solo los 4 obligatorios cuentan).
// props: jugadores [{ id, nombre, documentos, estadoInscripcion, documentosFaltantes, tieneFoto }], onSubir(idJugador, datos),
//        onVer(documento) opcional (con la API: abre la URL firmada en otra pestaña; sin él no hay botón)
const TONO = { PENDIENTE: 'warning', APROBADO: 'success', RECHAZADO: 'danger' }

function FilaDocumento({ documento: d, onVer }) {
  const [viendo, setViendo] = useState(false)
  const [error, setError] = useState(null)
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
  return (
    <li className="flex min-w-0 flex-col gap-1 rounded-md border bg-app p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="min-w-0 break-words text-body-medium">{ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo]} <span className="text-small text-secondary">· {obligatorio ? 'Obligatorio' : 'Si aplica'}</span></p>
        <Badge tone={TONO[d.estado]}>{ETIQUETAS_ESTADO_DOCUMENTO[d.estado]}</Badge>
      </div>
      <p className="break-words text-small text-secondary">{d.nombreOriginal} · {formatearTamano(d.tamanoBytes)}</p>
      <p className="break-words text-small text-secondary">
        Cargado el {fechaLargaConAnio(partesMomento(d.cargadoEn).fecha)} · Cargado por: {d.cargadoPor}
      </p>
      {d.estado === 'RECHAZADO' && d.comentario && <p className="break-words text-small text-danger">Comentario del administrador: {d.comentario}</p>}
      {d.estado === 'RECHAZADO' && <p className="break-words text-small text-secondary">Fue rechazado: puedes subir uno nuevo de este tipo.</p>}
      {onVer && (
        <div className="pt-1">
          <Button variant="secondary" onClick={ver} disabled={viendo} aria-busy={viendo} aria-label={`Ver documento: ${ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo].toLowerCase()}, ${d.nombreOriginal}`}>
            {viendo ? 'Abriendo…' : 'Ver documento'}
          </Button>
        </div>
      )}
      <div role="alert">{error && <p className="break-words text-small text-danger">{error}</p>}</div>
    </li>
  )
}

export default function DocumentosDelAcudiente({ jugadores, onSubir, onVer, claves = {} }) {
  return (
    <ul className="grid grid-cols-1 gap-4">
      {jugadores.map((j) => (
        <Card as="li" key={j.id} aria-labelledby={`jugador-${j.id}`} className="flex min-w-0 flex-col gap-4">
          <h2 id={`jugador-${j.id}`} className="break-words text-h3">{j.nombre}</h2>
          <ListaVerificacionDocumentos documentos={j.documentos} tieneFoto={j.tieneFoto} estadoInscripcion={j.estadoInscripcion} documentosFaltantes={j.documentosFaltantes} />
          <section aria-label={`Documentos cargados de ${j.nombre}`} className="flex flex-col gap-3">
            <h3 className="break-words text-body-medium">Documentos cargados</h3>
            {j.documentos.length === 0 ? (
              <p className="break-words text-body text-secondary">Todavía no hay documentos cargados.</p>
            ) : (
              <ul className="grid grid-cols-1 gap-3">
                {j.documentos.map((d) => <FilaDocumento key={d.id} documento={d} onVer={onVer} />)}
              </ul>
            )}
          </section>
          <section aria-label={`Subir un documento de ${j.nombre}`} className="flex flex-col gap-3 border-t pt-4">
            <h3 className="break-words text-body-medium">Subir documento</h3>
            <FormularioDocumento key={claves[j.id] ?? 0} onSubir={(datos) => onSubir(j.id, datos)} tipos={tiposParaSubir(j.documentos)} />
          </section>
        </Card>
      ))}
    </ul>
  )
}
