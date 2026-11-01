// Datos de ejemplo del horario semanal del club (2026): entrenadores, sedes y la plantilla de franjas. Solo datos: la lógica
// está en utils/horarioClub.js y el servicio en horarioClubService.js. Es una plantilla FIJA todo el año, de lunes a sábado.
// Las sedes y la asignación de entrenadores son datos de ejemplo editables, no datos reales del club.
//
//   Franja = { id, idCategoria, dia (1 = lunes … 6 = sábado), horaInicio "HH:mm", horaFin "HH:mm", sede, idEntrenador | null,
//              grupo ("mañana" | "tarde" | null) }
//   idEntrenador null = "Todos los entrenadores". grupo solo en Sub-9 y Sub-10 (dos grupos por categoría; el niño va a uno solo).
import { NOMBRES_CATEGORIAS } from '../utils/categorias.js'

export const SEDES = ['Horizontes', 'UVA El Paraíso']
export const GRUPOS = ['mañana', 'tarde']
export const NOMBRES_DIAS = { 1: 'Lunes', 2: 'Martes', 3: 'Miércoles', 4: 'Jueves', 5: 'Viernes', 6: 'Sábado' }

// Mismo id que usa deportistasService para cada categoría (el orden oficial de NOMBRES_CATEGORIAS, desde 1)
export const idCategoriaDe = (nombre) => NOMBRES_CATEGORIAS.indexOf(nombre) + 1
export const nombreDeCategoria = (idCategoria) => NOMBRES_CATEGORIAS[Number(idCategoria) - 1] ?? null

// Una categoría tiene un solo entrenador responsable; un entrenador puede tener varias. Párvulos y Cachorros no tienen uno fijo.
export const ENTRENADORES_CLUB = [
  { id: 1, nombre: 'Nicolás', categorias: ['Sub-9', 'Sub-12'] },
  { id: 2, nombre: 'Dairo', categorias: ['Sub-10'] },
  { id: 3, nombre: 'David Mesa', categorias: ['Sub-11'] },
  { id: 4, nombre: 'Jhon Cardona', categorias: ['Sub-13', 'Sub-14'] },
  { id: 5, nombre: 'Giovanny', categorias: ['Sub-15'] },
  { id: 6, nombre: 'Jhon Ramírez', categorias: ['Sub-16'] },
]
export const entrenadorDeCategoria = (nombre) => ENTRENADORES_CLUB.find((e) => e.categorias.includes(nombre))?.id ?? null

// Párvulos a Sub-11 en UVA El Paraíso; Sub-12 a Sub-16 en Horizontes (dato de ejemplo)
export const sedeDeCategoria = (nombre) => (idCategoriaDe(nombre) <= idCategoriaDe('Sub-11') ? 'UVA El Paraíso' : 'Horizontes')

// Las horas finales que no estaban en la hoja del club se asumieron de 1 hora (ver el reporte).
const TODOS = null
const franjasDe = (categoria, dias, horaInicio, horaFin, { entrenador = entrenadorDeCategoria(categoria), grupo = null } = {}) =>
  dias.map((dia) => ({ idCategoria: idCategoriaDe(categoria), dia, horaInicio, horaFin, sede: sedeDeCategoria(categoria), idEntrenador: entrenador, grupo }))

// Franjas de "Semillero" de Sub-9 y Sub-10 (sin entrenador fijo): dos grupos, mañana y tarde
const semillero = (categoria) => [
  ...franjasDe(categoria, [2], '07:00', '08:00', { entrenador: TODOS, grupo: 'mañana' }),
  ...franjasDe(categoria, [4], '07:00', '08:30', { entrenador: TODOS, grupo: 'mañana' }),
  ...franjasDe(categoria, [2], '16:30', '17:30', { entrenador: TODOS, grupo: 'tarde' }),
  ...franjasDe(categoria, [4], '16:30', '18:00', { entrenador: TODOS, grupo: 'tarde' }),
]

export const FRANJAS_EJEMPLO = [
  ...franjasDe('Párvulos', [2], '08:30', '09:30', { entrenador: TODOS }),
  ...franjasDe('Párvulos', [6], '08:30', '10:00', { entrenador: TODOS }),
  ...franjasDe('Cachorros', [2, 4], '07:00', '08:00', { entrenador: TODOS }),
  ...franjasDe('Cachorros', [2, 4], '16:30', '17:30', { entrenador: TODOS }),
  ...franjasDe('Cachorros', [6], '08:30', '10:00', { entrenador: TODOS }),
  ...franjasDe('Sub-9', [5], '19:00', '20:00'),
  ...franjasDe('Sub-9', [6], '08:30', '10:00'),
  ...semillero('Sub-9'),
  ...franjasDe('Sub-10', [1, 2, 5], '19:00', '20:00'),
  ...franjasDe('Sub-10', [6], '08:30', '10:00'),
  ...semillero('Sub-10'),
  ...franjasDe('Sub-11', [2], '20:00', '21:00'),
  ...franjasDe('Sub-11', [3, 4], '19:00', '20:00'),
  ...franjasDe('Sub-12', [1], '19:00', '20:00'),
  ...franjasDe('Sub-12', [2], '18:00', '19:00'),
  ...franjasDe('Sub-12', [4], '19:00', '20:00'),
  ...franjasDe('Sub-13', [1, 3, 5], '19:00', '20:00'),
  ...franjasDe('Sub-14', [1, 3, 5], '18:00', '19:00'),
  ...franjasDe('Sub-15', [1], '18:00', '19:00'),
  ...franjasDe('Sub-15', [2], '20:00', '21:00'),
  ...franjasDe('Sub-15', [4], '18:00', '19:00'),
  ...franjasDe('Sub-16', [2, 4], '16:30', '18:00'),
  ...franjasDe('Sub-16', [3], '18:00', '19:00'),
].map((franja, i) => ({ id: i + 1, ...franja }))
