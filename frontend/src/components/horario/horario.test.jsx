import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import VistaSemanalClub from './VistaSemanalClub.jsx'
import FormularioFranja from './FormularioFranja.jsx'
import FormularioEntrenamiento from '../entrenamientos/FormularioEntrenamiento.jsx'
import CuadriculaSemanal from '../entrenamientos/CuadriculaSemanal.jsx'
import ListaPorDia from '../entrenamientos/ListaPorDia.jsx'
import { FRANJAS_EJEMPLO, idCategoriaDe } from '../../services/horarioClubData.js'
import { entrenamientosDeEjemplo } from '../../services/entrenamientosService.js'
import { diasDeSemana } from '../../utils/fechas.js'

const sub11Martes = FRANJAS_EJEMPLO.find((f) => f.idCategoria === idCategoriaDe('Sub-11') && f.dia === 2)
const semillero = FRANJAS_EJEMPLO.find((f) => f.grupo === 'mañana' && f.idCategoria === idCategoriaDe('Semillero') && f.dia === 2)

describe('VistaSemanalClub', () => {
  it('la cuadrícula tiene tabla con caption, columnas de lunes a sábado y filas por hora de inicio', () => {
    const html = renderToStaticMarkup(<VistaSemanalClub franjas={FRANJAS_EJEMPLO} modo="cuadricula" />)
    expect(html).toContain('<caption class="sr-only">Horario semanal del club')
    for (const dia of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']) expect(html).toMatch(new RegExp(`<th[^>]*scope="col"[^>]*>${dia}</th>`))
    expect(html).toContain('<th scope="row"')
  })

  it('cada bloque muestra categoría, hora, sede y el NOMBRE del entrenador', () => {
    const html = renderToStaticMarkup(<VistaSemanalClub franjas={[sub11Martes]} modo="cuadricula" />)
    expect(html).toContain('Sub-11')
    expect(html).toContain('8:00 p. m. – 9:00 p. m.')
    expect(html).toContain('Horizontes')
    expect(html).toContain('David Mesa')
  })

  it('una franja sin entrenador dice "Por turnos" y una con grupo lo muestra', () => {
    const html = renderToStaticMarkup(<VistaSemanalClub franjas={[semillero]} modo="cuadricula" />)
    expect(html).toContain('Por turnos')
    expect(html).not.toContain('Todos los entrenadores')
    expect(html).toContain('Semillero · grupo mañana')
    expect(html).toContain('7:30 a. m. – 9:00 a. m.')
    expect(html).toContain('UVA El Paraíso')
  })

  it('con onEditar los bloques son botones con descripción accesible de 44 px; sin él son solo lectura', () => {
    const editable = renderToStaticMarkup(<VistaSemanalClub franjas={[sub11Martes]} modo="cuadricula" onEditar={() => {}} />)
    expect(editable).toMatch(/<button[^>]*aria-label="Editar franja: Sub-11, martes, 8:00 p\. m\. – 9:00 p\. m\., Horizontes, entrenador: David Mesa"/)
    expect(editable).toContain('min-h-11')
    const lectura = renderToStaticMarkup(<VistaSemanalClub franjas={[sub11Martes]} modo="cuadricula" />)
    expect(lectura).not.toContain('<button')
  })

  it('la lista agrupa por día con encabezados y tarjetas con Editar y Eliminar', () => {
    const html = renderToStaticMarkup(<VistaSemanalClub franjas={FRANJAS_EJEMPLO} modo="lista" onEditar={() => {}} onEliminar={() => {}} />)
    for (const dia of ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']) expect(html).toMatch(new RegExp(`<h2[^>]*>${dia}</h2>`))
    expect(html).toContain('Entrenador: David Mesa')
    expect(html).toContain('Entrenador: Por turnos')
    expect(html).not.toContain('Dairo')
    expect(html).toContain('Grupo mañana')
    expect(html).toMatch(/aria-label="Eliminar franja: Sub-11, martes/)
    expect(html).not.toContain('overflow-x')
  })
})

describe('FormularioFranja', () => {
  const vacia = { idCategoria: '', dia: '', horaInicio: '', horaFin: '', sede: '', idEntrenador: '', grupo: '' }
  const dibujar = (inicial = vacia) => renderToStaticMarkup(<FormularioFranja inicial={inicial} onGuardar={async () => {}} onCancelar={() => {}} />)

  it('tiene la leyenda "* Campo obligatorio" y los cinco campos obligatorios con asterisco', () => {
    const html = dibujar()
    expect(html).toContain('* Campo obligatorio')
    expect(html.match(/aria-required="true"/g)).toHaveLength(5)
    for (const etiqueta of ['Categoría', 'Día', 'Hora de inicio', 'Hora de fin', 'Sede']) {
      expect(html).toMatch(new RegExp(`<label[^>]*>${etiqueta}<span[^>]*aria-hidden="true"[^>]*> \\*</span></label>`))
    }
  })

  it('ofrece las 9 categorías, los días de lunes a sábado, las 2 sedes y "Por turnos"', () => {
    const html = dibujar()
    expect(html.match(/<option value="\d+">Sub-\d+<\/option>/g)).toHaveLength(6)
    expect(html).toContain('>Párvulos</option>')
    expect(html).toContain('>Cachorros</option>')
    expect(html).toContain('>Semillero</option>')
    expect(html).not.toMatch(/>Sub-(9|10)</)
    for (const dia of ['Lunes', 'Sábado']) expect(html).toContain(`>${dia}</option>`)
    expect(html).not.toContain('>Domingo</option>')
    expect(html).toContain('>Horizontes</option>')
    expect(html).toContain('>UVA El Paraíso</option>')
    expect(html).toContain('>Por turnos</option>')
    expect(html).not.toContain('Todos los entrenadores')
    expect(html).not.toContain('Cancha')
  })

  it('el grupo solo aparece en Cachorros y Semillero', () => {
    expect(dibujar()).not.toContain('franja-grupo')
    expect(dibujar({ ...vacia, idCategoria: String(idCategoriaDe('Semillero')) })).toContain('franja-grupo')
    expect(dibujar({ ...vacia, idCategoria: String(idCategoriaDe('Cachorros')) })).toContain('franja-grupo')
    expect(dibujar({ ...vacia, idCategoria: String(idCategoriaDe('Párvulos')) })).not.toContain('franja-grupo')
    expect(dibujar({ ...vacia, idCategoria: String(idCategoriaDe('Sub-12')) })).not.toContain('franja-grupo')
  })
})

describe('Entrenamientos con entrenador y sede', () => {
  const dias = diasDeSemana('2026-10-19')
  const ejemplo = entrenamientosDeEjemplo()

  it('la cuadrícula semanal muestra categoría, grupo, hora, sede y entrenador', () => {
    const sub12 = ejemplo.filter((e) => e.categoria === 'Sub-12' && e.fecha === '2026-10-19')
    const html = renderToStaticMarkup(<MemoryRouter><CuadriculaSemanal dias={dias} entrenamientos={sub12} base="/administrador/horarios" /></MemoryRouter>)
    expect(html).toContain('Sub-12')
    expect(html).toContain('7:00 p. m. – 8:00 p. m.')
    expect(html).toContain('Horizontes')
    expect(html).toContain('Nicolás')
    expect(html).not.toContain('Cancha')
    const grupo = ejemplo.filter((e) => e.grupo === 'tarde').slice(0, 1)
    const conGrupo = renderToStaticMarkup(<MemoryRouter><CuadriculaSemanal dias={dias} entrenamientos={grupo} base="/x" /></MemoryRouter>)
    expect(conGrupo).toContain('· tarde')
    expect(conGrupo).toContain('Por turnos')
  })

  it('la lista por día (móvil) muestra hora, sede y entrenador dentro de la tarjeta', () => {
    const html = renderToStaticMarkup(<MemoryRouter><ListaPorDia dias={dias} entrenamientos={ejemplo.filter((e) => e.fecha === '2026-10-19' && e.categoria === 'Sub-13')} base="/x" onEliminar={() => {}} /></MemoryRouter>)
    expect(html).toContain('7:00 p. m. – 8:00 p. m.')
    expect(html).toContain('Sub-13 · Horizontes')
    expect(html).toContain('Entrenador: Jhon Cardona')
  })

  it('el formulario pide sede (no lugar), hora de inicio y fin, y ofrece "Por turnos" y los activos', () => {
    const inicial = { categoria: '', idEntrenador: '', fecha: '', hora: '', horaFin: '', sede: '', grupo: '' }
    const html = renderToStaticMarkup(
      <FormularioEntrenamiento inicial={inicial} categorias={['Semillero', 'Sub-12']} detectarConflicto={async () => ({ hayConflicto: false })} onGuardar={async () => {}} onCancelar={() => {}} />,
    )
    expect(html).toContain('>Sede<')
    expect(html).toContain('>Selecciona la sede</option>')
    expect(html).toContain('>Horizontes</option>')
    expect(html).toContain('>UVA El Paraíso</option>')
    expect(html).toContain('>Hora de inicio<')
    expect(html).toContain('>Hora de fin<')
    expect(html).toContain('>Por turnos</option>')
    for (const nombre of ['Nicolás', 'David Mesa', 'Jhon Cardona', 'Giovanny', 'Jhon Ramírez']) expect(html).toContain(`>${nombre}</option>`)
    expect(html).not.toContain('Dairo')
    expect(html).not.toMatch(/Lugar|Cancha/)
    expect(html).not.toContain('id="grupo"') // sin Cachorros ni Semillero elegida
  })

  it('con Semillero elegida aparece el grupo opcional', () => {
    const inicial = { categoria: 'Semillero', idEntrenador: '1', fecha: '', hora: '', horaFin: '', sede: '', grupo: '' }
    const html = renderToStaticMarkup(
      <FormularioEntrenamiento inicial={inicial} categorias={['Semillero', 'Sub-12']} detectarConflicto={async () => ({ hayConflicto: false })} onGuardar={async () => {}} onCancelar={() => {}} />,
    )
    expect(html).toContain('Grupo (opcional)')
    expect(html).toContain('>Mañana</option>')
  })
})
