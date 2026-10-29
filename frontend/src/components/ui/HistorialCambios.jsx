// Historial de cambios genérico (lo usan la asistencia y la ficha del deportista).
// entradas: [{ clave, encabezado, lineas: [{ clave, texto }] }] ya en el orden en que se quieren ver (quien lo usa decide
// si va de la más antigua a la más reciente o al revés). vacio: texto cuando no hay entradas.
export default function HistorialCambios({ entradas, vacio, titulo = 'Historial de cambios', id = 'titulo-historial' }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="break-words text-h3">{titulo}</h2>
      {entradas.length === 0 ? (
        <p className="text-body text-secondary">{vacio}</p>
      ) : (
        <ol className="flex flex-col gap-3">
          {entradas.map((entrada) => (
            <li key={entrada.clave} className="flex flex-col gap-1 rounded-lg border bg-surface p-4">
              <p className="break-words text-body-medium">{entrada.encabezado}</p>
              <ul className="flex flex-col gap-1">
                {entrada.lineas.map((linea) => (
                  <li key={linea.clave} className="break-words text-body">{linea.texto}</li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
