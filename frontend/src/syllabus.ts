import type { LessonEntry, Phase } from './api/types'

export type SyllabusRow = { lesson: LessonEntry; code: string }
export type PhaseGroup = { key: string; label: string; rows: SyllabusRow[] }

const twoDigits = (value: number) => String(value).padStart(2, '0')

// Lessons are grouped by their phaseId, in the order of the API phases' position; lesson IDs are
// opaque and never read. The caption follows the 04 artboard: «Fase 02 · Fundamentos de Java»,
// numbered from 00 by position. The code column numbers each lesson inside its phase («02.01»).
// A lesson whose phase is not in the list is kept in a last «Sin fase» group, never dropped.
export function groupByPhase(phases: Phase[], lessons: LessonEntry[]): PhaseGroup[] {
  const ordered = [...phases].sort((a, b) => a.position - b.position)
  const byId = new Map(ordered.map((phase) => [phase.id, phase]))
  const groups = ordered.map((phase) => ({
    key: phase.id,
    label: `Fase ${twoDigits(phase.position - 1)} · ${phase.title}`,
    rows: [] as SyllabusRow[],
  }))
  const orphans: SyllabusRow[] = []
  for (const lesson of [...lessons].sort((a, b) => a.position - b.position)) {
    const phase = byId.get(lesson.phaseId)
    if (!phase) {
      orphans.push({ lesson, code: String(lesson.position) })
      continue
    }
    const group = groups[ordered.indexOf(phase)]
    group.rows.push({ lesson, code: `${twoDigits(phase.position - 1)}.${twoDigits(group.rows.length + 1)}` })
  }
  if (orphans.length > 0) groups.push({ key: '', label: 'Sin fase', rows: orphans })
  return groups.filter((group) => group.rows.length > 0)
}
