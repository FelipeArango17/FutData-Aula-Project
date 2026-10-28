import Avatar from '../ui/Avatar.jsx'
import Card from '../ui/Card.jsx'
import EstadoChip from './EstadoChip.jsx'

// Un deportista en solo lectura: nombre, estado (texto + ícono) y observación si hay.
export default function FilaLectura({ nombre, estado, observacion }) {
  return (
    <Card as="li" className="flex flex-wrap items-start gap-3">
      <Avatar name={nombre} />
      <div className="flex min-w-0 grow basis-[14rem] flex-col gap-2">
        <p className="break-words text-body-medium">{nombre}</p>
        <div className="flex flex-wrap">
          <EstadoChip estado={estado} />
        </div>
        {observacion && <p className="break-words text-small text-secondary">Observación: {observacion}</p>}
      </div>
    </Card>
  )
}
