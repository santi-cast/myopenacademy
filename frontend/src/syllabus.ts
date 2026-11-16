import type { LessonEntry, Phase } from './api/types'

export type SyllabusRow = { lesson: LessonEntry; code: string }
export type PhaseGroup = { key: string; label: string; rows: SyllabusRow[] }

const twoDigits = (value: number) => String(value).padStart(2, '0')

// Lessons are grouped by their phaseId, in the order of the API phases' position; lesson IDs are
// opaque and never read. The caption follows the 04 artboard: «Fase 02 · Fundamentos de Java».
// Numbers come only from the order of the course (curso.json): the first phase is «Fase 01» and
// the code column numbers each lesson inside its phase («01.01»), pending lessons included.
// A lesson whose phase is not in the list is kept in a last «Sin fase» group, never dropped.
export function groupByPhase(phases: Phase[], lessons: LessonEntry[]): PhaseGroup[] {
  const ordered = [...phases].sort((a, b) => a.position - b.position)
  const byId = new Map(ordered.map((phase) => [phase.id, phase]))
  const groups = ordered.map((phase, index) => ({
    key: phase.id,
    number: twoDigits(index + 1),
    label: `Fase ${twoDigits(index + 1)} · ${phase.title}`,
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
    group.rows.push({ lesson, code: `${group.number}.${twoDigits(group.rows.length + 1)}` })
  }
  const result: PhaseGroup[] = groups
    .filter((group) => group.rows.length > 0)
    .map(({ key, label, rows }) => ({ key, label, rows }))
  if (orphans.length > 0) result.push({ key: '', label: 'Sin fase', rows: orphans })
  return result
}

// «Retos de la fase 01»: the challenges of a phase, numbered like the phase in the order of the course (H8).
export function challengesLabel(phases: Phase[], phaseId: string | null): string {
  const index = [...phases].sort((a, b) => a.position - b.position).findIndex((phase) => phase.id === phaseId)
  return index < 0 ? 'Retos de la fase' : `Retos de la fase ${twoDigits(index + 1)}`
}
