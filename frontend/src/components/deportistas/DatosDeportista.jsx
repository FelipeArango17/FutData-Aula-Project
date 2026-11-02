import EstadoDeportista from './EstadoDeportista.jsx'
import { TEXTO_SIN_POSICION, edadDe } from '../../services/deportistasService.js'
import { ETIQUETA_GRUPO, categoriaRequiereGrupo } from '../../utils/categorias.js'
import { fechaLargaConAnio } from '../../utils/fechas.js'
import { ETIQUETA_ESTADO_INSCRIPCION } from '../../utils/deportistasApi.js'

// Datos personales de la ficha del deportista (solo lectura). `categoria` es el NOMBRE de la categoría (o null si no tiene):
// se muestra solo el nombre, sin textos de años de nacimiento. El grupo de entrenamiento solo aparece en Cachorros y Semillero;
// el club decidió que el grupo no se guarda en la base de datos (vive en el servicio de ejemplo). Sin posición: "Sin definir".
function Dato({ etiqueta, children }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="text-small text-secondary">{etiqueta}</dt>
      <dd className="break-words text-body">{children}</dd>
    </div>
  )
}

export default function DatosDeportista({ deportista, categoria }) {
  const edad = edadDe(deportista)
  return (
    <dl className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,14rem),1fr))] gap-4">
      <Dato etiqueta="Tipo de documento">{deportista.tipoDocumento}</Dato>
      <Dato etiqueta="Número de documento">{deportista.documento}</Dato>
      <Dato etiqueta="Fecha de nacimiento">{fechaLargaConAnio(deportista.fechaNacimiento)}</Dato>
      <Dato etiqueta="Edad">{edad} {edad === 1 ? 'año' : 'años'}</Dato>
      <Dato etiqueta="Teléfono de contacto">{deportista.telefono}</Dato>
      <Dato etiqueta="Dirección">{deportista.direccion}</Dato>
      <Dato etiqueta="Categoría">{categoria ?? 'Sin asignar'}</Dato>
      {categoriaRequiereGrupo(categoria) && <Dato etiqueta={ETIQUETA_GRUPO}>{deportista.grupoEntrenamiento ?? 'Sin definir'}</Dato>}
      <Dato etiqueta="Posición">{deportista.posicion ?? TEXTO_SIN_POSICION}</Dato>
      <Dato etiqueta="Estado"><EstadoDeportista activo={deportista.activo} /></Dato>
      {/* Solo el administrador lo recibe de la API (se calcula con los 4 documentos obligatorios) */}
      {deportista.estadoInscripcion && <Dato etiqueta="Inscripción">{ETIQUETA_ESTADO_INSCRIPCION[deportista.estadoInscripcion] ?? deportista.estadoInscripcion}</Dato>}
      {/* Solo los inscritos en línea traen origen y contacto del acudiente (los registros del administrador no) */}
      {deportista.origen && <Dato etiqueta="Origen">{deportista.origen}</Dato>}
      {deportista.contactoAcudiente && (
        <Dato etiqueta="Contacto del acudiente">
          {deportista.contactoAcudiente.nombre} {deportista.contactoAcudiente.apellido}
          <span className="block">{deportista.contactoAcudiente.correo}</span>
          <span className="block">{deportista.contactoAcudiente.telefono}</span>
        </Dato>
      )}
    </dl>
  )
}
