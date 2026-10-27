// Círculo con iniciales; el nombre ya está en el texto de al lado, por eso es decorativo.
export default function Avatar({ name }) {
  const initials = name.split(' ').map((word) => word[0]).slice(0, 2).join('')
  return (
    <span
      aria-hidden="true"
      className="flex min-h-10 min-w-10 shrink-0 items-center justify-center rounded-full bg-brand-primary text-body-medium text-on-dark"
    >
      {initials}
    </span>
  )
}
