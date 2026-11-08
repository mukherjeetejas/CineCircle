import { Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout, AuthLayout } from './layout/AppLayout'
import { AboutPage } from './pages/AboutPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { MoviePage } from './pages/MoviePage'
import { MovieSearchPage } from './pages/MovieSearchPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PopularPage } from './pages/PopularPage'
import { PostPage } from './pages/PostPage'
import { ProfilePage } from './pages/ProfilePage'
import { RegisterPage } from './pages/RegisterPage'
import { RequestsPage } from './pages/RequestsPage'
import { SettingsPage } from './pages/SettingsPage'
import { SubmitPage } from './pages/SubmitPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route
          index
          element={
            <RequireAuth redirectTo="/popular">
              <HomePage />
            </RequireAuth>
          }
        />
        <Route path="popular" element={<PopularPage />} />
        <Route path="movies/search" element={<MovieSearchPage />} />
        <Route path="movies/:tmdbId" element={<MoviePage />} />
        <Route path="u/:username" element={<ProfilePage />} />
        <Route path="posts/:id" element={<PostPage />} />
        <Route
          path="submit"
          element={
            <RequireAuth>
              <SubmitPage />
            </RequireAuth>
          }
        />
        <Route
          path="settings"
          element={
            <RequireAuth>
              <SettingsPage />
            </RequireAuth>
          }
        />
        <Route
          path="requests"
          element={
            <RequireAuth>
              <RequestsPage />
            </RequireAuth>
          }
        />
        <Route path="about" element={<AboutPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route element={<AuthLayout />}>
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
      </Route>
    </Routes>
  )
}
