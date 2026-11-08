import type { FakeBackend } from './fakeBackend'
import { error, notAddedProgress } from './fakeBackend'

// Synthetic inventory, not the pending bulk import.
export const stages = [
  { id: 'java-stage-1', courseId: 'java', title: 'Primera etapa', position: 1, available: true },
  { id: 'java-stage-2', courseId: 'java', title: 'Segunda etapa', position: 2, available: true },
  { id: 'java-stage-3', courseId: 'java', title: 'Etapa pendiente', position: 3, available: false },
]

export function stageFixture(backend: FakeBackend, added = false) {
  let progress = {
    courseId: 'java', added,
    stageStates: [] as { stageId: string; state: string; updatedAt: string }[],
    continuation: { lastOpenedStageId: null as string | null },
    progress: { completedStages: 0, availableStages: 2, plannedStages: 3, percentage: 0 },
  }
  const lesson = { ...notAddedProgress(), added, continuation: { lastOpenedLessonId: added ? 'java-02.01' : null } }
  backend.route('GET /api/me/courses/java/progress', () => ({ status: 200, body: lesson }))
  backend.route('GET /api/courses/java/stages', { status: 200, body: { courseId: 'java', stages } })
  backend.route('GET /api/me/courses/java/stage-progress', () => ({ status: 200, body: progress }))
  backend.route('PUT /api/me/courses/java/stage-continuation', (call) => {
    if (!progress.added) return error(409, 'COURSE_NOT_ADDED')
    const { stageId } = JSON.parse(call.body!) as { stageId: string }
    progress = { ...progress, continuation: { lastOpenedStageId: stageId } }
    return { status: 200, body: progress }
  })
  for (const stage of stages) {
    backend.route(`GET /api/stages/${stage.id}`, stage.available ? {
      status: 200,
      body: {
        stage, bodyHtml: `<h2 id="trabajo">Trabajo de ${stage.title}</h2><details><summary>Orientación</summary><p>Texto de apoyo</p></details>`,
        resources: [{ resourceId: 'stage-zip', url: '/api/resources/stage-zip', mediaType: 'application/zip', fileName: 'proyecto.zip' }],
        previousStageId: stage.position === 2 ? stages[0].id : null,
        nextStageId: stage.position === 1 ? stages[1].id : null,
      },
    } : error(409, 'STAGE_UNAVAILABLE'))
    backend.route(`PUT /api/me/stages/${stage.id}/state`, (call) => {
      const { state } = JSON.parse(call.body!)
      const courseAutoAdded = !progress.added
      lesson.added = true
      const updatedAt = '2026-10-01T10:00:00Z'
      const states = [...progress.stageStates.filter((entry) => entry.stageId !== stage.id), { stageId: stage.id, state, updatedAt }]
      const completedStages = states.filter((entry) => entry.state === 'completed').length
      progress = {
        ...progress, added: true, stageStates: states,
        continuation: courseAutoAdded ? { lastOpenedStageId: stage.id } : progress.continuation,
        progress: { ...progress.progress, completedStages, percentage: completedStages * 50 },
      }
      return { status: 200, body: { courseId: 'java', stageId: stage.id, state, updatedAt, courseAutoAdded, continuation: progress.continuation, progress: progress.progress } }
    })
  }
}
