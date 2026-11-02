// Traducción entre la franja del backend (FranjaHorarioRequestDTO / ResponseDTO de /api/horario-club) y la que usan las pantallas.
// Funciones puras, sin red. Ver backend/README.md, sección "Horario del club".
//
//   Franja (pantalla) = { id, idCategoria, categoria, dia (1 = lunes … 6), horaInicio "HH:mm", horaFin "HH:mm", sede,
//                         idEntrenador | null, entrenador | null, entrenadorActivo | null, grupo | null }
//   En modo API los ids son UUID y vienen el nombre de la categoría y el del entrenador (los roles que no son administrador no
//   pueden listar entrenadores: solo cuentan con lo que trae la franja). Sin entrenador (idEntrenador null) = "Por turnos".

export const MENSAJE_CRUCE = 'Ese entrenador ya tiene otra franja que se cruza con este horario'

const vacioANull = (valor) => (valor === '' || valor === null || valor === undefined ? null : valor)

export function adaptarFranja(dto) {
  return {
    id: dto.idFranja,
    idCategoria: dto.idCategoria,
    categoria: dto.categoria ?? null,
    dia: Number(dto.dia),
    horaInicio: dto.horaInicio,
    horaFin: dto.horaFin,
    sede: dto.sede,
    idEntrenador: dto.idEntrenador ?? null,
    entrenador: dto.entrenador ?? null,
    entrenadorActivo: dto.idEntrenador ? dto.entrenadorActivo !== false : null,
    grupo: dto.grupo ?? null,
  }
}

// Cuerpo de POST y PUT. El PUT reemplaza TODOS los campos: siempre se mandan los siete (idEntrenador y grupo en null si están vacíos).
// Las horas van como HH:mm (el campo de hora del navegador puede traer segundos).
export function cuerpoFranja(datos) {
  const hora = (h) => String(h ?? '').trim().slice(0, 5)
  const dia = datos.dia === '' || datos.dia === null || datos.dia === undefined ? null : Number(datos.dia)
  return {
    idCategoria: vacioANull(datos.idCategoria),
    dia,
    horaInicio: hora(datos.horaInicio),
    horaFin: hora(datos.horaFin),
    sede: vacioANull(datos.sede),
    idEntrenador: vacioANull(datos.idEntrenador),
    grupo: vacioANull(datos.grupo),
  }
}

// Query de GET /api/horario-club: solo los filtros con valor (categoria = nombre, entrenador = UUID, sede = nombre exacto)
export function consultaFranjas({ categoria, entrenador, sede } = {}) {
  const query = new URLSearchParams()
  for (const [clave, valor] of Object.entries({ categoria, entrenador, sede })) if (valor) query.set(clave, String(valor))
  const texto = query.toString()
  return texto ? `?${texto}` : ''
}
