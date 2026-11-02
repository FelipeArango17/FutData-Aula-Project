import Card from '../ui/Card.jsx'
import Button from '../ui/Button.jsx'
import Avatar from '../ui/Avatar.jsx'
import EstadoDeportista from './EstadoDeportista.jsx'
import { TEXTO_SIN_POSICION, edadDe } from '../../services/deportistasService.js'
import { traeDocumento } from '../../utils/deportistasApi.js'
import { textoCategoriaConGrupo } from '../../utils/categorias.js'

// Tabla (escritorio) y tarjetas (móvil) de la lista de deportistas. Solo leen: reciben los deportistas ya filtrados y paginados.
// La categoría se muestra con su grupo de entrenamiento cuando lo tiene ("Semillero · mañana"); el grupo no se guarda en la base
// de datos (solo vive en el servicio de ejemplo). Sin posición se muestra "Sin definir".
// props: deportistas, fotos (id -> url), nombresCategoria (id -> nombre), actualizando
const nombreCompleto = (d) => `${d.nombre} ${d.apellido}`
const textoEdad = (d) => {
  const edad = edadDe(d)
  return `${edad} ${edad === 1 ? 'año' : 'años'}`
}

function categoriaDe(d, nombresCategoria) {
  if (d.idCategoria === null) return 'Sin asignar'
  const nombre = nombresCategoria[d.idCategoria]
  return nombre ? textoCategoriaConGrupo(nombre, d.grupoEntrenamiento) : 'No disponible'
}

export function TablaDeportistas({ deportistas, fotos = {}, nombresCategoria, actualizando = false }) {
  const conDocumento = traeDocumento(deportistas) // el backend no manda el documento a todos los roles
  return (
    <table aria-busy={actualizando} className="w-full table-fixed border-collapse rounded-lg border bg-surface text-left text-body">
      <caption className="sr-only">Deportistas registrados</caption>
      <thead>
        <tr className="border-b bg-app">
          <th scope="col" className="p-3 text-body-medium">Deportista</th>
          <th scope="col" className="w-[5.5rem] p-3 text-body-medium">Edad</th>
          {conDocumento && <th scope="col" className="w-[8rem] p-3 text-body-medium">Documento</th>}
          <th scope="col" className="w-[8.5rem] p-3 text-body-medium">Categoría</th>
          <th scope="col" className="w-[7rem] p-3 text-body-medium">Posición</th>
          <th scope="col" className="w-[7rem] p-3 text-body-medium">Estado</th>
          <th scope="col" className="w-[7.5rem] p-3 text-body-medium"><span className="sr-only">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        {deportistas.map((d) => (
          <tr key={d.id} className="border-b last:border-b-0">
            <th scope="row" className="p-3 font-normal">
              <div className="flex min-w-0 items-center gap-3">
                <Avatar name={nombreCompleto(d)} foto={fotos[d.id]} />
                <span className="min-w-0 break-words text-body-medium">{nombreCompleto(d)}</span>
              </div>
            </th>
            <td className="p-3">{textoEdad(d)}</td>
            {conDocumento && <td className="break-words p-3">{d.documento}</td>}
            <td className="break-words p-3">{categoriaDe(d, nombresCategoria)}</td>
            <td className="break-words p-3">{d.posicion ?? TEXTO_SIN_POSICION}</td>
            <td className="p-3"><EstadoDeportista activo={d.activo} /></td>
            <td className="p-3">
              <Button to={`/administrador/deportistas/${d.id}`} variant="secondary" aria-label={`Ver ficha de ${nombreCompleto(d)}`}>Ver ficha</Button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function TarjetasDeportistas({ deportistas, fotos = {}, nombresCategoria, actualizando = false }) {
  return (
    <ul aria-busy={actualizando} aria-label="Deportistas registrados" className="grid grid-cols-1 gap-4">
      {deportistas.map((d) => (
        <Card as="li" key={d.id} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-start gap-3">
            <Avatar name={nombreCompleto(d)} foto={fotos[d.id]} />
            <div className="flex min-w-0 grow basis-[12rem] flex-col gap-1">
              <h2 className="break-words text-h3">{nombreCompleto(d)}</h2>
              <p className="break-words text-small text-secondary">{textoEdad(d)}{d.documento !== undefined && ` · Documento: ${d.documento}`}</p>
              <p className="break-words text-small text-secondary">Categoría: {categoriaDe(d, nombresCategoria)}</p>
              <p className="break-words text-small text-secondary">Posición: {d.posicion ?? TEXTO_SIN_POSICION}</p>
              <div className="flex flex-wrap pt-1"><EstadoDeportista activo={d.activo} /></div>
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button to={`/administrador/deportistas/${d.id}`} variant="secondary" aria-label={`Ver ficha de ${nombreCompleto(d)}`}>Ver ficha</Button>
          </div>
        </Card>
      ))}
    </ul>
  )
}
