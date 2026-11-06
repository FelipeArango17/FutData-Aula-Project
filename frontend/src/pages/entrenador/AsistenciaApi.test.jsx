import { describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'

vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn() }))
vi.mock('../../services/supabaseClient.js', () => ({ autenticacionReal: true }))

import { VistaLista } from './AsistenciaApi.jsx'
import SesionesSemana from '../../components/horario/SesionesSemana.jsx'

const sesion = { fecha: '2026-10-22', hora: '19:00', horaFin: '20:00', categoria: 'Sub-12', grupo: null, sede: 'Horizontes', entrenador: 'Luis Pérez' }
const d = (id, nombre, extra = {}) => ({ id, nombre, apellido: 'Rojas', estado: '', observacion: '', ...extra })
const props = (extra) => ({
  sesion, deportistas: [d('a1', 'Ana'), d('b2', 'Beto', { estado: 'ausente', observacion: 'Gripa' })], puedeRegistrar: true,
  marcas: {}, abiertas: {}, guardando: false, onEstado() {}, onObservacion() {}, onAlternarObs() {}, onMarcarTodos() {}, onGuardar() {}, onVolver() {}, ...extra,
})
const dibujar = (extra) => renderToStaticMarkup(<VistaLista {...props(extra)} />)

describe('VistaLista', () => {
  it('muestra nombre y apellido, los tres estados y lo ya guardado', () => {
    const html = dibujar()
    expect(html).toContain('Ana Rojas')
    expect(html).toContain('Beto Rojas')
    for (const estado of ['Presente', 'Ausente', 'Justificado']) expect(html).toContain(estado)
    expect(html).toContain('type="radio"')
    expect(html).toContain('Gripa')
    expect(html).toContain('Marcar todos presentes')
  })

  it('el contador de observaciones usa el máximo de 300', () => {
    expect(dibujar()).toContain('5 de 300 caracteres')
  })

  it('sin nada marcado por el usuario no se puede guardar y se dice por qué', () => {
    const html = dibujar()
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Guardar asistencia/)
    expect(html).toContain('Marca la asistencia de al menos un deportista')
  })

  it('una observación sin estado avisa junto a la fila y bloquea Guardar aunque haya otros marcados', () => {
    const html = dibujar({ marcas: { a1: { estado: 'presente', observacion: '' }, c3: { estado: '', observacion: 'Llegó tarde' } }, deportistas: [d('a1', 'Ana'), d('c3', 'Carla')] })
    expect(html).toContain('Marca el estado para guardar la observación.')
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*>Guardar asistencia/)
  })

  it('con el estado marcado ya no avisa y se puede guardar', () => {
    const html = dibujar({ marcas: { c3: { estado: 'ausente', observacion: 'Llegó tarde' } }, deportistas: [d('c3', 'Carla')] })
    expect(html).not.toContain('Marca el estado para guardar la observación.')
    expect(html).not.toMatch(/disabled=""[^>]*>Guardar asistencia/)
  })

  it('el error de una fila del servidor se muestra junto a ella', () => {
    expect(dibujar({ erroresFila: { a1: 'Revisa la asistencia de este deportista.' } })).toContain('Revisa la asistencia de este deportista.')
  })

  it('con algo marcado se puede guardar', () => {
    const html = dibujar({ marcas: { a1: { estado: 'presente', observacion: '' } } })
    expect(html).not.toMatch(/disabled=""[^>]*>Guardar asistencia/)
    expect(html).not.toContain('Marca la asistencia de al menos un deportista')
  })

  it('mientras guarda bloquea el botón y lo dice', () => {
    const html = dibujar({ marcas: { a1: { estado: 'presente', observacion: '' } }, guardando: true })
    expect(html).toContain('Guardando…')
    expect(html).toMatch(/<button[^>]*disabled=""[^>]*aria-busy="true"/)
  })

  it('sin deportistas activos lo dice', () => {
    expect(dibujar({ deportistas: [] })).toContain('Esta sesión no tiene deportistas activos.')
  })

  it('sin permiso para registrar queda en solo lectura', () => {
    const html = dibujar({ puedeRegistrar: false })
    expect(html).not.toContain('type="radio"')
    expect(html).not.toContain('Guardar asistencia')
    expect(html).toContain('Sin marcar')
  })

  it('no muestra nada técnico', () => {
    expect(dibujar()).not.toMatch(/\/api|idFranja|idExcepcion|endpoint|UUID/i)
  })
})

describe('semana: Pasar lista', () => {
  const s = (clave, extra) => ({ clave, tipo: 'FRANJA', estado: 'PROGRAMADO', fecha: '2026-10-22', hora: '19:00', horaFin: '20:00', categoria: 'Sub-12', sede: 'Horizontes', puedeGestionar: true, ...extra })
  it('solo las sesiones con pasarLista ofrecen el botón', () => {
    const html = renderToStaticMarkup(
      <SesionesSemana
        dias={['2026-10-22', '2026-10-23']}
        hoy="2026-10-22"
        modo="lista"
        sesiones={[s('hoy'), s('futura', { fecha: '2026-10-23' }), s('cancelada', { estado: 'CANCELADO', hora: '18:00' })]}
        accionesDe={(x) => ({ pasarLista: x.estado !== 'CANCELADO' && x.fecha <= '2026-10-22' })}
        onPasarLista={() => {}}
      />,
    )
    expect(html.match(/>Pasar lista</g)).toHaveLength(1)
  })
})
