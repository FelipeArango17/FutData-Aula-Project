import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import FormularioDocumento from './FormularioDocumento.jsx'
import ListaVerificacionDocumentos from './ListaVerificacionDocumentos.jsx'
import DocumentosDelAcudiente from './DocumentosDelAcudiente.jsx'

const ids = (html) => [...html.matchAll(/ id="([^"]+)"/g)].map((m) => m[1])
const repetidos = (lista) => lista.filter((x, i) => lista.indexOf(x) !== i)

describe('ids únicos por formulario', () => {
  it('dos formularios en la misma pantalla no repiten ids y cada etiqueta y aria-describedby apuntan a su propio campo', () => {
    const html = renderToStaticMarkup(<><FormularioDocumento onSubir={async () => {}} /><FormularioDocumento onSubir={async () => {}} /></>)
    expect(repetidos(ids(html))).toEqual([])
    const todos = new Set(ids(html))
    for (const [, para] of html.matchAll(/ for="([^"]+)"/g)) expect(todos.has(para), `label for=${para}`).toBe(true)
    for (const [, lista] of html.matchAll(/aria-describedby="([^"]+)"/g)) for (const id of lista.split(' ')) expect(todos.has(id), `describedby ${id}`).toBe(true)
    // dentro de cada formulario, el select apunta a SU ayuda
    for (const form of html.split('<form').slice(1)) {
      const select = form.match(/<select id="([^"]+)"[^>]*aria-describedby="([^"]+)"/)
      expect(form).toContain(`id="${select[2]}"`)
      expect(select[2]).toContain(select[1].replace(/-tipo$/, ''))
    }
  })

  it('la pantalla del acudiente con varios jugadores (formularios y listas de verificación) no repite ids', () => {
    const jugador = (id) => ({ id, nombre: `Jugador ${id}`, tieneFoto: false, documentos: [], estadoInscripcion: 'PENDIENTE', documentosFaltantes: [] })
    const html = renderToStaticMarkup(<DocumentosDelAcudiente jugadores={[jugador('a'), jugador('b'), jugador('c')]} onSubir={async () => {}} />)
    expect(repetidos(ids(html))).toEqual([])
  })

  it('dos listas de verificación tampoco repiten el id del título', () => {
    const html = renderToStaticMarkup(<><ListaVerificacionDocumentos documentos={[]} /><ListaVerificacionDocumentos documentos={[]} /></>)
    expect(repetidos(ids(html))).toEqual([])
  })
})
