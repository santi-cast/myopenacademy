import type { Counts } from './api/types'

// Display rounding only: the contract percentage has no contractual rounding.
const percent = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 })

/** «X de Y lecciones disponibles completadas · P % · Z previstas» (PLAN §3, «Reglas de progreso»). */
export function progressText(counts: Counts): string {
  if (counts.availableLessons === 0) return `sin lecciones disponibles · ${counts.plannedLessons} previstas`
  const share = counts.percentage === null ? '' : ` · ${percent.format(counts.percentage)}\u00a0%`
  return `${counts.completedLessons} de ${counts.availableLessons} lecciones disponibles completadas${share} · ${counts.plannedLessons} previstas`
}

/** Bar width as a CSS percentage; 0 when nothing is available. */
export function progressWidth(counts: Counts): string {
  const value = counts.availableLessons === 0 || counts.percentage === null ? 0 : Math.min(100, Math.max(0, counts.percentage))
  return `${value}%`
}

type LessonCount = { completed: number; available: number }

/** «N de M»: completed of available lessons of a part or a phase (H12, S6). */
export const countText = (count: LessonCount) => `${count.completed} de ${count.available}`

/** «12 de 40 lecciones»: the progress of a part in the course index (H12, S5). */
export const partProgressText = (count: LessonCount) => `${countText(count)} lecciones`

/** «1 de 2 completadas»: the progress of a phase in the course index, after its availability (H12, S1). */
export const phaseProgressText = (count: LessonCount) => `${countText(count)} completadas`
