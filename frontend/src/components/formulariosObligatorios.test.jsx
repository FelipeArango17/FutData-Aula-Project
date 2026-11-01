import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import CamposDeportista from './deportistas/CamposDeportista.jsx'
import FormularioEntrenador from './entrenadores/FormularioEntrenador.jsx'
import FormularioEntrenamiento from './entrenamientos/FormularioEntrenamiento.jsx'
import FormularioUsuario from './usuarios/FormularioUsuario.jsx'
import { hoyLocal, restarAnios } from '../utils/fechas.js'

const sin = async () => {}
const texto = () => ({ value: '', error: undefined, onChange: () => {} })
const LEYENDA = '* Campo obligatorio'

describe('leyenda "* Campo obligatorio" y asteriscos', () => {
  it('FormularioEntrenador: leyenda, 7 campos obligatorios; dirección y horario sin asterisco', () => {
    const vacio = { nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', correo: '', especialidad: '', categorias: [], horarioTrabajo: '', direccion: '' }
    const html = renderToStaticMarkup(
      <FormularioEntrenador inicial={vacio} detectarDuplicado={async () => null} onGuardar={sin} onAsignarCategorias={sin} onTerminar={() => {}} onCancelar={() => {}} />,
    )
    expect(html).toContain(LEYENDA)
    // 10: nombre, apellido, tipo, documento, día, mes y año, teléfono, correo, especialidad
    expect(html.match(/aria-required="true"/g)).toHaveLength(10)
    expect(html).toMatch(/Dirección \(opcional\)<\/label>/)
    expect(html).toMatch(/Horario de trabajo \(opcional\)<\/label>/)
  })

  it('CamposDeportista: leyenda y todos los campos salvo categoría y edad (que son de solo lectura)', () => {
    const html = renderToStaticMarkup(<CamposDeportista texto={texto} fechaNacimiento="" />)
    expect(html).toContain(LEYENDA)
    // nombres, apellidos, tipo y número de documento, día, mes y año, teléfono y dirección
    expect(html.match(/aria-required="true"/g)).toHaveLength(9)
    expect(html).not.toMatch(/id="categoria"[^>]*aria-required/)
  })

  it('FormularioEntrenamiento: leyenda y los cinco campos obligatorios', () => {
    const inicial = { categoria: '', idEntrenador: '', fecha: '', hora: '', lugar: '' }
    const html = renderToStaticMarkup(
      <FormularioEntrenamiento inicial={inicial} categorias={['Sub-8']} detectarConflicto={async () => ({ hayConflicto: false })} onGuardar={sin} onCancelar={() => {}} />,
    )
    expect(html).toContain(LEYENDA)
    expect(html.match(/aria-required="true"/g)).toHaveLength(5)
  })

  it('FormularioUsuario: leyenda, nombres, apellidos, correo y rol', () => {
    const html = renderToStaticMarkup(<FormularioUsuario onGuardar={sin} onCancelar={() => {}} />)
    expect(html).toContain(LEYENDA)
    expect(html.match(/aria-required="true"/g)).toHaveLength(3)
    expect(html).toMatch(/Rol<span[^>]*aria-hidden="true"[^>]*> \*<\/span>/)
  })
})

describe('límites de las fechas en los formularios', () => {
  it('entrenador: los años de la fecha de nacimiento van de hace 18 años a hace 100', () => {
    const vacio = { nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', correo: '', especialidad: '', categorias: [], horarioTrabajo: '', direccion: '' }
    const html = renderToStaticMarkup(
      <FormularioEntrenador inicial={vacio} detectarDuplicado={async () => null} onGuardar={sin} onAsignarCategorias={sin} onTerminar={() => {}} onCancelar={() => {}} />,
    )
    const anios = [...html.match(/<select id="fechaNacimiento-anio".*?<\/select>/s)[0].matchAll(/<option value="(\d{4})"/g)].map((m) => Number(m[1]))
    const hoy = Number(hoyLocal().slice(0, 4))
    expect(anios[0]).toBe(Number(restarAnios(hoyLocal(), 18).slice(0, 4)))
    expect(anios.at(-1)).toBe(hoy - 100)
    expect(anios).toHaveLength(83)
  })

  it('entrenamiento nuevo: min de la fecha es hoy; al editar uno pasado no hay min', () => {
    const props = { categorias: ['Sub-8'], detectarConflicto: async () => ({ hayConflicto: false }), onGuardar: sin, onCancelar: () => {} }
    const base = { categoria: 'Sub-8', idEntrenador: '1', hora: '16:00', lugar: 'Cancha 1' }
    const nuevo = renderToStaticMarkup(<FormularioEntrenamiento {...props} inicial={{ ...base, fecha: '' }} />)
    expect(nuevo).toContain(`min="${hoyLocal()}"`)
    const pasado = renderToStaticMarkup(<FormularioEntrenamiento {...props} ignorarId={1} inicial={{ ...base, fecha: '2020-01-01' }} />)
    expect(pasado).not.toContain('min="')
  })
})
