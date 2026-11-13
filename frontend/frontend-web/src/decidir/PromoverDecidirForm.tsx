import { useRef, useState, type FormEvent } from 'react'

import { ApiError } from '../auth/api/authApi'
import { Drawer } from '../feedback/Drawer'
import { InlineAlert } from '../feedback/InlineAlert'
import type { Prioridade } from '../tarefa/validation/tarefaForm'
import {
  promoverDecidir,
  type DecidirResponse,
  type PromoverDecidirRequest,
} from './api/decidirApi'

type PromoverDecidirFormProps = {
  item: DecidirResponse
  onPromovido: (item: DecidirResponse) => void
  onCancelar: () => void
}

/** Drawer de promoção de um item Decidir a tarefa (campos opcionais). */
export function PromoverDecidirForm({ item, onPromovido, onCancelar }: PromoverDecidirFormProps) {
  const [duracao, setDuracao] = useState('')
  const [prioridade, setPrioridade] = useState<Prioridade | ''>('')
  const [prazo, setPrazo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const enviandoRef = useRef(false)

  async function confirmar(event: FormEvent) {
    event.preventDefault()
    if (enviandoRef.current) {
      return
    }
    enviandoRef.current = true
    setEnviando(true)
    setErro(null)
    try {
      const body: PromoverDecidirRequest = {}
      if (duracao.trim()) {
        body.duracaoMinutos = Number(duracao)
      }
      if (prioridade) {
        body.prioridade = prioridade
      }
      if (prazo) {
        body.prazo = new Date(prazo).toISOString()
      }
      const promovido = await promoverDecidir(item.id, body)
      onPromovido(promovido)
    } catch (error) {
      setErro(error instanceof ApiError ? error.message : 'Não foi possível promover o item.')
    } finally {
      enviandoRef.current = false
      setEnviando(false)
    }
  }

  return (
    <Drawer
      open
      onClose={() => {
        if (!enviando) {
          onCancelar()
        }
      }}
      title={`Promover “${item.titulo}”`}
    >
      <form className="decidir-promover" onSubmit={(e) => void confirmar(e)}>
        <p>Cria uma tarefa com o título do item. Campos abaixo são opcionais.</p>
        {erro ? <InlineAlert>{erro}</InlineAlert> : null}
        <div className="field">
          <label htmlFor="promover-duracao">Duração (minutos)</label>
          <input
            id="promover-duracao"
            type="number"
            min={15}
            step={15}
            value={duracao}
            onChange={(e) => setDuracao(e.target.value)}
            disabled={enviando}
          />
        </div>
        <div className="field">
          <label htmlFor="promover-prioridade">Prioridade</label>
          <select
            id="promover-prioridade"
            value={prioridade}
            onChange={(e) => setPrioridade(e.target.value as Prioridade | '')}
            disabled={enviando}
          >
            <option value="">Padrão (média)</option>
            <option value="ALTA">Alta</option>
            <option value="MEDIA">Média</option>
            <option value="BAIXA">Baixa</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="promover-prazo">Prazo</label>
          <input
            id="promover-prazo"
            type="datetime-local"
            value={prazo}
            onChange={(e) => setPrazo(e.target.value)}
            disabled={enviando}
          />
        </div>
        <div className="decidir-promover__acoes">
          <button
            type="button"
            className="btn btn--secondary"
            disabled={enviando}
            onClick={onCancelar}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className="btn btn--primary"
            disabled={enviando}
            aria-busy={enviando}
          >
            Promover
          </button>
        </div>
      </form>
    </Drawer>
  )
}
