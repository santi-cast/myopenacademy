// Response shapes from docs/api.md («Formas reutilizables de salida»).

export type User = { id: string; username: string }

export type Counts = {
  completedLessons: number
  availableLessons: number
  plannedLessons: number
  percentage: number | null
}

export type Course = { id: string; title: string }

export type LessonEntry = {
  id: string
  courseId: string
  title: string
  position: number
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

export type Syllabus = { course: Course; lessons: LessonEntry[] }

export type LessonView = {
  lesson: LessonEntry
  bodyHtml: string
  exercises: unknown[]
  resources: unknown[]
  previousLessonId: string | null
  nextLessonId: string | null
}
