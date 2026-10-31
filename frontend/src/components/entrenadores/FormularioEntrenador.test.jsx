import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import FormularioEntrenador from './FormularioEntrenador.jsx'

// Render estático (sin navegador): comprueba el marcado accesible del formulario y los casos de entrenador activo e inactivo.
const VACIO = {
  nombre: '', apellido: '', tipoDocumento: '', documento: '', fechaNacimiento: '', telefono: '', correo: '',
  especialidad: '', categorias: [], horarioTrabajo: '', direccion: '',
}
const sinOperaciones = async () => {}
const dibujar = (props = {}) => renderToStaticMarkup(
  <FormularioEntrenador
    inicial={VACIO}
    detectarDuplicado={async () => null}
    onGuardar={sinOperaciones}
    onAsignarCategorias={sinOperaciones}
    onTerminar={() => {}}
    onCancelar={() => {}}
    {...props}
  />,
)

describe('FormularioEntrenador (marcado)', () => {
  it('la fecha de nacimiento es un input date nativo con etiqueta visible', () => {
    const html = dibujar()
    expect(html).toMatch(/<label[^>]*for="fechaNacimiento"[^>]*>Fecha de nacimiento \(18 años o más\)<\/label>/)
    expect(html).toMatch(/<input[^>]*id="fechaNacimiento"[^>]*type="date"|<input[^>]*type="date"[^>]*id="fechaNacimiento"/)
  })

  it('el tipo de documento ofrece los códigos de la API: CC, CE y PASAPORTE (sin TI)', () => {
    const html = dibujar()
    for (const codigo of ['CC', 'CE', 'PASAPORTE']) expect(html).toContain(`value="${codigo}"`)
    expect(html).not.toContain('value="TI"')
  })

  it('las categorías son opcionales y muestran quién dirige cada una', () => {
    const html = dibujar({
      ignorarId: 'yo',
      categorias: [
        { id: '1', nombre: 'Sub-8', entrenador: null },
        { id: '2', nombre: 'Sub-12', entrenador: { id: 'otro', nombre: 'Andrés', apellido: 'Mora' } },
        { id: '3', nombre: 'Sub-10', entrenador: { id: 'yo', nombre: 'Luis', apellido: 'Pérez' } },
      ],
    })
    expect(html).toContain('Categorías a cargo (opcional)')
    expect(html).toContain('Sin entrenador: Sub-8.')
    expect(html).toContain('Con otro entrenador: Sub-12 (Andrés Mora)')
    expect(html).not.toContain('Sub-10 (Luis')
  })

  it('un entrenador inactivo no ofrece categorías', () => {
    const html = dibujar({ inicial: { ...VACIO, activo: false } })
    expect(html).not.toContain('Categorías a cargo')
    expect(html).toContain('Este entrenador está inactivo')
  })

  it('los campos con valores de la API sin null (nada de value="null")', () => {
    const html = dibujar({ inicial: { ...VACIO, nombre: 'Luis', horarioTrabajo: '', direccion: '' } })
    expect(html).not.toContain('null')
  })
})
