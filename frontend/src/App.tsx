import { Route, Routes } from 'react-router'
import { PrivateLayout, PublicLayout } from './layout/Layouts'
import { CatalogPage } from './pages/CatalogPage'
import { CourseIndexPage } from './pages/CourseIndexPage'
import { LessonPage } from './pages/LessonPage'
import { LoginPage } from './pages/LoginPage'
import { MyCoursesPage } from './pages/MyCoursesPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { ProjectPage } from './pages/ProjectPage'
import { DocumentPage } from './pages/DocumentPage'
import { StagePage } from './pages/StagePage'
import { SessionProvider } from './session/SessionProvider'
import './App.css'

/** Rendered inside a router: BrowserRouter in main.tsx, MemoryRouter in tests. */
function App() {
  return (
    <SessionProvider>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>
        <Route element={<PrivateLayout />}>
          <Route index element={<MyCoursesPage />} />
          <Route path="/courses" element={<CatalogPage />} />
          <Route path="/courses/:courseId" element={<CourseIndexPage />} />
          <Route path="/lessons/:lessonId" element={<LessonPage />} />
          <Route path="/courses/:courseId/project" element={<ProjectPage />} />
          <Route path="/stages/:stageId" element={<StagePage />} />
          <Route path="/documents/:documentId" element={<DocumentPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </SessionProvider>
  )
}

export default App
