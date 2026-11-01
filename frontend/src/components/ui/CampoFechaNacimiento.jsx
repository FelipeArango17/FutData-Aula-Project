import { useState } from 'react'
import Icon from '../Icon.jsx'
import { MESES } from '../../utils/fechas.js'

// Fecha por día / mes / año con tres listas, sin calendario. Valor: "AAAA-MM-DD", o '' mientras falte alguna parte.
// props: label, id (los selects usan id-dia, id-mes e id-anio), value, onChange(valor), anioMinimo, anioMaximo (los años van
// del más reciente al más antiguo), error, requerido, describedBy.
const dos = (n) => String(n).padStart(2, '0')
const select = 'min-h-11 w-full rounded-md border bg-surface px-3 text-body focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

function partesDe(valor) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor ?? '')
  return m ? { anio: m[1], mes: String(Number(m[2])), dia: String(Number(m[3])) } : null
}

// Días del mes (con el año desconocido se cuenta como bisiesto, para no quitar el 29 de febrero antes de tiempo)
const diasDelMes = (anio, mes) => (mes ? new Date(Date.UTC(Number(anio) || 2000, Number(mes), 0)).getUTCDate() : 31)

export default function CampoFechaNacimiento({ label, id, value, onChange, anioMinimo, anioMaximo, error, requerido, describedBy }) {
  const [parcial, setParcial] = useState({ dia: '', mes: '', anio: '' })
  // Una fecha completa que llega de fuera manda; mientras esté incompleta se conserva lo que el usuario ya eligió
  const partes = partesDe(value) ?? parcial
  const errorId = error ? `${id}-error` : undefined

  function cambiar(parte, texto) {
    const nuevas = { ...partes, [parte]: texto }
    if (nuevas.dia && Number(nuevas.dia) > diasDelMes(nuevas.anio, nuevas.mes)) nuevas.dia = ''
    setParcial(nuevas)
    const completa = nuevas.dia && nuevas.mes && nuevas.anio
    onChange(completa ? `${nuevas.anio}-${dos(nuevas.mes)}-${dos(nuevas.dia)}` : '')
  }

  const anios = []
  for (let a = anioMaximo; a >= anioMinimo; a--) anios.push(String(a))
  if (partes.anio && !anios.includes(partes.anio)) anios.unshift(partes.anio) // un año fuera del rango se muestra; la validación lo rechaza
  const propiedades = {
    'aria-invalid': error ? true : undefined,
    'aria-required': requerido ? true : undefined,
    'aria-describedby': [errorId, describedBy].filter(Boolean).join(' ') || undefined,
  }

  return (
    <fieldset className="flex min-w-0 flex-col gap-1">
      <legend className="mb-1 break-words text-body-medium">
        {label}
        {requerido && <span aria-hidden="true" className="text-danger"> *</span>}
      </legend>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,6rem),1fr))] gap-2">
        <div>
          <label htmlFor={`${id}-dia`} className="sr-only">Día</label>
          <select id={`${id}-dia`} value={partes.dia} onChange={(e) => cambiar('dia', e.target.value)} className={`${select} ${error ? 'border-danger' : ''}`} {...propiedades}>
            <option value="">Día</option>
            {Array.from({ length: diasDelMes(partes.anio, partes.mes) }, (_, i) => <option key={i + 1} value={String(i + 1)}>{i + 1}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-mes`} className="sr-only">Mes</label>
          <select id={`${id}-mes`} value={partes.mes} onChange={(e) => cambiar('mes', e.target.value)} className={`${select} ${error ? 'border-danger' : ''}`} {...propiedades}>
            <option value="">Mes</option>
            {MESES.map((m, i) => <option key={m} value={String(i + 1)}>{m[0].toUpperCase() + m.slice(1)}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-anio`} className="sr-only">Año</label>
          <select id={`${id}-anio`} value={partes.anio} onChange={(e) => cambiar('anio', e.target.value)} className={`${select} ${error ? 'border-danger' : ''}`} {...propiedades}>
            <option value="">Año</option>
            {anios.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </div>
      </div>
      {error && (
        <p id={errorId} className="flex items-start gap-1 text-small text-danger">
          <Icon name="alert" className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="min-w-0 break-words">{error}</span>
        </p>
      )}
    </fieldset>
  )
}
