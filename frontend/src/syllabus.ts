import type { LessonEntry } from './api/types'

export type SyllabusRow = { lesson: LessonEntry; code: string }
export type Phase = { label: string; rows: SyllabusRow[] }

// The API has no phase field. The 04 artboard groups lessons by the phase number of their code
// («02.01» → «Fase 02»); the manifest gives every lesson the ID `<courseId>-<phase>.<lesson>`,
// so the same code is read from the ID. Phase names («Fundamentos») are not in the API and are
// not shown. If any ID does not follow the pattern, or a phase is not contiguous in position
// order, there is no clean derivation: the caller shows one flat list in position order.
export function groupByPhase(courseId: string, lessons: LessonEntry[]): Phase[] | null {
  const pattern = new RegExp(`^${courseId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}-(\\d+)\\.(\\d+)$`)
  const phases: Phase[] = []
  const seen = new Set<string>()
  for (const lesson of [...lessons].sort((a, b) => a.position - b.position)) {
    const match = pattern.exec(lesson.id)
    if (!match) return null
    const [, phase, number] = match
    const last = phases.at(-1)
    if (last?.label !== `Fase ${phase}`) {
      if (seen.has(phase)) return null
      seen.add(phase)
      phases.push({ label: `Fase ${phase}`, rows: [] })
    }
    phases[phases.length - 1].rows.push({ lesson, code: `${phase}.${number}` })
  }
  return phases
}

export function flatSyllabus(lessons: LessonEntry[]): SyllabusRow[] {
  return [...lessons].sort((a, b) => a.position - b.position).map((lesson) => ({ lesson, code: String(lesson.position) }))
}
