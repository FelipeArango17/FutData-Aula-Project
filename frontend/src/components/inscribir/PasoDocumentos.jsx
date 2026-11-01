import Badge from '../ui/Badge.jsx'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import SubidaFoto from '../ui/SubidaFoto.jsx'
import { TIPOS_DOCUMENTO_CLUB } from '../../utils/documentosClub.js'
import { formatearTamano } from '../../utils/archivos.js'
import { CLAVE_FOTO } from '../../services/asistenteInscripcion.js'

// Paso 3 (opcional): adjuntar documentos y la foto. Nada se sube todavía: los archivos quedan en el asistente y se registran al
// confirmar. Los 7 tipos con la marca Obligatorio / Si aplica; la "Foto tipo documento" es la foto de perfil (con vista previa).
// props: documentos ({ idTipo: File }), foto (File | null), fotoPrevia (URL de la vista previa), nombreJugador, errores,
//        onDocumento(idTipo, archivo), onFoto(archivo)
export default function PasoDocumentos({ documentos, foto, fotoPrevia = null, nombreJugador, errores, onDocumento, onFoto }) {
  return (
    <>
      <p className="break-words text-body">
        Puedes subirlos ahora o después: el administrador o el acudiente pueden hacerlo desde la ficha del deportista.
      </p>

      <section aria-labelledby="foto-titulo" className="flex min-w-0 flex-col gap-3 rounded-lg border bg-surface p-3 sm:p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id="foto-titulo" className="min-w-0 break-words text-body-medium">Foto tipo documento</h3>
          <Badge tone="neutral">Obligatorio</Badge>
        </div>
        <p className="break-words text-small text-secondary">Es la foto de perfil del jugador. JPG o PNG, máximo 5 MB.</p>
        <SubidaFoto
          nombre={nombreJugador || 'el jugador'}
          foto={foto ? fotoPrevia : null}
          guardarAlElegir
          onGuardar={async (archivo) => onFoto(archivo)}
          onQuitar={async () => onFoto(null)}
        />
        {foto && <p role="status" className="break-words text-body">Foto adjunta: {foto.name}</p>}
        {errores[CLAVE_FOTO] && <p role="alert" className="break-words text-small text-danger">{errores[CLAVE_FOTO]}</p>}
      </section>

      <ul aria-label="Documentos del jugador" className="grid grid-cols-1 gap-3">
        {TIPOS_DOCUMENTO_CLUB.map((t) => {
          const archivo = documentos[t.id]
          return (
            <li key={t.id} className="flex min-w-0 flex-col gap-2 rounded-lg border bg-surface p-3 sm:p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="min-w-0 break-words text-body-medium">{t.nombre}</h3>
                <Badge tone={archivo ? 'success' : 'neutral'}>{t.obligatorio ? 'Obligatorio' : 'Si aplica'}</Badge>
              </div>
              <p className="break-words text-small text-secondary">{t.descripcion}</p>
              {archivo ? (
                <div className="flex flex-wrap items-center gap-2">
                  <p className="min-w-0 break-words text-body">
                    {archivo.name} <span className="text-secondary">· {formatearTamano(archivo.size)}</span>
                  </p>
                  <Button variant="secondary" onClick={() => onDocumento(t.id, null)} aria-label={`Quitar ${t.nombre.toLowerCase()}: ${archivo.name}`}>Quitar</Button>
                </div>
              ) : (
                <Field
                  label="Archivo (PDF, JPG o PNG, máximo 5 MB)" id={`documento-${t.id}`} type="file"
                  accept=".pdf,application/pdf,image/jpeg,image/png"
                  error={errores[`documento-${t.id}`]} onChange={(e) => onDocumento(t.id, e.target.files?.[0] ?? null)}
                />
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}
