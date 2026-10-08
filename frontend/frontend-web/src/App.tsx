import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { ProtectedRoute } from './auth/components/ProtectedRoute'
import { CadastroPage } from './auth/pages/CadastroPage'
import { LoginPage } from './auth/pages/LoginPage'
import { PerfilPage } from './auth/pages/PerfilPage'
import { AuthProvider } from './auth/session/AuthContext'
import { CapacidadePage } from './capacidade/pages/CapacidadePage'
import { HomePage } from './pages/HomePage'
import { TarefaFormPage } from './tarefa/pages/TarefaFormPage'
import { TarefasPage } from './tarefa/pages/TarefasPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/cadastro" element={<CadastroPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <HomePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/perfil"
            element={
              <ProtectedRoute>
                <PerfilPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/tarefas"
            element={
              <ProtectedRoute>
                <TarefasPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/tarefas/nova"
            element={
              <ProtectedRoute>
                <TarefaFormPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/tarefas/:id"
            element={
              <ProtectedRoute>
                <TarefaFormPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/capacidade"
            element={
              <ProtectedRoute>
                <CapacidadePage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
