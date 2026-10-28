// Íconos SVG simples (trazo de 2 px, viewBox 24). Se pintan con el color del texto.
const paths = {
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'M6 6l12 12M18 6L6 18',
  home: 'M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10',
  users: 'M9 11a3 3 0 100-6 3 3 0 000 6zM3 20c0-3 2.7-5 6-5s6 2 6 5M17 11a3 3 0 000-6M18 15c2 .5 3 2.2 3 5',
  wallet: 'M3 7h16a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V7zM3 7l2-3h12M16 14h2',
  bell: 'M6 17v-6a6 6 0 1112 0v6l2 2H4l2-2zM10 21h4',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  check: 'M5 13l4 4L19 7',
  trophy: 'M8 4h8v6a4 4 0 01-8 0V4zM8 6H4v1a3 3 0 003 3M16 6h4v1a3 3 0 01-3 3M12 14v4M8 20h8',
  // figura con brazos abiertos dentro de un círculo
  accessibility: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7.5v.01M7.5 10.5l4.5 1 4.5-1M12 11.5v3M12 14.5l-2 3M12 14.5l2 3',
  chart: 'M4 20h16M7 20v-7M12 20V5M17 20v-10',
  alert: 'M12 9v4M12 17v.01M10.3 4l-8 14a2 2 0 001.7 3h16a2 2 0 001.7-3l-8-14a2 2 0 00-3.4 0z',
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2',
  info: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 11v5M12 8v.01',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3',
  plus: 'M12 5v14M5 12h14',
}

export default function Icon({ name, className = 'h-6 w-6' }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      <path d={paths[name]} />
    </svg>
  )
}
