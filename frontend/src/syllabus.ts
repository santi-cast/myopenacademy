import type { LessonEntry, Part, Phase } from './api/types'

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

/** Lessons completed of the lessons available now, like the course progress: pending lessons never count (S8). */
export type LessonCount = { completed: number; available: number }

export function countLessons(lessons: LessonEntry[], completed: Set<string>): LessonCount {
  const available = lessons.filter((lesson) => lesson.available)
  return { completed: available.filter((lesson) => completed.has(lesson.id)).length, available: available.length }
}

export const countRows = (rows: SyllabusRow[], completed: Set<string>) => countLessons(rows.map(({ lesson }) => lesson), completed)

export type PartGroup = { key: string; label: string; position: number; phases: PhaseGroup[] }

const byPosition = (parts: Part[]) => [...parts].sort((a, b) => a.position - b.position)

// The parts of a course (H12), in their position, each with its phase groups in the order of its phaseIds.
// A part with no lessons is left out; phase groups in no part (an unknown phase) are returned apart, never dropped.
export function groupByPart(parts: Part[] | undefined, groups: PhaseGroup[]): { parts: PartGroup[]; rest: PhaseGroup[] } {
  const placed = new Set<string>()
  const result = byPosition(parts ?? []).map((part) => {
    const phases = part.phaseIds
      .map((phaseId) => groups.find((group) => group.key === phaseId && !placed.has(group.key)))
      .filter((group): group is PhaseGroup => group !== undefined)
    for (const group of phases) placed.add(group.key)
    return { key: part.id, label: `Parte ${part.position} · ${part.title}`, position: part.position, phases }
  })
  return { parts: result.filter((part) => part.phases.length > 0), rest: groups.filter((group) => !placed.has(group.key)) }
}

/** The part a lesson belongs to, through its phase; null for a course without parts. */
export function partOfLesson(parts: Part[] | undefined, lesson: LessonEntry | undefined): Part | null {
  if (!lesson) return null
  return byPosition(parts ?? []).find((part) => part.phaseIds.includes(lesson.phaseId)) ?? null
}

// «Retos de la fase 01»: the challenges of a phase, numbered like the phase in the order of the course (H8).
export function challengesLabel(phases: Phase[], phaseId: string | null): string {
  const index = [...phases].sort((a, b) => a.position - b.position).findIndex((phase) => phase.id === phaseId)
  return index < 0 ? 'Retos de la fase' : `Retos de la fase ${twoDigits(index + 1)}`
}
