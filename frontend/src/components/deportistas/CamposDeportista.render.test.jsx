import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import CamposDeportista from './CamposDeportista.jsx'
import CampoFechaNacimiento from '../ui/CampoFechaNacimiento.jsx'
import { TIPOS_DOCUMENTO_DEPORTISTA, aplicarCambio, validarDatosDeportista } from '../../services/deportistasService.js'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
})
afterEach(() => vi.useRealTimers())

const texto = (valores = {}) => (campo) => ({ value: valores[campo] ?? '', error: undefined, onChange: () => {} })
const dibujar = (valores = {}, props = {}) => renderToStaticMarkup(<CamposDeportista texto={texto(valores)} fechaNacimiento={valores.fechaNacimiento ?? ''} {...props} />)
const anios = (html) => [...html.match(/<select id="fechaNacimiento-anio".*?<\/select>/s)[0].matchAll(/<option value="(\d{4})"/g)].map((m) => Number(m[1]))

describe('CamposDeportista: fecha de nacimiento y categoría', () => {
  it('la fecha son tres listas día/mes/año (sin input date) y los años van de 2022 a 2010', () => {
    const html = dibujar()
    expect(html).not.toContain('type="date"')
    expect(html).toMatch(/id="fechaNacimiento-dia"/)
    expect(html).toMatch(/id="fechaNacimiento-mes"/)
    expect(anios(html)).toEqual([2022, 2021, 2020, 2019, 2018, 2017, 2016, 2015, 2014, 2013, 2012, 2011, 2010])
  })

  it('la categoría es de solo lectura: "—" sin fecha y "Semillero" para 2016-05-10', () => {
    const vacia = dibujar()
    expect(vacia).toMatch(/<input id="categoria"[^>]*readOnly=""[^>]*value="—"/)
    const html = dibujar({ fechaNacimiento: '2016-05-10' })
    expect(html).toMatch(/<input id="categoria"[^>]*readOnly=""[^>]*value="Semillero"/)
    expect(html).not.toMatch(/<select id="idCategoria"/)
    expect(html).toContain('Se asigna automáticamente según el año de nacimiento.')
  })

  it('la edad sigue siendo la real cumplida (2016-12-31 con el reloj en 2026-10-22: 9 años)', () => {
    expect(dibujar({ fechaNacimiento: '2016-12-31' })).toMatch(/<input id="edad"[^>]*value="9 años"/)
  })

  it('la inscripción en línea (mostrarCategoria false) no muestra la categoría pero sí el rango de años', () => {
    const html = dibujar({ fechaNacimiento: '2016-05-10' }, { mostrarCategoria: false })
    expect(html).not.toContain('id="categoria"')
    expect(anios(html)[0]).toBe(2022)
  })

  it('un año fuera del rango que ya venga en el valor se muestra (la validación lo rechaza)', () => {
    expect(anios(dibujar({ fechaNacimiento: '2005-01-01' }))).toContain(2005)
  })
})

describe('CampoFechaNacimiento', () => {
  it('con la fecha completa deja elegidos día, mes y año', () => {
    const html = renderToStaticMarkup(<CampoFechaNacimiento label="Fecha" id="f" value="2016-05-10" onChange={() => {}} anioMinimo={2010} anioMaximo={2022} />)
    expect(html).toContain('<option value="10" selected="">10</option>')
    expect(html).toContain('<option value="5" selected="">Mayo</option>')
    expect(html).toContain('<option value="2016" selected="">2016</option>')
  })

  it('febrero de un año no bisiesto tiene 28 días', () => {
    const html = renderToStaticMarkup(<CampoFechaNacimiento label="Fecha" id="f" value="2017-02-10" onChange={() => {}} anioMinimo={2010} anioMaximo={2022} />)
    expect(html).not.toContain('<option value="29"')
    expect(html).toContain('<option value="28"')
  })

  it('marca el error y el campo obligatorio', () => {
    const html = renderToStaticMarkup(<CampoFechaNacimiento label="Fecha" id="f" value="" onChange={() => {}} anioMinimo={2010} anioMaximo={2022} error="Falta" requerido />)
    expect(html.match(/aria-invalid="true"/g)).toHaveLength(3)
    expect(html.match(/aria-required="true"/g)).toHaveLength(3)
    expect(html).toContain('id="f-error"')
  })
})

describe('tipo de documento del deportista', () => {
  it('las opciones no incluyen Cédula de ciudadanía y la ayuda explica la tarjeta de identidad', () => {
    const html = dibujar()
    expect(html).not.toContain('Cédula de ciudadanía')
    for (const t of TIPOS_DOCUMENTO_DEPORTISTA) expect(html).toContain(`<option value="${t}"`)
    expect(html).toContain('Sugerido: tarjeta de identidad. Es obligatoria desde los 14 años.')
  })

  it('con 14 años o más y Registro civil muestra el aviso; con menos, o con otro tipo, no', () => {
    const aviso = 'A partir de los 14 años se espera tarjeta de identidad.'
    expect(dibujar({ fechaNacimiento: '2012-05-10', tipoDocumento: 'Registro civil' })).toContain(aviso) // 14 años
    expect(dibujar({ fechaNacimiento: '2013-12-31', tipoDocumento: 'Registro civil' })).not.toContain(aviso) // 12 años cumplidos
    expect(dibujar({ fechaNacimiento: '2010-05-10', tipoDocumento: 'Tarjeta de identidad' })).not.toContain(aviso)
  })

  it('al completar la fecha con el tipo vacío se sugiere Tarjeta de identidad; si ya hay tipo, se respeta', () => {
    expect(aplicarCambio({ tipoDocumento: '' }, 'fechaNacimiento', '2016-05-10').tipoDocumento).toBe('Tarjeta de identidad')
    expect(aplicarCambio({ tipoDocumento: 'Pasaporte' }, 'fechaNacimiento', '2016-05-10').tipoDocumento).toBe('Pasaporte')
    expect(aplicarCambio({ tipoDocumento: '' }, 'fechaNacimiento', '').tipoDocumento).toBe('')
    expect(aplicarCambio({ tipoDocumento: '' }, 'nombre', 'Lucas').tipoDocumento).toBe('')
  })
})

describe('validación del rango de años y del tipo de documento', () => {
  const base = { nombre: 'Lucas', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento: '2016-05-10', telefono: '3001234567', direccion: 'Calle 50 # 10-20' }

  it('2010 y 2022 pasan; 2009 y 2023 se rechazan con los años calculados en el mensaje', () => {
    expect(validarDatosDeportista({ ...base, fechaNacimiento: '2010-01-01' })).toEqual({})
    expect(validarDatosDeportista({ ...base, fechaNacimiento: '2022-12-31' })).toEqual({})
    for (const f of ['2009-12-31', '2023-01-01']) {
      expect(validarDatosDeportista({ ...base, fechaNacimiento: f })).toEqual({ fechaNacimiento: 'Solo se inscriben deportistas nacidos entre 2010 y 2022.' })
    }
  })

  it('con el reloj en 2027 el mensaje habla de 2011 y 2023', () => {
    vi.setSystemTime(new Date(2027, 5, 1, 12, 0))
    expect(validarDatosDeportista({ ...base, fechaNacimiento: '2010-01-01' }).fechaNacimiento).toBe('Solo se inscriben deportistas nacidos entre 2011 y 2023.')
    expect(validarDatosDeportista({ ...base, fechaNacimiento: '2023-01-01' })).toEqual({})
  })

  it('la cédula de ciudadanía se rechaza con el mensaje de tipo de documento que ya existía', () => {
    expect(validarDatosDeportista({ ...base, tipoDocumento: 'Cédula de ciudadanía' })).toEqual({ tipoDocumento: 'Selecciona el tipo de documento.' })
  })
})
