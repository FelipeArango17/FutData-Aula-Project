import { describe, expect, it } from 'vitest'
import {
  DESCRIPCIONES_TIPO_DOCUMENTO, IDS_OBLIGATORIOS, IDS_TIPOS_DOCUMENTO, NOMBRES_TIPO_DOCUMENTO, TIPOS_DOCUMENTO_CLUB, TOTAL_REQUISITOS_OBLIGATORIOS, estadoDelTipo, listaDeVerificacion, resumenObligatorios,
} from './documentosClub.js'

const doc = (id, tipo, estado, cargadoEn = '2026-10-05T14:30:00.000Z') => ({ id, tipo, estado, cargadoEn })

describe('tipos de documento del club', () => {
  it('son 7 los que se pueden subir: 4 obligatorios y 3 si aplica, sin la foto', () => {
    expect(IDS_TIPOS_DOCUMENTO).toEqual([
      'DOCUMENTO_IDENTIDAD', 'CERTIFICADO_ESTUDIO', 'FORMULARIO_INSCRIPCION', 'AUTORIZACION_HABEAS_DATA', 'PAZ_Y_SALVO', 'CARTA_RENUNCIA', 'TRASPASO_NACIONAL',
    ])
    expect(IDS_TIPOS_DOCUMENTO).not.toContain('FOTO_DOCUMENTO')
    expect(IDS_OBLIGATORIOS).toEqual(IDS_TIPOS_DOCUMENTO.slice(0, 4))
    expect(TIPOS_DOCUMENTO_CLUB.filter((t) => !t.obligatorio).map((t) => t.id)).toEqual(['PAZ_Y_SALVO', 'CARTA_RENUNCIA', 'TRASPASO_NACIONAL'])
    expect(TOTAL_REQUISITOS_OBLIGATORIOS).toBe(5)
  })

  it('usa nombres cortos en las listas y deja la descripción larga como ayuda', () => {
    expect(Object.values(NOMBRES_TIPO_DOCUMENTO)).toEqual([
      'Documento de identidad', 'Certificado de estudio', 'Formulario de inscripción', 'Habeas Data', 'Paz y salvo', 'Carta de renuncia', 'Traspaso nacional',
    ])
    expect(DESCRIPCIONES_TIPO_DOCUMENTO.DOCUMENTO_IDENTIDAD).toContain('fotocopia de la cédula, tarjeta de identidad o registro civil autenticado, según la edad')
    expect(DESCRIPCIONES_TIPO_DOCUMENTO.AUTORIZACION_HABEAS_DATA).toBe('Autorización de tratamiento de datos (Habeas Data)')
    expect(Object.values(NOMBRES_TIPO_DOCUMENTO).join(' ')).not.toMatch(/Ficha médica|Foto/)
  })
})

describe('estadoDelTipo', () => {
  it('null si no hay documentos de ese tipo; si hay varios manda el más reciente', () => {
    expect(estadoDelTipo([], 'FOTO_DOCUMENTO')).toBeNull()
    const docs = [doc(1, 'FOTO_DOCUMENTO', 'RECHAZADO', '2026-10-01T10:00:00.000Z'), doc(2, 'FOTO_DOCUMENTO', 'PENDIENTE', '2026-10-10T10:00:00.000Z')]
    expect(estadoDelTipo(docs, 'FOTO_DOCUMENTO')).toBe('PENDIENTE')
    expect(estadoDelTipo([...docs].reverse(), 'FOTO_DOCUMENTO')).toBe('PENDIENTE')
    expect(estadoDelTipo(docs, 'PAZ_Y_SALVO')).toBeNull()
  })
})

describe('resumenObligatorios', () => {
  const obligatorios = IDS_OBLIGATORIOS.map((tipo, i) => doc(i + 1, tipo, 'APROBADO'))

  it('cuenta los documentos obligatorios aprobados y la foto si existe, sobre 5', () => {
    expect(resumenObligatorios([])).toEqual({ completos: 0, total: 5, texto: '0 de 5 requisitos obligatorios completos' })
    const docs = [doc(1, 'DOCUMENTO_IDENTIDAD', 'APROBADO'), doc(2, 'CERTIFICADO_ESTUDIO', 'PENDIENTE'), doc(3, 'FORMULARIO_INSCRIPCION', 'RECHAZADO'), doc(4, 'PAZ_Y_SALVO', 'APROBADO')]
    expect(resumenObligatorios(docs).texto).toBe('1 de 5 requisitos obligatorios completos')
    expect(resumenObligatorios(docs, { tieneFoto: true }).texto).toBe('2 de 5 requisitos obligatorios completos')
    expect(resumenObligatorios([], { tieneFoto: true }).texto).toBe('1 de 5 requisitos obligatorios completos')
  })

  it('Mateo: 4 documentos obligatorios aprobados y foto cargada = 5 de 5; sin foto, 4 de 5; un si aplica no suma', () => {
    expect(resumenObligatorios(obligatorios, { tieneFoto: true }).texto).toBe('5 de 5 requisitos obligatorios completos')
    expect(resumenObligatorios(obligatorios).texto).toBe('4 de 5 requisitos obligatorios completos')
    expect(resumenObligatorios([...obligatorios, doc(9, 'TRASPASO_NACIONAL', 'APROBADO')]).completos).toBe(4)
  })
})

describe('listaDeVerificacion', () => {
  it('8 filas: los documentos en orden y la foto justo después del documento de identidad', () => {
    const lista = listaDeVerificacion([doc(1, 'DOCUMENTO_IDENTIDAD', 'APROBADO'), doc(2, 'AUTORIZACION_HABEAS_DATA', 'RECHAZADO'), doc(3, 'PAZ_Y_SALVO', 'PENDIENTE')])
    expect(lista.map((f) => f.id)).toEqual(['DOCUMENTO_IDENTIDAD', 'FOTO_PERFIL', ...IDS_TIPOS_DOCUMENTO.slice(1)])
    const por = Object.fromEntries(lista.map((f) => [f.id, f]))
    expect(por.DOCUMENTO_IDENTIDAD).toMatchObject({ estado: 'APROBADO', etiquetaEstado: 'Aprobado', marca: 'Obligatorio', esFoto: false })
    expect(por.AUTORIZACION_HABEAS_DATA).toMatchObject({ estado: 'RECHAZADO', etiquetaEstado: 'Rechazado', marca: 'Obligatorio' })
    expect(por.PAZ_Y_SALVO).toMatchObject({ estado: 'PENDIENTE', etiquetaEstado: 'Pendiente', marca: 'Si aplica' })
    expect(por.CERTIFICADO_ESTUDIO).toMatchObject({ estado: null, etiquetaEstado: 'Sin cargar', marca: 'Obligatorio' })
    expect(por.TRASPASO_NACIONAL).toMatchObject({ estado: null, etiquetaEstado: 'Sin cargar', marca: 'Si aplica' })
  })

  it('la fila "Foto tipo documento" sale de la foto de perfil: Cargada o Sin cargar, siempre Obligatorio', () => {
    const sinFoto = listaDeVerificacion([]).find((f) => f.esFoto)
    expect(sinFoto).toMatchObject({ nombre: 'Foto tipo documento', estado: null, etiquetaEstado: 'Sin cargar', marca: 'Obligatorio' })
    const conFoto = listaDeVerificacion([], { tieneFoto: true }).find((f) => f.esFoto)
    expect(conFoto).toMatchObject({ estado: 'CARGADA', etiquetaEstado: 'Cargada', marca: 'Obligatorio' })
    // un documento viejo con ese tipo no cambia la fila de la foto
    expect(listaDeVerificacion([doc(1, 'FOTO_DOCUMENTO', 'APROBADO')]).find((f) => f.esFoto).etiquetaEstado).toBe('Sin cargar')
  })
})
