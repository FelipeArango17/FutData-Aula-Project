import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import SesionesSemana from './SesionesSemana.jsx'
import { FormularioExtra } from './DialogosHorario.jsx'
import { sesionesDeSemana } from '../../utils/semana.js'
import { diasDeSemana } from '../../utils/fechas.js'
import { FRANJAS_EJEMPLO } from '../../services/horarioClubData.js'

const LUNES = '2026-10-19'
const HOY = '2026-10-22' // jueves
const dias = diasDeSemana(LUNES)
const franjas = FRANJAS_EJEMPLO.map((f) => ({ ...f, categoria: ['Párvulos', 'Cachorros', 'Semillero', 'Sub-11', 'Sub-12', 'Sub-13', 'Sub-14', 'Sub-15', 'Sub-16'][f.idCategoria - 1], entrenador: f.idEntrenador ? `Entrenador ${f.idEntrenador}` : null }))
const sub12Lunes = franjas.find((f) => f.categoria === 'Sub-12' && f.dia === 1)
const excepciones = [
  { id: 1, tipo: 'CANCELACION', idFranja: sub12Lunes.id, fecha: '2026-10-19', motivo: 'Cancha en mantenimiento' },
  { id: 2, tipo: 'EXTRA', fecha: '2026-10-23', motivo: null, idCategoria: 5, categoria: 'Sub-12', horaInicio: '06:00', horaFin: '07:00', sede: 'Horizontes', idEntrenador: 1, entrenador: 'Nicolás', grupo: null },
]
const sesiones = sesionesDeSemana(franjas, excepciones, LUNES)
const acciones = (s) => ({ cancelar: s.tipo === 'FRANJA' && s.estado === 'PROGRAMADO', reactivar: s.estado === 'CANCELADO', rotar: s.tipo === 'FRANJA', editarExtra: s.tipo === 'EXTRA', eliminarExtra: s.tipo === 'EXTRA' })
const noop = () => {}
const dibujar = (modo, extra = {}) => renderToStaticMarkup(<SesionesSemana dias={dias} sesiones={sesiones} hoy={HOY} modo={modo} {...extra} />)

describe('SesionesSemana', () => {
  it('la cuadrícula tiene tabla con caption y las fechas de lunes a sábado; resalta hoy con texto', () => {
    const html = dibujar('cuadricula')
    expect(html).toContain('<caption class="sr-only">Horario de la semana')
    for (const fecha of ['Lun 19 oct', 'Mar 20 oct', 'Mié 21 oct', 'Jue 22 oct', 'Vie 23 oct', 'Sáb 24 oct']) expect(html).toContain(fecha)
    expect(html).toMatch(/aria-current="date"[^>]*>Jue 22 oct<span[^>]*>Hoy<\/span>/)
  })

  it('una sesión cancelada se dice con texto (Cancelada), va tachada y muestra el motivo', () => {
    for (const modo of ['cuadricula', 'lista']) {
      const html = dibujar(modo)
      expect(html).toContain('Cancelada')
      expect(html).toContain('line-through')
      expect(html).toContain('Motivo: Cancha en mantenimiento')
    }
  })

  it('un extra lleva la etiqueta Extra', () => {
    expect(dibujar('lista')).toContain('Extra')
    expect(dibujar('lista')).toContain('6:00 a. m. – 7:00 a. m.')
  })

  it('muestra categoría con grupo, hora, sede y "Por turnos" cuando no hay entrenador', () => {
    const html = dibujar('lista')
    expect(html).toContain('Semillero · grupo mañana')
    expect(html).toContain('UVA El Paraíso')
    expect(html).toContain('Entrenador: Por turnos')
    expect(html).toContain('Entrenador: Entrenador 1')
  })

  it('la lista agrupa por día con fecha larga y marca hoy', () => {
    const html = dibujar('lista')
    expect(html).toContain('Lunes 19 de octubre')
    expect(html).toContain('Jueves 22 de octubre · Hoy')
  })

  it('sin accionesDe es solo lectura: ningún botón', () => {
    expect(dibujar('cuadricula')).not.toContain('<button')
    expect(dibujar('lista')).not.toContain('<button')
  })

  it('con acciones: Cancelar, Reactivar, Cambiar entrenador (todas las semanas), Editar y Eliminar solo donde corresponde', () => {
    const html = dibujar('lista', { accionesDe: acciones, onCancelar: noop, onReactivar: noop, onRotar: noop, onEditarExtra: noop, onEliminarExtra: noop })
    expect(html).toContain('aria-label="Cancelar entrenamiento: Sub-13')
    expect(html).toContain('aria-label="Reactivar entrenamiento: Sub-12, lunes 19 de octubre')
    expect(html).toContain('Cambiar entrenador (todas las semanas)')
    expect(html).toContain('aria-label="Editar entrenamiento extra: Sub-12, viernes 23 de octubre')
    expect(html).toContain('aria-label="Eliminar entrenamiento extra: Sub-12, viernes 23 de octubre')
    // la sesión cancelada no se vuelve a cancelar
    expect(html).not.toContain('aria-label="Cancelar entrenamiento: Sub-12, lunes 19 de octubre')
  })

  it('una acción apagada no se dibuja', () => {
    const soloCancelar = (s) => ({ ...acciones(s), rotar: false, editarExtra: false, eliminarExtra: false, reactivar: false })
    const html = dibujar('lista', { accionesDe: soloCancelar, onCancelar: noop })
    expect(html).not.toContain('Cambiar entrenador')
    expect(html).not.toContain('Reactivar')
    expect(html).not.toContain('Eliminar entrenamiento extra')
  })
})

describe('FormularioExtra', () => {
  const entrenadores = [{ id: 1, nombre: 'Nicolás', activo: true }, { id: 2, nombre: 'David Mesa', activo: true }, { id: 9, nombre: 'Inactivo', activo: false }]
  const inicial = { categoria: 'Sub-12', fecha: '', hora: '', horaFin: '', sede: '', idEntrenador: '1', grupo: '', motivo: '' }
  const dibujarForm = (props = {}) => renderToStaticMarkup(
    <FormularioExtra inicial={inicial} categorias={['Sub-12']} entrenadores={entrenadores} entrenadorFijo={null} hoy="2026-10-22" proponerEntrenador={() => null}
      detectarConflicto={async () => ({ hayConflicto: false })} onGuardar={noop} onCerrar={noop} {...props} />,
  )

  it('la fecha no admite días pasados y dice a quién se avisa', () => {
    const html = dibujarForm()
    expect(html).toContain('min="2026-10-22"')
    expect(html).toContain('Se avisará por correo a los acudientes de Sub-12.')
    expect(html).toContain('Guardar y avisar')
  })

  it('el administrador elige entre entrenadores activos o por turnos', () => {
    const html = dibujarForm()
    expect(html).toContain('Por turnos')
    expect(html).toContain('David Mesa')
    expect(html).not.toContain('Inactivo')
  })

  it('el entrenador solo puede asignarse a sí mismo: un único entrenador y el campo deshabilitado', () => {
    const html = dibujarForm({ entrenadorFijo: 1 })
    expect(html).toMatch(/<select id="extra-entrenador"[^>]*disabled/)
    expect(html).not.toContain('David Mesa')
    expect(html).not.toContain('Por turnos')
  })

  it('al editar el botón dice Guardar cambios', () => {
    expect(dibujarForm({ edita: true })).toContain('Guardar cambios')
  })
})
