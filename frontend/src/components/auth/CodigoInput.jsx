import { useRef } from 'react'

const LARGO = 6

// Seis casillas, una por dígito. value: texto de hasta 6 dígitos; onChange recibe el texto nuevo.
// Teclado: los dígitos avanzan solos, Backspace retrocede, las flechas se mueven. Pegar reparte los dígitos.
// Se eligieron casillas (como en el diseño) y no un único input; ver el reporte de la tarea.
export default function CodigoInput({ value, onChange, disabled, invalid, describedBy, inputRef }) {
  const casillas = useRef([])
  const digitos = Array.from({ length: LARGO }, (_, i) => value[i] ?? '')

  const enfocar = (i) => {
    const casilla = casillas.current[Math.max(0, Math.min(LARGO - 1, i))]
    casilla?.focus()
    casilla?.select()
  }
  const juntar = (lista) => lista.join('')

  // Reparte `texto` (solo dígitos) desde la casilla `desde`
  function repartir(desde, texto) {
    const nuevos = [...digitos]
    const limpios = texto.replace(/\D/g, '').slice(0, LARGO - desde)
    for (let k = 0; k < limpios.length; k++) nuevos[desde + k] = limpios[k]
    onChange(juntar(nuevos))
    enfocar(desde + limpios.length)
  }

  function alCambiar(i, event) {
    const texto = event.target.value.replace(/\D/g, '')
    if (texto === '') {
      const nuevos = [...digitos]
      nuevos[i] = ''
      return onChange(juntar(nuevos))
    }
    repartir(i, texto) // un dígito, o varios si el navegador autocompleta el código
  }

  function alTeclear(i, event) {
    if (/^\d$/.test(event.key) && !event.ctrlKey && !event.metaKey && !event.altKey) {
      // Se maneja aquí (y no en onChange) para que escribir el mismo dígito que ya hay también avance
      event.preventDefault()
      repartir(i, event.key)
    } else if (event.key === 'Backspace' && digitos[i] === '' && i > 0) {
      event.preventDefault()
      const nuevos = [...digitos]
      nuevos[i - 1] = ''
      onChange(juntar(nuevos))
      enfocar(i - 1)
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      enfocar(i - 1)
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      enfocar(i + 1)
    }
  }

  function alPegar(i, event) {
    event.preventDefault()
    const texto = event.clipboardData.getData('text')
    // un código completo se reparte desde la primera casilla
    repartir(texto.replace(/\D/g, '').length >= LARGO ? 0 : i, texto)
  }

  return (
    <div className="flex flex-wrap justify-center gap-x-4 gap-y-2">
      {[0, 1].map((grupo) => (
        <div key={grupo} className="flex flex-wrap justify-center gap-2">
          {digitos.slice(grupo * 3, grupo * 3 + 3).map((digito, k) => {
            const i = grupo * 3 + k
            return (
              <input
                key={i}
                ref={(el) => {
                  casillas.current[i] = el
                  if (i === 0 && inputRef) inputRef.current = el
                }}
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={1}
                value={digito}
                disabled={disabled}
                aria-label={`Dígito ${i + 1} de ${LARGO}`}
                aria-invalid={invalid ? true : undefined}
                aria-describedby={describedBy}
                onChange={(e) => alCambiar(i, e)}
                onKeyDown={(e) => alTeclear(i, e)}
                onPaste={(e) => alPegar(i, e)}
                onFocus={(e) => e.target.select()}
                className={`min-h-12 w-11 shrink-0 rounded-md border bg-surface px-0 text-center text-h2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:cursor-not-allowed disabled:opacity-60 ${
                  invalid ? 'border-danger' : ''
                }`}
              />
            )
          })}
        </div>
      ))}
    </div>
  )
}
