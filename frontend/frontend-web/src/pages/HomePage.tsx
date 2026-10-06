import { Link } from 'react-router-dom'

import { useAuth } from '../auth/session/AuthContext'

export function HomePage() {
  const { logout } = useAuth()

  return (
    <main className="home-page">
      <header className="home-header">
        <h1>Controle de Rotina</h1>
        <div className="home-actions">
          <Link to="/perfil">Meu perfil</Link>
          <button type="button" onClick={() => void logout()}>
            Sair
          </button>
        </div>
      </header>
      <p>Você está autenticado. O planejamento virá nas próximas features.</p>
    </main>
  )
}
