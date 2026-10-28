// Manual states (PLAN §3, «Reglas de progreso»): shown as saved only after the server confirms.
export type SaveStatus =
  | { kind: 'idle' }
  | { kind: 'saving' }
  | { kind: 'saved'; updatedAt: string }
  | { kind: 'failed'; message: string }

export type SaveState<S extends string> = { pending: S | null; status: SaveStatus }

export const STATE_HELP = 'Marcá vos el estado: la academia no corrige los ejercicios.'

const twoDigits = (value: number) => String(value).padStart(2, '0')

/** «Guardado · hoy, 14:05» from the server's updatedAt, in local time. */
export function savedText(updatedAt: string): string {
  const date = new Date(updatedAt)
  if (Number.isNaN(date.getTime())) return 'Guardado'
  const time = `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`
  const today = date.toDateString() === new Date().toDateString()
  const day = today ? 'hoy' : `${twoDigits(date.getDate())}/${twoDigits(date.getMonth() + 1)}/${date.getFullYear()}`
  return `Guardado · ${day}, ${time}`
}
