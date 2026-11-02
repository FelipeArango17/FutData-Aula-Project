import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { TablaDeportistas, TarjetasDeportistas } from './ListaDeportistas.jsx'
import DatosDeportista from './DatosDeportista.jsx'
import { adaptarDeportista } from '../../utils/deportistasApi.js'

// Permisos por rol con lo que devuelve la API: el entrenador no recibe documento, teléfono, dirección ni estado de la inscripción
const CAT = '0a0a0a0a-1111-4222-8333-444444444444'
const dto = { id: '6f1c2a9e-3b4d-4e5f-8a7b-1c2d3e4f5a6b', nombre: 'Ana', apellido: 'Pérez', tipoDocumento: 'TI', fechaNacimiento: '2016-08-20', edad: 10, posicion: null, foto: null, categoria: 'Semillero', idCategoria: CAT, grupo: 'tarde', activo: true }
const paraAdmin = adaptarDeportista({ ...dto, documento: '1098765432', telefono: '3001234567', direccion: 'Calle 10 # 20-30', estadoInscripcion: 'COMPLETA' })
const paraEntrenador = adaptarDeportista({ ...dto, telefono: null, direccion: null })
const nombres = { [CAT]: 'Semillero' }
const dibujar = (Componente, deportistas) => renderToStaticMarkup(<MemoryRouter><Componente deportistas={deportistas} nombresCategoria={nombres} /></MemoryRouter>)

describe('lista con lo que recibe cada rol', () => {
  it('administrador: la tabla trae la columna y el número de documento, la categoría con su grupo y la edad de la API', () => {
    const html = dibujar(TablaDeportistas, [paraAdmin])
    expect(html).toMatch(/<th scope="col"[^>]*>Documento<\/th>/)
    expect(html).toContain('1098765432')
    expect(html).toContain('>Semillero · tarde</td>')
    expect(html).toContain('10 años')
  })
  it('entrenador: sin columna de documento ni su valor, ni "undefined" por ningún lado', () => {
    for (const html of [dibujar(TablaDeportistas, [paraEntrenador]), dibujar(TarjetasDeportistas, [paraEntrenador])]) {
      expect(html).not.toContain('Documento')
      expect(html).not.toContain('undefined')
      expect(html).not.toContain('null')
      expect(html).toContain('Ana Pérez')
    }
  })
  it('las tarjetas del administrador sí muestran el documento', () => {
    expect(dibujar(TarjetasDeportistas, [paraAdmin])).toContain('Documento: 1098765432')
  })
})

describe('ficha con lo que recibe el administrador', () => {
  it('muestra el estado de la inscripción calculado por el backend, solo si viene', () => {
    expect(renderToStaticMarkup(<DatosDeportista deportista={paraAdmin} categoria="Semillero" />)).toContain('>Completa<')
    const sinEstado = renderToStaticMarkup(<DatosDeportista deportista={{ ...paraAdmin, estadoInscripcion: undefined }} categoria="Semillero" />)
    expect(sinEstado).not.toContain('Inscripción')
  })
  it('el estado pendiente se muestra como Pendiente', () => {
    expect(renderToStaticMarkup(<DatosDeportista deportista={{ ...paraAdmin, estadoInscripcion: 'PENDIENTE' }} categoria="Semillero" />)).toContain('>Pendiente<')
  })
})
