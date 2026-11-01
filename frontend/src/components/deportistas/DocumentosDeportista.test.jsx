import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { PanelDocumentos } from './DocumentosDeportista.jsx'
import { hoyLocal } from '../../utils/fechas.js'

const base = {
  idDeportista: 1, tipo: 'DOCUMENTO_IDENTIDAD', nombreOriginal: 'registro-civil-ejemplo.pdf', tipoContenido: 'application/pdf', tamanoBytes: 245 * 1024,
  comentario: null, fechaVencimiento: null, cargadoPor: 'administrador@pradofc.co', cargadoEn: '2026-10-05T14:30:00.000Z', revisadoPor: null, revisadoEn: null,
}
const pendiente = { ...base, id: 2, tipo: 'CERTIFICADO_ESTUDIO', nombreOriginal: 'ficha.jpg', tamanoBytes: Math.round(1.8 * 1024 * 1024), estado: 'PENDIENTE', fechaVencimiento: '2027-03-01' }
const aprobado = { ...base, id: 1, estado: 'APROBADO' }
const rechazado = { ...base, id: 3, tipo: 'PAZ_Y_SALVO', nombreOriginal: 'permiso.png', estado: 'RECHAZADO', comentario: 'La imagen está borrosa.' }
const dibujar = (props = {}) => renderToStaticMarkup(<PanelDocumentos documentos={[]} onSubir={async () => {}} onRevisar={async () => {}} onReintentar={() => {}} {...props} />)

describe('PanelDocumentos: lista', () => {
  it('lista vacía: mensaje de estado vacío y el formulario sigue disponible', () => {
    const html = dibujar()
    expect(html).toContain('Todavía no hay documentos de este deportista.')
    expect(html).not.toContain('aria-label="Documentos del deportista"')
    expect(html).toContain('Subir documento')
  })

  it('cargando y con error de carga', () => {
    expect(dibujar({ documentos: null })).toContain('Cargando los documentos…')
    const html = dibujar({ documentos: null, error: true })
    expect(html).toContain('No se pudieron cargar los documentos.')
    expect(html).toContain('Reintentar')
    expect(html).not.toContain('Cargando')
  })

  it('cada documento muestra tipo legible, nombre, tamaño, fecha de carga, vencimiento y estado', () => {
    const html = dibujar({ documentos: [pendiente, aprobado, rechazado] })
    for (const texto of ['Certificado de estudio', 'Documento de identidad', 'Paz y salvo', 'ficha.jpg', '1,8 MB', '245 KB', 'registro-civil-ejemplo.pdf']) expect(html).toContain(texto)
    expect(html).toContain('Cargado el')
    expect(html).toContain('5 de octubre de 2026')
    expect(html).toContain('Vence el')
    expect(html).toContain('1 de marzo de 2027')
    expect(html.match(/Vence el/g)).toHaveLength(1)
  })

  it('el estado va en un Badge: Pendiente = warning, Aprobado = success, Rechazado = danger', () => {
    const html = dibujar({ documentos: [pendiente, aprobado, rechazado] })
    expect(html).toMatch(/bg-warning text-warning[^>]*>.*?Pendiente/s)
    expect(html).toMatch(/bg-success text-success[^>]*>.*?Aprobado/s)
    expect(html).toMatch(/bg-danger text-danger[^>]*>.*?Rechazado/s)
  })

  it('el comentario del administrador solo se muestra si fue rechazado', () => {
    expect(dibujar({ documentos: [rechazado] })).toContain('Comentario del administrador: La imagen está borrosa.')
    expect(dibujar({ documentos: [{ ...aprobado, comentario: 'Todo bien' }] })).not.toContain('Comentario del administrador')
  })

  it('"Aprobar" y "Rechazar" solo aparecen en los pendientes, con objetivos táctiles de 44 px', () => {
    const html = dibujar({ documentos: [pendiente, aprobado, rechazado] })
    expect(html.match(/>Aprobar<\/button>/g)).toHaveLength(1)
    expect(html.match(/>Rechazar<\/button>/g)).toHaveLength(1)
    expect(html).toContain('min-h-11')
    expect(html).not.toContain('Motivo del rechazo') // el campo de comentario aparece al pulsar "Rechazar"
  })

  it('el aviso de éxito va en un contenedor role="status"', () => {
    const html = dibujar({ exito: { texto: 'Documento subido. Queda pendiente de revisión.' } })
    expect(html).toMatch(/role="status"[^>]*>.*Documento subido/s)
  })
})

describe('PanelDocumentos: formulario "Subir documento"', () => {
  it('tipo obligatorio con asterisco, archivo PDF/JPG/PNG de máximo 5 MB y vencimiento opcional con min = hoy', () => {
    const html = dibujar()
    expect(html).toContain('* Campo obligatorio')
    expect(html).toMatch(/<label[^>]*for="documento-tipo"[^>]*>Tipo de documento<span[^>]*aria-hidden="true"[^>]*> \*<\/span><\/label>/)
    for (const etiqueta of ['Documento de identidad', 'Certificado de estudio', 'Formulario de inscripción', 'Habeas Data', 'Paz y salvo', 'Carta de renuncia', 'Traspaso nacional']) {
      expect(html).toContain(`>${etiqueta}</option>`)
    }
    expect(html).not.toContain('Ficha médica')
    expect(html).not.toMatch(/>Foto tipo documento<\/option>/) // la foto es la de perfil, no se sube como documento
    expect(html.match(/<option value="[A-Z_]+">/g)).toHaveLength(7)
    expect(html).toContain('Elige el tipo para ver qué documento se espera.') // la descripción larga aparece como ayuda al elegir
    expect(html).toMatch(/<input id="documento-archivo"[^>]*aria-required="true"[^>]*type="file"[^>]*accept="\.pdf,application\/pdf,image\/jpeg,image\/png"/)
    expect(html).toContain('Archivo (PDF, JPG o PNG, máximo 5 MB)')
    expect(html).toContain('Fecha de vencimiento (opcional)')
    expect(html).toMatch(new RegExp(`<input id="documento-vencimiento"[^>]*type="date"[^>]*min="${hoyLocal()}"`))
    expect(html).not.toMatch(/documento-vencimiento"[^>]*aria-required/)
    expect(html).toContain('>Subir documento</button>')
  })
})

describe('PanelDocumentos: lista de verificación', () => {
  const filas = (html) => html.match(/<li class="flex flex-wrap items-center justify-between gap-2 rounded-md border/g)

  it('muestra el resumen y los 8 requisitos (7 documentos y la foto) con su estado y la marca Obligatorio / Si aplica', () => {
    const html = dibujar({ documentos: [pendiente, aprobado, rechazado] })
    expect(html).toContain('Lista de verificación')
    expect(html).toContain('1 de 5 requisitos obligatorios completos')
    expect(filas(html)).toHaveLength(8)
    expect(html.match(/>Obligatorio</g)).toHaveLength(5)
    expect(html.match(/>Si aplica</g)).toHaveLength(3)
    expect(html).toContain('>Foto tipo documento<')
    expect(html).not.toContain('Ficha médica')
  })

  it('sin documentos ni foto: 0 de 5, todo sin cargar y la fila de la foto lleva a la tarjeta de Foto', () => {
    const html = dibujar()
    expect(html).toContain('0 de 5 requisitos obligatorios completos')
    expect(html.match(/>Sin cargar</g)).toHaveLength(8)
    expect(html).toContain('href="#foto-deportista"')
    expect(html.match(/href="#foto-deportista"/g)).toHaveLength(1)
  })

  it('con foto de perfil la fila dice "Cargada", cuenta en el resumen y ya no enlaza', () => {
    const html = dibujar({ documentos: [aprobado], tieneFoto: true })
    expect(html).toContain('2 de 5 requisitos obligatorios completos')
    expect(html).toContain('>Cargada<')
    expect(html).not.toContain('href="#foto-deportista"')
    expect(html.match(/>Sin cargar</g)).toHaveLength(6)
  })

  it('cargando o con error de carga no muestra la lista de verificación', () => {
    expect(dibujar({ documentos: null })).not.toContain('Lista de verificación')
    expect(dibujar({ documentos: null, error: true })).not.toContain('Lista de verificación')
  })
})

describe('PanelDocumentos: quién subió cada documento', () => {
  it('la ficha muestra "Cargado por: Administrador" o "Acudiente"', () => {
    const html = dibujar({ documentos: [{ ...aprobado, cargadoPor: 'Administrador' }, { ...pendiente, cargadoPor: 'Acudiente' }] })
    expect(html).toContain('Cargado por: Administrador')
    expect(html).toContain('Cargado por: Acudiente')
  })
})
