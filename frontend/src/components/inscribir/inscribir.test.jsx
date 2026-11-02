import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import IndicadorPasos from './IndicadorPasos.jsx'
import PasoJugador from './PasoJugador.jsx'
import PasoAcudiente from './PasoAcudiente.jsx'
import PasoDocumentos from './PasoDocumentos.jsx'
import PasoConfirmar from './PasoConfirmar.jsx'
import DocumentosDelAcudiente from '../deportistas/DocumentosDelAcudiente.jsx'
import { ACUDIENTE_VACIO } from '../../services/acudientesService.js'
import { DEPORTISTA_VACIO } from '../../services/deportistasService.js'
import RedirigirAInscribir, { RUTA_INSCRIBIR } from '../../pages/RedirigirAInscribir.jsx'
import { extraScreens } from '../../pages/index.js'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 9, 22, 12, 0))
})
afterEach(() => vi.useRealTimers())

const archivo = (extra = {}) => ({ name: 'doc.pdf', type: 'application/pdf', size: 245 * 1024, ...extra })
const jugador = { ...DEPORTISTA_VACIO, nombre: 'Lucas', apellido: 'Pérez', tipoDocumento: 'Tarjeta de identidad', documento: '1200000001', fechaNacimiento: '2017-05-10', telefono: '3001234567', direccion: 'Calle 50 # 10-20', grupoEntrenamiento: 'tarde', posicion: 'Portero' }
const acudiente = { nombre: 'Claudia', apellido: 'Ruiz', correo: 'claudia@correo.com', telefono: '3001234567', parentesco: 'Madre' }
const sinLlenar = () => {}

describe('indicador de pasos', () => {
  it('lista ordenada con los 4 pasos y aria-current solo en el actual', () => {
    for (const paso of [1, 2, 3, 4]) {
      const html = renderToStaticMarkup(<IndicadorPasos paso={paso} />)
      expect(html).toContain('aria-label="Pasos de la inscripción"')
      for (const nombre of ['Jugador', 'Acudiente', 'Documentos', 'Confirmar']) expect(html).toContain(`>${nombre}</span>`)
      expect(html.match(/aria-current="step"/g)).toHaveLength(1)
      expect(html).toMatch(new RegExp(`aria-current="step"[^>]*>\\s*<span[^>]*>${paso}</span>`))
    }
  })
})

describe('paso 1: Jugador', () => {
  const html = renderToStaticMarkup(<PasoJugador jugador={DEPORTISTA_VACIO} errores={{}} onCambio={sinLlenar} />)

  it('formulario en blanco con la leyenda "* Campo obligatorio" y asterisco en los obligatorios', () => {
    expect(html).toContain('* Campo obligatorio')
    for (const etiqueta of ['Nombres', 'Apellidos', 'Tipo de documento', 'Número de documento', 'Teléfono de contacto', 'Dirección']) {
      expect(html).toMatch(new RegExp(`<label[^>]*>${etiqueta}<span[^>]*aria-hidden="true"[^>]*> \\*</span></label>`))
    }
    expect(html).not.toMatch(/Nicolás Mejía|value="[^"]+"[^>]*id="nombre"/)
  })

  it('reutiliza CamposDeportista: fecha en tres selectores, categoría de solo lectura y posición opcional', () => {
    expect(html).toContain('id="fechaNacimiento-dia"')
    expect(html).toContain('id="fechaNacimiento-anio"')
    expect(html).toMatch(/<input id="categoria"[^>]*readOnly=""/)
    expect(html).toContain('Posición (opcional)')
    expect(html).not.toContain('Categoría (según edad)')
    expect(html).not.toContain('EPS')
  })

  it('el grupo de entrenamiento solo aparece en Cachorros y Semillero', () => {
    expect(html).not.toContain('Grupo de entrenamiento')
    const semillero = renderToStaticMarkup(<PasoJugador jugador={jugador} errores={{}} onCambio={sinLlenar} />)
    expect(semillero).toContain('Grupo de entrenamiento')
    expect(semillero).toMatch(/<input id="categoria"[^>]*value="Semillero"/)
  })

  it('muestra los errores del paso con aria-invalid', () => {
    const conErrores = renderToStaticMarkup(<PasoJugador jugador={DEPORTISTA_VACIO} errores={{ nombre: 'Escribe los nombres.' }} onCambio={sinLlenar} />)
    expect(conErrores).toContain('Escribe los nombres.')
    expect(conErrores).toMatch(/aria-invalid="true"/)
  })
})

describe('paso 2: Acudiente', () => {
  it('nombres, apellidos, correo, teléfono y parentesco (select con las 5 opciones), todos obligatorios', () => {
    const html = renderToStaticMarkup(<PasoAcudiente acudiente={ACUDIENTE_VACIO} errores={{}} onCambio={sinLlenar} />)
    expect(html).toContain('* Campo obligatorio')
    expect(html.match(/aria-required="true"/g)).toHaveLength(5)
    for (const p of ['Madre', 'Padre', 'Abuelo', 'Abuela', 'Tía', 'Tío', 'Otro']) expect(html).toContain(`>${p}</option>`)
    expect(html).toContain('type="email"')
    expect(html).not.toMatch(/Claudia Ruiz|claudia\.ruiz/)
  })
})

describe('paso 3: Documentos', () => {
  const dibujar = (props = {}) => renderToStaticMarkup(<PasoDocumentos documentos={{}} foto={null} nombreJugador="Lucas Pérez" errores={{}} onDocumento={sinLlenar} onFoto={sinLlenar} {...props} />)

  it('explica que es opcional y lista los 7 tipos con Obligatorio / Si aplica, más la foto', () => {
    const html = dibujar()
    expect(html).toContain('Puedes subirlos ahora o después: el administrador o el acudiente pueden hacerlo desde la ficha del deportista.')
    for (const nombre of ['Documento de identidad', 'Certificado de estudio', 'Formulario de inscripción', 'Habeas Data', 'Paz y salvo', 'Carta de renuncia', 'Traspaso nacional']) {
      expect(html).toContain(`>${nombre}</h3>`)
    }
    expect(html.match(/<input id="documento-[A-Z_]+"[^>]*type="file"/g)).toHaveLength(7)
    expect(html.match(/>Obligatorio</g)).toHaveLength(5) // 4 documentos + la foto
    expect(html.match(/>Si aplica</g)).toHaveLength(3)
    expect(html).toContain('accept=".pdf,application/pdf,image/jpeg,image/png"')
    expect(html).toContain('Archivo (PDF, JPG o PNG, máximo 5 MB)')
  })

  it('la "Foto tipo documento" es la foto de perfil: usa SubidaFoto (JPG o PNG) y no es un tipo de documento', () => {
    const html = dibujar()
    expect(html).toContain('Foto tipo documento')
    expect(html).toContain('Es la foto de perfil del jugador')
    expect(html).toContain('accept="image/jpeg,image/png"')
    expect(html).not.toContain('documento-FOTO')
  })

  it('la foto se adjunta al elegirla (sin "Guardar foto"), con "Quitar foto" visible y el estado "Foto adjunta: nombre"', () => {
    const html = dibujar({ foto: archivo({ name: 'foto.jpg', type: 'image/jpeg' }), fotoPrevia: 'blob:previa' })
    expect(html).toContain('Foto adjunta: foto.jpg')
    expect(html).toContain('Quitar foto')
    expect(html).not.toContain('Guardar foto')
    expect(dibujar()).not.toContain('Foto adjunta')
  })

  it('con foto elegida muestra su vista previa; con un documento muestra el nombre, el tamaño y "Quitar"', () => {
    const html = dibujar({ foto: archivo({ name: 'foto.png', type: 'image/png' }), fotoPrevia: 'blob:previa', documentos: { HABEAS: undefined, AUTORIZACION_HABEAS_DATA: archivo({ name: 'habeas.pdf' }) } })
    expect(html).toContain('src="blob:previa"')
    expect(html).toContain('habeas.pdf')
    expect(html).toContain('245 KB')
    expect(html).toContain('>Quitar<')
    expect(html.match(/<input id="documento-[A-Z_]+"[^>]*type="file"/g)).toHaveLength(6)
  })

  it('muestra el error de un archivo en su fila', () => {
    expect(dibujar({ errores: { 'documento-PAZ_Y_SALVO': 'El archivo pesa 6 MB; el máximo es 5 MB.' } })).toContain('El archivo pesa 6 MB')
  })
})

describe('paso 4: Confirmar (resumen)', () => {
  const dibujar = (props = {}) => renderToStaticMarkup(<PasoConfirmar jugador={jugador} acudiente={acudiente} documentos={{}} foto={null} onEditar={sinLlenar} {...props} />)

  it('resume jugador (con categoría, grupo y posición), acudiente y documentos, con un "Editar" por sección', () => {
    const html = dibujar({ documentos: { PAZ_Y_SALVO: archivo({ name: 'paz.pdf' }) }, foto: archivo({ name: 'foto.png', type: 'image/png' }) })
    for (const texto of ['Lucas Pérez', 'Tarjeta de identidad 1200000001', 'Semillero', 'tarde', 'Portero', 'Claudia Ruiz', 'Madre', 'claudia@correo.com', 'paz.pdf', 'foto.png']) expect(html).toContain(texto)
    expect(html).toContain('Grupo de entrenamiento')
    expect(html.match(/>Editar</g)).toHaveLength(3)
    expect(html).toContain('aria-label="Editar jugador"')
    expect(html).toContain('aria-label="Editar acudiente"')
    expect(html).toContain('aria-label="Editar documentos"')
  })

  it('sin posición dice "Sin definir", sin grupo en otras categorías, y sin documentos lo avisa', () => {
    const html = dibujar({ jugador: { ...jugador, fechaNacimiento: '2012-05-10', grupoEntrenamiento: '', posicion: '' } })
    expect(html).toContain('Sub-14')
    expect(html).toContain('Sin definir')
    expect(html).not.toContain('Grupo de entrenamiento')
    expect(html).toContain('No adjuntaste documentos')
    expect(html).toContain('Foto tipo documento: Sin adjuntar')
  })
})

describe('accesibilidad y diseño adaptable', () => {
  const todo = [
    renderToStaticMarkup(<IndicadorPasos paso={2} />),
    renderToStaticMarkup(<PasoJugador jugador={jugador} errores={{}} onCambio={sinLlenar} />),
    renderToStaticMarkup(<PasoAcudiente acudiente={acudiente} errores={{}} onCambio={sinLlenar} />),
    renderToStaticMarkup(<PasoDocumentos documentos={{ PAZ_Y_SALVO: archivo() }} foto={null} nombreJugador="Lucas" errores={{}} onDocumento={sinLlenar} onFoto={sinLlenar} />),
    renderToStaticMarkup(<PasoConfirmar jugador={jugador} acudiente={acudiente} documentos={{}} foto={null} onEditar={sinLlenar} />),
  ].join('')

  it('sin scroll horizontal: nada fuerza ancho (sin overflow-x ni anchos fijos grandes) y los textos largos se parten', () => {
    expect(todo).not.toMatch(/overflow-x|min-w-\[|w-\[(?:[2-9]\d|\d{3})/)
    expect(todo).toContain('break-words')
    expect(todo).toContain('grid-cols-2') // el indicador baja a 2 columnas en pantallas angostas
    expect(todo).toContain('minmax(min(100%,14rem),1fr)') // las columnas pasan a una cuando no caben (320 px o letra al 200 %)
  })

  it('los botones y las etiquetas de archivo miden al menos 44 px (min-h-11)', () => {
    expect(todo).toContain('min-h-11')
    expect(todo.match(/<button/g).length).toBeGreaterThan(0)
    for (const boton of todo.match(/<button[^>]*>/g)) expect(boton, boton).toContain('min-h-11')
  })

  it('los colores salen de los tokens del tema (sirven en modo oscuro), no de colores fijos', () => {
    expect(todo).not.toMatch(/#[0-9a-fA-F]{3,6}\b|text-(white|black)|bg-(white|black)|text-gray|bg-gray/)
  })
})

describe('/administrador/deportistas/nuevo redirige al asistente', () => {
  it('la ruta vieja existe y apunta a una redirección (reemplazando el historial) a la ruta única', () => {
    expect(RUTA_INSCRIBIR).toBe('/administrador/deportistas/inscribir')
    const rutas = Object.fromEntries(extraScreens.administrador.map((r) => [r.path, r.Screen]))
    expect(rutas['/administrador/deportistas/nuevo']).toBe(RedirigirAInscribir)
    expect(rutas['/administrador/deportistas/inscribir']).toBeTruthy()
    const elemento = RedirigirAInscribir()
    expect(elemento.props).toMatchObject({ to: '/administrador/deportistas/inscribir', replace: true })
  })
})

describe('documentos del acudiente: quién los subió', () => {
  const doc = (extra) => ({ id: 1, idDeportista: 1, tipo: 'CERTIFICADO_ESTUDIO', nombreOriginal: 'c.pdf', tipoContenido: 'application/pdf', tamanoBytes: 1024, estado: 'PENDIENTE', comentario: null, cargadoPor: 'Acudiente', cargadoEn: '2026-10-20T15:10:00.000Z', ...extra })

  it('cada documento dice "Cargado por: Administrador" o "Acudiente"; hay lista de verificación y formulario con los 7 tipos', () => {
    const html = renderToStaticMarkup(<DocumentosDelAcudiente jugadores={[{ id: 1, nombre: 'Santiago Ríos', tieneFoto: false, documentos: [doc(), doc({ id: 2, tipo: 'DOCUMENTO_IDENTIDAD', cargadoPor: 'Administrador' })] }]} onSubir={sinLlenar} />)
    expect(html).toContain('Cargado por: Acudiente')
    expect(html).toContain('Cargado por: Administrador')
    expect(html).toContain('Lista de verificación')
    expect(html.match(/<option value="[A-Z_]+">/g)).toHaveLength(7)
    expect(html).not.toContain('>Aprobar<') // aprobar y rechazar son solo del administrador
  })
})
