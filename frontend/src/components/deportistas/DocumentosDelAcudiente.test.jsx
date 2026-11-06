import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import DocumentosDelAcudiente from './DocumentosDelAcudiente.jsx'

const doc = (extra) => ({
  id: '9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d', idDeportista: 'j1', tipo: 'CERTIFICADO_ESTUDIO', nombreOriginal: 'c.pdf', tipoContenido: 'application/pdf', tamanoBytes: 1024,
  estado: 'RECHAZADO', comentario: 'Falta la firma', cargadoPor: 'Acudiente', cargadoEn: '2026-11-01T15:10:00.000Z', ...extra,
})
const jugador = (extra = {}) => ({ id: 'j1', nombre: 'Luis Gómez', tieneFoto: true, documentos: [doc()], estadoInscripcion: 'PENDIENTE', documentosFaltantes: ['CERTIFICADO_ESTUDIO'], ...extra })
const dibujar = (j, props = {}) => renderToStaticMarkup(<DocumentosDelAcudiente jugadores={[j]} onSubir={async () => {}} {...props} />)

describe('DocumentosDelAcudiente (API)', () => {
  it('muestra el estado de la inscripción, lo que falta, el estado y el comentario del rechazo', () => {
    const html = dibujar(jugador())
    expect(html).toContain('Inscripción: Pendiente')
    expect(html).toContain('Faltan: Certificado de estudio')
    expect(html).toContain('Rechazado')
    expect(html).toContain('Comentario del administrador: Falta la firma')
    expect(html).toContain('puedes subir uno nuevo de este tipo')
  })
  it('puede volver a subir el tipo rechazado, pero no el que tiene un documento activo', () => {
    const html = dibujar(jugador({ documentos: [doc(), doc({ id: 'b', tipo: 'DOCUMENTO_IDENTIDAD', estado: 'PENDIENTE' })] }))
    expect(html).toContain('value="CERTIFICADO_ESTUDIO"')
    expect(html).not.toContain('value="DOCUMENTO_IDENTIDAD"')
  })
  it('"Ver documento" solo con onVer; el acudiente nunca ve Aprobar ni Rechazar', () => {
    expect(dibujar(jugador())).not.toContain('Ver documento')
    const html = dibujar(jugador(), { onVer: async () => {} })
    expect(html).toContain('>Ver documento<')
    expect(html).not.toContain('>Aprobar<')
    expect(html).not.toContain('>Rechazar<')
  })
  it('los 3 "Si aplica" se pueden subir y se marcan con texto', () => {
    const html = dibujar(jugador({ documentos: [] }))
    for (const tipo of ['PAZ_Y_SALVO', 'CARTA_RENUNCIA', 'TRASPASO_NACIONAL']) expect(html).toContain(`value="${tipo}"`)
    expect(html).toContain('(si aplica)')
  })
})
