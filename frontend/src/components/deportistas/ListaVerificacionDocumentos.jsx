import Badge from '../ui/Badge.jsx'
import { listaDeVerificacion, resumenObligatorios } from '../../utils/documentosClub.js'

// Lista de verificación de los requisitos del club: cada tipo de documento con su estado (Sin cargar / Pendiente / Aprobado /
// Rechazado) y la marca "Obligatorio" o "Si aplica", con un resumen arriba. La fila "Foto tipo documento" no es un documento:
// su estado sale de si el deportista tiene foto de perfil (tieneFoto); sin foto enlaza a la tarjeta de Foto de la misma ficha.
// Solo lee: recibe los documentos ya cargados.
const TONO = { PENDIENTE: 'warning', APROBADO: 'success', RECHAZADO: 'danger' }

export const ID_TARJETA_FOTO = 'foto-deportista' // id del título de la tarjeta de Foto en la ficha

export default function ListaVerificacionDocumentos({ documentos, tieneFoto = false }) {
  const resumen = resumenObligatorios(documentos, { tieneFoto })
  return (
    <section aria-labelledby="verificacion-titulo" className="flex flex-col gap-3">
      <h3 id="verificacion-titulo" className="break-words text-body-medium">Lista de verificación</h3>
      <p role="status" className="break-words text-body">{resumen.texto}</p>
      <ul aria-label="Lista de verificación de documentos" className="grid grid-cols-1 gap-2">
        {listaDeVerificacion(documentos, { tieneFoto }).map((fila) => (
          <li key={fila.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border bg-app p-3">
            <div className="min-w-0">
              <p className="break-words text-body-medium">{fila.nombre}</p>
              <p className="break-words text-small text-secondary">{fila.marca}</p>
              {fila.esFoto && !tieneFoto && (
                <a href={`#${ID_TARJETA_FOTO}`} className="break-words text-small underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">Ir a la tarjeta de Foto</a>
              )}
            </div>
            <Badge tone={fila.esFoto ? (tieneFoto ? 'success' : 'neutral') : TONO[fila.estado] ?? 'neutral'}>{fila.etiquetaEstado}</Badge>
          </li>
        ))}
      </ul>
    </section>
  )
}
