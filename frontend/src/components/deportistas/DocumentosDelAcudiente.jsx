import Badge from '../ui/Badge.jsx'
import Card from '../ui/Card.jsx'
import FormularioDocumento from './FormularioDocumento.jsx'
import ListaVerificacionDocumentos from './ListaVerificacionDocumentos.jsx'
import { ETIQUETAS_ESTADO_DOCUMENTO, ETIQUETAS_TIPO_DOCUMENTO_JUGADOR } from '../../services/documentosService.js'
import { formatearTamano } from '../../utils/archivos.js'
import { fechaLargaConAnio, partesMomento } from '../../utils/fechas.js'

// Documentos de los jugadores del acudiente (parte visual). Por jugador: lista de verificación, documentos cargados con quién los
// subió ("Cargado por: Administrador" o "Acudiente") y el formulario para subir cualquiera de los 7 tipos. El acudiente solo sube:
// aprobar o rechazar es del administrador.
// props: jugadores [{ id, nombre, documentos, tieneFoto }], onSubir(idJugador, datos)
const TONO = { PENDIENTE: 'warning', APROBADO: 'success', RECHAZADO: 'danger' }

export default function DocumentosDelAcudiente({ jugadores, onSubir, claves = {} }) {
  return (
    <ul className="grid grid-cols-1 gap-4">
      {jugadores.map((j) => (
        <Card as="li" key={j.id} aria-labelledby={`jugador-${j.id}`} className="flex min-w-0 flex-col gap-4">
          <h2 id={`jugador-${j.id}`} className="break-words text-h3">{j.nombre}</h2>
          <ListaVerificacionDocumentos documentos={j.documentos} tieneFoto={j.tieneFoto} />
          <section aria-label={`Documentos cargados de ${j.nombre}`} className="flex flex-col gap-3">
            <h3 className="break-words text-body-medium">Documentos cargados</h3>
            {j.documentos.length === 0 ? (
              <p className="break-words text-body text-secondary">Todavía no hay documentos cargados.</p>
            ) : (
              <ul className="grid grid-cols-1 gap-3">
                {j.documentos.map((d) => (
                  <li key={d.id} className="flex min-w-0 flex-col gap-1 rounded-md border bg-app p-3">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <p className="min-w-0 break-words text-body-medium">{ETIQUETAS_TIPO_DOCUMENTO_JUGADOR[d.tipo]}</p>
                      <Badge tone={TONO[d.estado]}>{ETIQUETAS_ESTADO_DOCUMENTO[d.estado]}</Badge>
                    </div>
                    <p className="break-words text-small text-secondary">{d.nombreOriginal} · {formatearTamano(d.tamanoBytes)}</p>
                    <p className="break-words text-small text-secondary">
                      Cargado el {fechaLargaConAnio(partesMomento(d.cargadoEn).fecha)} · Cargado por: {d.cargadoPor}
                    </p>
                    {d.estado === 'RECHAZADO' && d.comentario && <p className="break-words text-small text-danger">Comentario del administrador: {d.comentario}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section aria-label={`Subir un documento de ${j.nombre}`} className="flex flex-col gap-3 border-t pt-4">
            <h3 className="break-words text-body-medium">Subir documento</h3>
            <FormularioDocumento key={claves[j.id] ?? 0} onSubir={(datos) => onSubir(j.id, datos)} />
          </section>
        </Card>
      ))}
    </ul>
  )
}
