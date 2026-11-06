import { useId } from 'react'
import { Link } from 'react-router-dom'
import Badge from '../ui/Badge.jsx'
import { estadoFoto, listaDeVerificacion, resumenObligatorios, textoFaltantes } from '../../utils/documentosClub.js'
import { ETIQUETA_ESTADO_INSCRIPCION_DOCUMENTOS } from '../../utils/documentosApi.js'

// Lista de verificación de los requisitos del club: cada tipo de documento con su estado (Sin cargar / Pendiente / Aprobado /
// Rechazado) y la marca "Obligatorio" o "Si aplica", con un resumen "x de 4" arriba (los 4 obligatorios, como estadoInscripcion). La
// "Foto tipo documento" no es un documento ni cuenta para la inscripción: va aparte como dato informativo (tieneFoto); Cuando falta la foto:
// `rutaFoto` (administrador, fuera de la ficha) enlaza con el router a la ficha, ya en la tarjeta de Foto; `enlazarFoto` (dentro de la ficha)
// usa el ancla de la propia página; `notaFoto` (acudiente, que no puede subirla) solo muestra una línea informativa, sin enlace.
// Los 3 "Si aplica" se pueden subir y revisar, pero NO cuentan para la inscripción: estadoInscripcion y documentosFaltantes miran solo los 4
// obligatorios. Si recibe `estadoInscripcion` (y `documentosFaltantes`) los muestra: "Inscripción: Pendiente · Faltan: Certificado de estudio".
// Solo lee: recibe los documentos ya cargados.
const TONO = { PENDIENTE: 'warning', APROBADO: 'success', RECHAZADO: 'danger' }

export const ID_TARJETA_FOTO = 'foto-deportista' // id del título de la tarjeta de Foto en la ficha

export default function ListaVerificacionDocumentos({ documentos, tieneFoto = false, enlazarFoto = false, rutaFoto, notaFoto, estadoInscripcion, documentosFaltantes = [] }) {
  const tituloId = useId()
  const foto = estadoFoto(tieneFoto)
  const resumen = resumenObligatorios(documentos)
  const faltan = textoFaltantes(documentosFaltantes)
  return (
    <section aria-labelledby={tituloId} className="flex flex-col gap-3">
      <h3 id={tituloId} className="break-words text-body-medium">Lista de verificación</h3>
      <p role="status" className="break-words text-body">{resumen.texto}</p>
      {estadoInscripcion && (
        <p className="break-words text-body-medium">
          Inscripción: {ETIQUETA_ESTADO_INSCRIPCION_DOCUMENTOS[estadoInscripcion] ?? estadoInscripcion}
          {faltan && <span className="text-body text-secondary"> · {faltan}</span>}
        </p>
      )}
      <p className="break-words text-small text-secondary">Los documentos marcados «Si aplica» no cuentan para completar la inscripción: solo los 4 obligatorios.</p>
      <ul aria-label="Lista de verificación de documentos" className="grid grid-cols-1 gap-2">
        {listaDeVerificacion(documentos).map((fila) => (
          <li key={fila.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border bg-app p-3">
            <div className="min-w-0">
              <p className="break-words text-body-medium">{fila.nombre}</p>
              <p className="break-words text-small text-secondary">{fila.marca}{!fila.obligatorio && ' · no cuenta para completar la inscripción'}</p>
            </div>
            <Badge tone={TONO[fila.estado] ?? 'neutral'}>{fila.etiquetaEstado}</Badge>
          </li>
        ))}
      </ul>
      <div aria-label="Foto del deportista" role="group" className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-dashed bg-app p-3">
        <div className="min-w-0">
          <p className="break-words text-body-medium">{foto.nombre}</p>
          <p className="break-words text-small text-secondary">Informativo · no cuenta para la inscripción</p>
          {notaFoto && <p className="break-words text-small text-secondary">{notaFoto}</p>}
          {rutaFoto && !foto.cargada && (
            <Link to={`${rutaFoto}#${ID_TARJETA_FOTO}`} className="break-words text-small underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">Ir a la tarjeta de Foto</Link>
          )}
          {!rutaFoto && enlazarFoto && !foto.cargada && (
            <a href={`#${ID_TARJETA_FOTO}`} className="break-words text-small underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">Ir a la tarjeta de Foto</a>
          )}
        </div>
        <Badge tone={foto.cargada ? 'success' : 'neutral'}>{foto.etiquetaEstado}</Badge>
      </div>
    </section>
  )
}
