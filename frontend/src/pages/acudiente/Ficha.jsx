import { useCallback } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Avatar from '../../components/ui/Avatar.jsx'
import EstadoDeCarga from '../../components/pagos/EstadoDeCarga.jsx'
import useCarga from '../../hooks/useCarga.js'
import { acudientesPorApi, obtenerMiJugador } from '../../services/acudientesService.js'
import { textoCategoriaConGrupo } from '../../utils/categorias.js'
import { textoEdad } from '../../utils/entrenadorApi.js'
import {
  ETIQUETA_INSCRIPCION, TONO_INSCRIPCION, esErrorSinReintento, fechaNacimientoLegible, mensajeDeErrorAcudiente, nombreCompleto,
} from '../../utils/acudientesApi.js'

// Datos de ejemplo de la ficha (solo Santiago está en el diseño).
const fichas = {
  santiago: {
    nombre: 'Santiago Ríos', categoria: 'Cachorros · Prado FC',
    datos: [
      ['Nombres', 'Santiago'], ['Apellidos', 'Ríos Zapata'], ['Documento de identidad', '1.023.456.789'],
      ['Fecha de nacimiento', '14 mar. 2018'], ['Categoría', 'Cachorros'], ['Grupo de entrenamiento', 'Mañana'], ['Posición', 'Delantero'],
      ['EPS', 'Sura'], ['Talla de uniforme', 'M'],
    ],
    documentos: [
      ['Documento de identidad', 'success', 'Aprobado'], ['Certificado de estudio', 'warning', 'Pendiente'],
      ['Formulario de inscripción', 'success', 'Aprobado'],
      ['Habeas Data', 'neutral', 'Sin cargar'],
    ],
  },
}

// Ficha con la API (módulo "acudientes"): solo lectura, con lo que entrega el servidor. Los datos que vienen vacíos se ocultan; EPS,
// talla de uniforme, asistencia y pagos no vienen en esta respuesta y no se muestran.
const datosDe = (j) => [
  ['Nombres', j.nombre], ['Apellidos', j.apellido], ['Tipo de documento', j.tipoDocumento], ['Documento de identidad', j.documento],
  ['Fecha de nacimiento', fechaNacimientoLegible(j.fechaNacimiento)], ['Edad', j.edad === null ? null : textoEdad(j.edad)],
  ['Categoría', j.categoria], ['Grupo de entrenamiento', j.categoria ? j.grupoEntrenamiento : null], ['Posición', j.posicion],
  ['Teléfono', j.telefono], ['Dirección', j.direccion], ['Parentesco contigo', j.parentesco],
].filter(([, valor]) => valor)

function ContenidoFicha({ jugador }) {
  const faltantes = jugador.documentosFaltantes
  return (
    <>
      <div className="flex flex-wrap gap-2">
        <Badge tone={TONO_INSCRIPCION[jugador.estadoInscripcion]}>Inscripción: {ETIQUETA_INSCRIPCION[jugador.estadoInscripcion]}</Badge>
        {!jugador.activo && <Badge>Inactivo</Badge>}
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card aria-labelledby="datos-titulo">
          <div className="mb-3 flex items-center gap-3">
            <Avatar name={nombreCompleto(jugador)} foto={jugador.foto} className="h-12 w-12" />
            <h2 id="datos-titulo" className="text-h3">Datos del jugador</h2>
          </div>
          <dl className="flex flex-col gap-3">
            {datosDe(jugador).map(([etiqueta, valor]) => (
              <div key={etiqueta}>
                <dt className="text-small text-secondary">{etiqueta}</dt>
                <dd className="break-words text-body-medium">{valor}</dd>
              </div>
            ))}
          </dl>
        </Card>
        <Card aria-labelledby="docs-titulo" className="flex flex-col gap-3">
          <h2 id="docs-titulo" className="text-h3">Documentos que faltan</h2>
          {faltantes.length === 0 ? (
            <p className="break-words text-body text-secondary">No falta ningún documento obligatorio. La inscripción está completa.</p>
          ) : (
            <>
              <p className="break-words text-body text-secondary">Estos documentos obligatorios aún no están aprobados. Súbelos en Documentos.</p>
              <ul aria-label="Documentos que faltan" className="flex flex-col gap-2">
                {faltantes.map((d) => (
                  <li key={d.tipo}>
                    <Link to="/acudiente/documentos" className="break-words text-body-medium underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">{d.nombre}</Link>
                  </li>
                ))}
              </ul>
            </>
          )}
          <Button to="/acudiente/documentos">Ir a Documentos</Button>
        </Card>
      </div>
      <p className="text-body text-secondary">Para corregir un dato de la ficha, solicítalo en secretaría.</p>
    </>
  )
}

// Vista pura (sin red): recibe el estado de la carga. La usan el contenedor de abajo y las pruebas.
export function VistaFicha({ cargando, jugador, error, onReintentar }) {
  const titulo = jugador ? nombreCompleto(jugador) : 'Ficha del jugador'
  const subtitulo = jugador?.categoria ? textoCategoriaConGrupo(jugador.categoria, jugador.grupoEntrenamiento) : undefined
  return (
    <Page title={titulo} subtitle={subtitulo}>
      <EstadoDeCarga
        cargando={cargando}
        mensajeError={error ? mensajeDeErrorAcudiente(error, 'No se pudo cargar la ficha del jugador.') : null}
        onReintentar={error && !esErrorSinReintento(error) ? onReintentar : undefined}
        texto="Cargando la ficha…"
      >
        {jugador && <ContenidoFicha jugador={jugador} />}
      </EstadoDeCarga>
      {error && <Button variant="secondary" to="/acudiente/jugadores" className="sm:self-start">Volver a Mis jugadores</Button>}
    </Page>
  )
}

function FichaApi({ id }) {
  const cargar = useCallback(() => obtenerMiJugador(id), [id])
  const { cargando, datos, error, recargar } = useCarga(cargar)
  return <VistaFicha cargando={cargando} jugador={datos} error={error} onReintentar={recargar} />
}

function FichaDemo({ id }) {
  const ficha = fichas[id]
  if (!ficha) return <Navigate to="/acudiente/jugadores" replace />

  return (
    <Page title={ficha.nombre} subtitle={ficha.categoria}>
      <div className="flex flex-wrap gap-2">
        <Badge tone="success">Inscripción aprobada</Badge>
        <Badge tone="warning">Faltan documentos</Badge>
      </div>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card aria-labelledby="datos-titulo">
          <h2 id="datos-titulo" className="mb-3 text-h3">Datos del jugador</h2>
          <dl className="flex flex-col gap-3">
            {ficha.datos.map(([etiqueta, valor]) => (
              <div key={etiqueta}>
                <dt className="text-small text-secondary">{etiqueta}</dt>
                <dd className="break-words text-body-medium">{valor}</dd>
              </div>
            ))}
          </dl>
        </Card>
        <Card aria-labelledby="docs-titulo" className="flex flex-col gap-3">
          <h2 id="docs-titulo" className="text-h3">Documentos</h2>
          <ul className="flex flex-col gap-3">
            {ficha.documentos.map(([nombre, tone, estado]) => (
              <li key={nombre} className="flex flex-wrap items-center justify-between gap-2">
                <span className="break-words text-body-medium">{nombre}</span>
                <Badge tone={tone}>{estado}</Badge>
              </li>
            ))}
          </ul>
          <Button to="/acudiente/documentos">Ir a Documentos</Button>
        </Card>
      </div>
      <p className="text-body text-secondary">Para corregir un dato de la ficha, solicítalo en secretaría.</p>
    </Page>
  )
}

export default function Ficha() {
  const { id } = useParams()
  return acudientesPorApi() ? <FichaApi id={id} /> : <FichaDemo id={id} />
}
