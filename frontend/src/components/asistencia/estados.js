// Estados de asistencia con su texto, ícono y colores (nunca solo color: siempre texto + ícono).
export const ESTADOS_ASISTENCIA = {
  presente: { texto: 'Presente', plural: 'Presentes', icono: 'check', clases: 'border-success bg-success text-success' },
  ausente: { texto: 'Ausente', plural: 'Ausentes', icono: 'close', clases: 'border-danger bg-danger text-danger' },
  justificado: { texto: 'Justificado', plural: 'Justificados', icono: 'alert', clases: 'border-warning bg-warning text-warning' },
}
export const ORDEN_ESTADOS = ['presente', 'ausente', 'justificado']
