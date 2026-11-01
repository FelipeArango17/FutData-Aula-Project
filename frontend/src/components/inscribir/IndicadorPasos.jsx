import { PASOS } from '../../services/asistenteInscripcion.js'

// Indicador de pasos del asistente: lista ordenada, el paso actual con aria-current="step" y el texto "Paso X de 4" para lectores de pantalla.
// Con letra grande los nombres se parten en vez de salirse (sin scroll horizontal): la cuadrícula baja a 2 columnas en pantallas angostas.
export default function IndicadorPasos({ paso }) {
  return (
    <nav aria-label="Pasos de la inscripción">
      <ol className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {PASOS.map((nombre, i) => {
          const actual = i + 1 === paso
          const hecho = i + 1 < paso
          return (
            <li
              key={nombre}
              aria-current={actual ? 'step' : undefined}
              className={`flex min-w-0 flex-col items-center gap-1 rounded-md border p-2 text-center text-small ${
                actual ? 'border-transparent bg-brand-primary text-on-dark' : hecho ? 'bg-surface text-primary' : 'bg-surface text-secondary'
              }`}
            >
              <span className="text-body-medium">{i + 1}</span>
              <span className="break-words">{nombre}</span>
              <span className="sr-only">{actual ? ' (paso actual)' : hecho ? ' (completado)' : ''}</span>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
