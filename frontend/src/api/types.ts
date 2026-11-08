// Response shapes from docs/api.md («Formas reutilizables de salida»).

export type User = { id: string; username: string }

export type Counts = {
  completedLessons: number
  availableLessons: number
  plannedLessons: number
  percentage: number | null
}

export type Course = { id: string; title: string }

export type Phase = { id: string; title: string; position: number }

export type LessonEntry = {
  id: string
  courseId: string
  title: string
  position: number
  phaseId: string
  available: boolean
}

export type Continuation = { lastOpenedLessonId: string | null }

export type Enrollment = {
  courseId: string
  added: boolean
  continuation: Continuation
  progress: Counts
}

export type LessonState = { lessonId: string; state: 'not_completed' | 'completed'; updatedAt: string | null }

export type ExerciseState = {
  exerciseId: string
  state: 'pending' | 'in_progress' | 'solved'
  updatedAt: string | null
}

export type Progress = {
  courseId: string
  added: boolean
  lessonStates: LessonState[]
  exerciseStates: ExerciseState[]
  continuation: Continuation
  progress: Counts
}

export type MyCourses = { courses: { course: Course; enrollment: Enrollment }[] }

export type Courses = { courses: Course[] }

export type Syllabus = { course: Course; phases: Phase[]; lessons: LessonEntry[] }

export type Exercise = {
  id: string
  lessonId: string
  courseId: string
  title: string
  statementHtml: string
  solutionHtml: string
}

export type Resource = { resourceId: string; url: string; mediaType: string; fileName: string }

export type LessonView = {
  lesson: LessonEntry
  bodyHtml: string
  exercises: Exercise[]
  resources: Resource[]
  previousLessonId: string | null
  nextLessonId: string | null
}

type StateResultBase = {
  courseId: string
  updatedAt: string
  courseAutoAdded: boolean
  continuation: Continuation
  progress: Counts
}

export type LessonStateResult = StateResultBase & { lessonId: string; state: LessonState['state'] }

export type ExerciseStateResult = StateResultBase & { exerciseId: string; state: ExerciseState['state'] }

export type StageEntry = Omit<LessonEntry, 'phaseId'>
export type StageState = { stageId: string; state: 'not_completed' | 'completed'; updatedAt: string | null }
export type StageCounts = {
  completedStages: number
  availableStages: number
  plannedStages: number
  percentage: number | null
}
export type StageProgress = {
  courseId: string
  added: boolean
  stageStates: StageState[]
  continuation: { lastOpenedStageId: string | null }
  progress: StageCounts
}
export type StageIndex = { courseId: string; stages: StageEntry[] }
export type StageView = {
  stage: StageEntry
  bodyHtml: string
  resources: Resource[]
  previousStageId: string | null
  nextStageId: string | null
}
export type StageStateResult = Omit<StageProgress, 'added' | 'stageStates'> & {
  stageId: string
  state: StageState['state']
  updatedAt: string
  courseAutoAdded: boolean
}
