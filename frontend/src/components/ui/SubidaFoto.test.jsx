import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import SubidaFoto from './SubidaFoto.jsx'
import Avatar from './Avatar.jsx'

const dibujar = (props = {}) => renderToStaticMarkup(<SubidaFoto nombre="Santiago Ríos" onGuardar={async () => {}} onQuitar={async () => {}} {...props} />)

describe('SubidaFoto', () => {
  it('sin foto muestra las iniciales, el botón "Elegir foto" y un input que solo acepta JPG y PNG', () => {
    const html = dibujar()
    expect(html).not.toContain('<img')
    expect(html).toContain('>SR<')
    expect(html).toContain('>Elegir foto</label>')
    expect(html).toMatch(/<input[^>]*type="file"[^>]*accept="image\/jpeg,image\/png"/)
    expect(html).toContain('JPG o PNG, máximo 5 MB.')
    expect(html).not.toContain('Quitar foto')
    expect(html).not.toContain('Guardar foto')
  })

  it('el input es enfocable por teclado (sr-only, no oculto) y su etiqueta lo señala con htmlFor', () => {
    const html = dibujar()
    const id = html.match(/<input id="([^"]+)"/)[1]
    expect(html).toContain(`for="${id}"`)
    expect(html).toContain('sr-only')
    expect(html).not.toContain('display:none')
    expect(html).toContain('min-h-11')
    expect(html).toContain('peer-focus-visible:ring-2')
  })

  it('con foto la muestra con alt "Foto de {nombre}" y ofrece "Quitar foto"', () => {
    const html = dibujar({ foto: 'blob:http://localhost/abc' })
    expect(html).toMatch(/<img src="blob:http:\/\/localhost\/abc" alt="Foto de Santiago Ríos"/)
    expect(html).toContain('Quitar foto')
    expect(html).not.toContain('>SR<')
  })

  it('sin onQuitar no hay "Quitar foto"', () => {
    expect(dibujar({ foto: 'blob:x', onQuitar: undefined })).not.toContain('Quitar foto')
  })

  it('con error muestra el mensaje en role="alert" y lo asocia al input con aria-describedby', () => {
    const html = dibujar({ errorInicial: 'Solo se aceptan archivos JPG o PNG.' })
    const id = html.match(/<input id="([^"]+)"/)[1]
    expect(html).toMatch(/role="alert"[^>]*><p id="[^"]+-error"/)
    expect(html).toContain('Solo se aceptan archivos JPG o PNG.')
    expect(html).toMatch(new RegExp(`aria-describedby="${id.replace(/:/g, ':')}-error ${id}-ayuda"`))
    expect(html).toContain('aria-invalid="true"')
  })

  it('sin error no hay mensaje; el contenedor role="alert" siempre está', () => {
    const html = dibujar()
    expect(html).toContain('role="alert"')
    expect(html).not.toContain('-error')
  })

  it('el diálogo de confirmación no se dibuja cerrado', () => {
    expect(dibujar({ foto: 'blob:x' })).not.toContain('alertdialog')
  })
})

describe('Avatar con foto', () => {
  it('sin foto sigue mostrando las iniciales', () => {
    const html = renderToStaticMarkup(<Avatar name="Santiago Ríos" />)
    expect(html).toContain('>SR<')
    expect(html).toContain('aria-hidden="true"')
    expect(html).toContain('min-h-10 min-w-10')
  })

  it('con foto muestra una imagen redonda decorativa del mismo tamaño', () => {
    const html = renderToStaticMarkup(<Avatar name="Santiago Ríos" foto="blob:x" />)
    expect(html).toMatch(/<img src="blob:x" alt=""/)
    expect(html).toContain('rounded-full')
    expect(html).toContain('h-10 w-10')
    expect(html).not.toContain('>SR<')
  })
})
