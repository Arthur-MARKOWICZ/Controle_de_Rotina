import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { ProtectedRoute } from './auth/components/ProtectedRoute'
import { CadastroPage } from './auth/pages/CadastroPage'
import { LoginPage } from './auth/pages/LoginPage'
import { PerfilPage } from './auth/pages/PerfilPage'
import { AuthProvider } from './auth/session/AuthContext'
import { CalendarioPage } from './calendario/pages/CalendarioPage'
import { CapacidadePage } from './capacidade/pages/CapacidadePage'
import { HabitoDrawer } from './habito/HabitoDrawer'
import { HabitosPage } from './habito/pages/HabitosPage'
import { HistoricoPage } from './historico/pages/HistoricoPage'
import { AppShell } from './layout/AppShell'
import { DocumentTitle } from './layout/DocumentTitle'
import { HomePage } from './pages/HomePage'
import { ProjetoFormPage } from './projeto/pages/ProjetoFormPage'
import { ProjetoDrawer } from './projeto/ProjetoDrawer'
import { ProjetosPage } from './projeto/pages/ProjetosPage'
import { TarefasPage } from './tarefa/pages/TarefasPage'
import { TarefaDrawer } from './tarefa/TarefaDrawer'
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
              <Route path="/tarefas" element={<TarefasPage />}>
                <Route path="nova" element={<TarefaDrawer />} />
                <Route path=":id" element={<TarefaDrawer />} />
              </Route>
              <Route path="/projetos" element={<ProjetosPage />}>
                <Route path="nova" element={<ProjetoDrawer />} />
              </Route>
              <Route path="/projetos/:id" element={<ProjetoFormPage />} />
              <Route path="/habitos" element={<HabitosPage />}>
                <Route path="nova" element={<HabitoDrawer />} />
                <Route path=":id" element={<HabitoDrawer />} />
              </Route>
              <Route path="/calendario" element={<CalendarioPage />} />
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
