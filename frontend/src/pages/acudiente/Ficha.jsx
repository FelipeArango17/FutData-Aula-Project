import { Navigate, useParams } from 'react-router-dom'
import Page from '../../components/ui/Page.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'

// Datos de ejemplo de la ficha (solo Santiago está en el diseño).
const fichas = {
  santiago: {
    nombre: 'Santiago Ríos', categoria: 'Semillero · Prado FC',
    datos: [
      ['Nombres', 'Santiago'], ['Apellidos', 'Ríos Zapata'], ['Documento de identidad', '1.023.456.789'],
      ['Fecha de nacimiento', '14 mar. 2016'], ['Categoría', 'Semillero'], ['Posición preferida', 'Volante'],
      ['EPS', 'Sura'], ['Talla de uniforme', 'M'],
    ],
    documentos: [
      ['Registro civil o T.I.', 'warning', 'Pendiente'], ['Ficha médica', 'warning', 'En revisión'],
      ['Autorización de padres', 'success', 'Aprobado'],
    ],
  },
}

export default function Ficha() {
  const { id } = useParams()
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
