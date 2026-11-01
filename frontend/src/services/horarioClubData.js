// Datos de ejemplo del horario semanal del club (2026): entrenadores, sedes y la plantilla de franjas. Solo datos: la lógica
// está en utils/horarioClub.js y el servicio en horarioClubService.js. Es una plantilla FIJA todo el año, de lunes a sábado.
// Las sedes y la asignación de entrenadores son datos de ejemplo editables, no datos reales del club.
//
//   Franja = { id, idCategoria, dia (1 = lunes … 6 = sábado), horaInicio "HH:mm", horaFin "HH:mm", sede, idEntrenador | null,
//              grupo ("mañana" | "tarde" | null) }
//   idEntrenador null = "Por turnos" (Párvulos, Cachorros y Semillero se turnan los entrenadores, sobre todo los sábados).
//   grupo solo en Cachorros y Semillero, martes y jueves: dos sesiones alternativas el mismo día (según el horario del colegio);
//   cada niño asiste a una sola. Todavía no hay asignación de niños a grupos.
//   La sede va POR FRANJA (se escribe explícita en cada una). Plantilla según el Excel oficial del club.
import { NOMBRES_CATEGORIAS } from '../utils/categorias.js'

export const SEDES = ['Horizontes', 'UVA El Paraíso']
export const GRUPOS = ['mañana', 'tarde']
export const NOMBRES_DIAS = { 1: 'Lunes', 2: 'Martes', 3: 'Miércoles', 4: 'Jueves', 5: 'Viernes', 6: 'Sábado' }

// Mismo id que usa deportistasService para cada categoría (el orden oficial de NOMBRES_CATEGORIAS, desde 1)
export const idCategoriaDe = (nombre) => NOMBRES_CATEGORIAS.indexOf(nombre) + 1
export const nombreDeCategoria = (idCategoria) => NOMBRES_CATEGORIAS[Number(idCategoria) - 1] ?? null

// Una categoría tiene un solo entrenador responsable; un entrenador puede tener varias (Jhon Cardona: Sub-13 y Sub-14).
// Párvulos, Cachorros y Semillero no tienen entrenador fijo: se turnan.
export const ENTRENADORES_CLUB = [
  { id: 1, nombre: 'Nicolás', categorias: ['Sub-12'] },
  { id: 2, nombre: 'David Mesa', categorias: ['Sub-11'] },
  { id: 3, nombre: 'Jhon Cardona', categorias: ['Sub-13', 'Sub-14'] },
  { id: 4, nombre: 'Giovanny', categorias: ['Sub-15'] },
  { id: 5, nombre: 'Jhon Ramírez', categorias: ['Sub-16'] },
]
export const entrenadorDeCategoria = (nombre) => ENTRENADORES_CLUB.find((e) => e.categorias.includes(nombre))?.id ?? null

const H = 'Horizontes'
const UVA = 'UVA El Paraíso'
const id = idCategoriaDe
const idEntrenador = entrenadorDeCategoria

// 30 franjas, escritas una por una (sede, día, hora y grupo explícitos). Días: 1 lunes … 6 sábado. Horas en 24 h.
export const FRANJAS_EJEMPLO = [
  // Párvulos (por turnos)
  { idCategoria: id('Párvulos'), dia: 2, horaInicio: '08:30', horaFin: '10:00', sede: UVA, idEntrenador: null, grupo: null },
  { idCategoria: id('Párvulos'), dia: 6, horaInicio: '09:00', horaFin: '10:00', sede: H, idEntrenador: null, grupo: null },
  // Cachorros (por turnos): martes y jueves en dos grupos (mañana / tarde), sábado sin grupo
  { idCategoria: id('Cachorros'), dia: 2, horaInicio: '07:30', horaFin: '09:00', sede: UVA, idEntrenador: null, grupo: 'mañana' },
  { idCategoria: id('Cachorros'), dia: 2, horaInicio: '16:30', horaFin: '18:00', sede: UVA, idEntrenador: null, grupo: 'tarde' },
  { idCategoria: id('Cachorros'), dia: 4, horaInicio: '07:30', horaFin: '09:00', sede: UVA, idEntrenador: null, grupo: 'mañana' },
  { idCategoria: id('Cachorros'), dia: 4, horaInicio: '16:30', horaFin: '18:00', sede: UVA, idEntrenador: null, grupo: 'tarde' },
  { idCategoria: id('Cachorros'), dia: 6, horaInicio: '08:30', horaFin: '10:00', sede: H, idEntrenador: null, grupo: null },
  // Semillero (por turnos): igual que Cachorros
  { idCategoria: id('Semillero'), dia: 2, horaInicio: '07:30', horaFin: '09:00', sede: UVA, idEntrenador: null, grupo: 'mañana' },
  { idCategoria: id('Semillero'), dia: 2, horaInicio: '16:30', horaFin: '18:00', sede: UVA, idEntrenador: null, grupo: 'tarde' },
  { idCategoria: id('Semillero'), dia: 4, horaInicio: '07:30', horaFin: '09:00', sede: UVA, idEntrenador: null, grupo: 'mañana' },
  { idCategoria: id('Semillero'), dia: 4, horaInicio: '16:30', horaFin: '18:00', sede: UVA, idEntrenador: null, grupo: 'tarde' },
  { idCategoria: id('Semillero'), dia: 6, horaInicio: '08:30', horaFin: '10:00', sede: H, idEntrenador: null, grupo: null },
  // Sub-11 (David Mesa)
  { idCategoria: id('Sub-11'), dia: 2, horaInicio: '20:00', horaFin: '21:00', sede: H, idEntrenador: idEntrenador('Sub-11'), grupo: null },
  { idCategoria: id('Sub-11'), dia: 3, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-11'), grupo: null },
  { idCategoria: id('Sub-11'), dia: 4, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-11'), grupo: null },
  // Sub-12 (Nicolás)
  { idCategoria: id('Sub-12'), dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-12'), grupo: null },
  { idCategoria: id('Sub-12'), dia: 2, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-12'), grupo: null },
  { idCategoria: id('Sub-12'), dia: 4, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-12'), grupo: null },
  // Sub-13 (Jhon Cardona)
  { idCategoria: id('Sub-13'), dia: 1, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-13'), grupo: null },
  { idCategoria: id('Sub-13'), dia: 3, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-13'), grupo: null },
  { idCategoria: id('Sub-13'), dia: 5, horaInicio: '19:00', horaFin: '20:00', sede: H, idEntrenador: idEntrenador('Sub-13'), grupo: null },
  // Sub-14 (Jhon Cardona)
  { idCategoria: id('Sub-14'), dia: 1, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-14'), grupo: null },
  { idCategoria: id('Sub-14'), dia: 3, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-14'), grupo: null },
  { idCategoria: id('Sub-14'), dia: 5, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-14'), grupo: null },
  // Sub-15 (Giovanny)
  { idCategoria: id('Sub-15'), dia: 1, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-15'), grupo: null },
  { idCategoria: id('Sub-15'), dia: 2, horaInicio: '20:00', horaFin: '21:00', sede: H, idEntrenador: idEntrenador('Sub-15'), grupo: null },
  { idCategoria: id('Sub-15'), dia: 4, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-15'), grupo: null },
  // Sub-16 (Jhon Ramírez)
  { idCategoria: id('Sub-16'), dia: 2, horaInicio: '16:30', horaFin: '18:00', sede: H, idEntrenador: idEntrenador('Sub-16'), grupo: null },
  { idCategoria: id('Sub-16'), dia: 3, horaInicio: '18:00', horaFin: '19:00', sede: H, idEntrenador: idEntrenador('Sub-16'), grupo: null },
  { idCategoria: id('Sub-16'), dia: 4, horaInicio: '16:30', horaFin: '18:00', sede: H, idEntrenador: idEntrenador('Sub-16'), grupo: null },
].map((franja, i) => ({ id: i + 1, ...franja }))
