import { useNavigate } from 'react-router-dom'
import { useRole } from '../context/RoleContext.js'
import { roles, roleLabels } from '../config/menus.js'

// SOLO DESARROLLO: cambia el rol activo hasta que exista el login.
export default function RoleSwitcher() {
  const { role, setRole } = useRole()
  const navigate = useNavigate()

  if (!import.meta.env.DEV) return null

  const onChange = (event) => {
    setRole(event.target.value)
    navigate(`/${event.target.value}`)
  }

  return (
    <label className="mb-4 flex flex-wrap items-center gap-2 text-small text-secondary">
      Rol (solo desarrollo)
      <select
        value={role}
        onChange={onChange}
        className="min-h-11 rounded-md border bg-surface px-2 text-body text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
      >
        {roles.map((r) => (
          <option key={r} value={r}>
            {roleLabels[r]}
          </option>
        ))}
      </select>
    </label>
  )
}
