import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { ProtectedRoute } from './auth/components/ProtectedRoute'
import { CadastroPage } from './auth/pages/CadastroPage'
import { LoginPage } from './auth/pages/LoginPage'
import { PerfilPage } from './auth/pages/PerfilPage'
import { AuthProvider } from './auth/session/AuthContext'
import { CapacidadePage } from './capacidade/pages/CapacidadePage'
import { HistoricoPage } from './historico/pages/HistoricoPage'
import { AppShell } from './layout/AppShell'
import { DocumentTitle } from './layout/DocumentTitle'
import { HomePage } from './pages/HomePage'
import { TarefaFormPage } from './tarefa/pages/TarefaFormPage'
import { TarefasPage } from './tarefa/pages/TarefasPage'
import { ThemeProvider } from './theme/ThemeProvider'

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <DocumentTitle />
          <Routes>
            <Route path="/cadastro" element={<CadastroPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<HomePage />} />
              <Route path="/perfil" element={<PerfilPage />} />
              <Route path="/tarefas" element={<TarefasPage />} />
              <Route path="/tarefas/nova" element={<TarefaFormPage />} />
              <Route path="/tarefas/:id" element={<TarefaFormPage />} />
              <Route path="/capacidade" element={<CapacidadePage />} />
              <Route path="/historico" element={<HistoricoPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
