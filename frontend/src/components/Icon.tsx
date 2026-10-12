// A small set of line icons drawn on a 16px grid. They take the text colour
// and the text size of wherever they are placed, like a character would.

const PATHS = {
  arrowLeft: <path d="M13 8H3m4-4L3 8l4 4" />,
  arrowRight: <path d="M3 8h10M9 4l4 4-4 4" />,
  arrowUpRight: <path d="M5 11l6-6M6 5h5v5" />,
  check: <path d="M3.5 8.5l3 3 6-7" />,
  cross: <path d="M4.5 4.5l7 7m0-7l-7 7" />,
  chevronDown: <path d="M4 6l4 4 4-4" />,
  replay: <path d="M3.2 8a4.8 4.8 0 1 0 1.5-3.5M3 2.5V5h2.5" />,
  sun: (
    <>
      <circle cx="8" cy="8" r="2.75" />
      <path d="M8 1.5V3m0 10v1.5M1.5 8H3m10 0h1.5M3.4 3.4l1.1 1.1m7 7l1.1 1.1m-9.2 0l1.1-1.1m7-7l1.1-1.1" />
    </>
  ),
  moon: <path d="M13.5 9.6A5.6 5.6 0 0 1 6.4 2.5a5.6 5.6 0 1 0 7.1 7.1z" />,
}

export type IconName = keyof typeof PATHS

export function Icon({ name, className }: { name: IconName; className?: string }) {
  return (
    <svg
      viewBox="0 0 16 16"
      width="1em"
      height="1em"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      {PATHS[name]}
    </svg>
  )
}
