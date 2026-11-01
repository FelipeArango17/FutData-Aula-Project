import Button from '../ui/Button.jsx'
import Card from '../ui/Card.jsx'
import { ETIQUETA_GRUPO, categoriaPorFecha, categoriaRequiereGrupo } from '../../utils/categorias.js'
import { TEXTO_SIN_POSICION, calcularEdad } from '../../services/deportistasService.js'
import { TIPOS_DOCUMENTO_CLUB } from '../../utils/documentosClub.js'
import { formatearTamano } from '../../utils/archivos.js'
import { fechaLargaConAnio } from '../../utils/fechas.js'

// Paso 4: resumen de todo lo escrito, con un enlace "Editar" por sección que vuelve a ese paso (los datos se conservan).
// props: jugador, acudiente, documentos, foto, onEditar(paso)
function Dato({ etiqueta, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-small text-secondary">{etiqueta}</dt>
      <dd className="break-words text-body">{children}</dd>
    </div>
  )
}

function Seccion({ id, titulo, paso, onEditar, children }) {
  return (
    <Card aria-labelledby={id} className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id={id} className="min-w-0 break-words text-h3">{titulo}</h3>
        <Button variant="secondary" onClick={() => onEditar(paso)} aria-label={`Editar ${titulo.toLowerCase()}`}>Editar</Button>
      </div>
      {children}
    </Card>
  )
}

const rejilla = 'grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4'

export default function PasoConfirmar({ jugador, acudiente, documentos, foto, onEditar }) {
  const categoria = categoriaPorFecha(jugador.fechaNacimiento)
  const edad = calcularEdad(jugador.fechaNacimiento)
  const adjuntos = TIPOS_DOCUMENTO_CLUB.filter((t) => documentos[t.id])
  return (
    <>
      <Seccion id="resumen-jugador" titulo="Jugador" paso={1} onEditar={onEditar}>
        <dl className={rejilla}>
          <Dato etiqueta="Nombre">{jugador.nombre} {jugador.apellido}</Dato>
          <Dato etiqueta="Documento">{jugador.tipoDocumento} {jugador.documento}</Dato>
          <Dato etiqueta="Fecha de nacimiento">{fechaLargaConAnio(jugador.fechaNacimiento)} ({edad} {edad === 1 ? 'año' : 'años'})</Dato>
          <Dato etiqueta="Categoría">{categoria ?? 'Sin asignar'}</Dato>
          {categoriaRequiereGrupo(categoria) && <Dato etiqueta={ETIQUETA_GRUPO}>{jugador.grupoEntrenamiento}</Dato>}
          <Dato etiqueta="Posición">{jugador.posicion || TEXTO_SIN_POSICION}</Dato>
          <Dato etiqueta="Teléfono de contacto">{jugador.telefono}</Dato>
          <Dato etiqueta="Dirección">{jugador.direccion}</Dato>
        </dl>
      </Seccion>
      <Seccion id="resumen-acudiente" titulo="Acudiente" paso={2} onEditar={onEditar}>
        <dl className={rejilla}>
          <Dato etiqueta="Nombre">{acudiente.nombre} {acudiente.apellido}</Dato>
          <Dato etiqueta="Parentesco">{acudiente.parentesco}</Dato>
          <Dato etiqueta="Correo electrónico">{acudiente.correo}</Dato>
          <Dato etiqueta="Teléfono">{acudiente.telefono}</Dato>
        </dl>
      </Seccion>
      <Seccion id="resumen-documentos" titulo="Documentos" paso={3} onEditar={onEditar}>
        <ul aria-label="Documentos adjuntos" className="flex flex-col gap-2">
          <li className="break-words text-body">Foto tipo documento: {foto ? `${foto.name} · ${formatearTamano(foto.size)}` : 'Sin adjuntar'}</li>
          {adjuntos.map((t) => (
            <li key={t.id} className="break-words text-body">{t.nombre}: {documentos[t.id].name} · {formatearTamano(documentos[t.id].size)}</li>
          ))}
        </ul>
        {adjuntos.length === 0 && <p className="break-words text-small text-secondary">No adjuntaste documentos. Puedes subirlos después desde la ficha del deportista.</p>}
      </Seccion>
    </>
  )
}
