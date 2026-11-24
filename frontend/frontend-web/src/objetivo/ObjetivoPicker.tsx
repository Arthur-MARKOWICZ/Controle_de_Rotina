import { FlagIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useId, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { Popover } from '../feedback/Popover'
import { listarObjetivos, type ObjetivoResumo, type StatusObjetivo } from './api/objetivoApi'

type Props = {
  selectedIds: string[]
  /** Objetivos do item carregado: inclui os já concluídos/arquivados, que não são opção. */
  selectedObjetivos?: ObjetivoResumo[]
  onChange: (objetivoIds: string[], objetivos: ObjetivoResumo[]) => void
  disabled?: boolean
}

/** Mesmo teto de página da API; o uso é pessoal, então o seletor não pagina. */
const MAX_OPCOES = 100

const STATUS_LABEL: Record<StatusObjetivo, string> = {
  ATIVO: 'Ativo',
  CONCLUIDO: 'Concluído',
  ARQUIVADO: 'Arquivado',
}

function mesclar(...listas: (ObjetivoResumo[] | undefined)[]): Map<string, ObjetivoResumo> {
  const map = new Map<string, ObjetivoResumo>()
  for (const lista of listas) {
    for (const objetivo of lista ?? []) {
      map.set(objetivo.id, objetivo)
    }
  }
  return map
}

/** Escolha de vários objetivos ativos; os já vinculados e encerrados continuam como chip. */
export function ObjetivoPicker({
  selectedIds,
  selectedObjetivos,
  onChange,
  disabled = false,
}: Props) {
  const rotuloId = useId()
  const [opcoes, setOpcoes] = useState<ObjetivoResumo[]>([])
  const [busca, setBusca] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const pagina = await listarObjetivos({ status: 'ATIVO', size: MAX_OPCOES })
      setOpcoes(
        pagina.content.map(({ id, titulo, status }) => ({ id, titulo, status })),
      )
    } catch {
      setErro('Não foi possível carregar os objetivos.')
      setOpcoes([])
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  const porId = useMemo(() => mesclar(opcoes, selectedObjetivos), [opcoes, selectedObjetivos])

  const selecionados = useMemo(
    () =>
      selectedIds
        .map((id) => porId.get(id))
        .filter((objetivo): objetivo is ObjetivoResumo => objetivo != null),
    [porId, selectedIds],
  )

  const buscaNorm = busca.trim().toLowerCase()
  const filtradas = useMemo(
    () =>
      buscaNorm
        ? opcoes.filter((objetivo) => objetivo.titulo.toLowerCase().includes(buscaNorm))
        : opcoes,
    [buscaNorm, opcoes],
  )

  function alternar(id: string) {
    if (disabled) {
      return
    }
    const proximos = selectedIds.includes(id)
      ? selectedIds.filter((item) => item !== id)
      : [...selectedIds, id]
    onChange(
      proximos,
      proximos
        .map((item) => porId.get(item))
        .filter((objetivo): objetivo is ObjetivoResumo => objetivo != null),
    )
  }

  const semOpcoes = !carregando && erro == null && opcoes.length === 0
  const semResultado = !carregando && erro == null && opcoes.length > 0 && filtradas.length === 0

  return (
    <div className="field objetivo-picker">
      <span className="field-label" id={rotuloId}>
        Objetivos
      </span>
      {selecionados.length ? (
        <div className="objetivo-picker__selecionados" aria-live="polite">
          {selecionados.map((objetivo) => (
            <button
              key={objetivo.id}
              type="button"
              className={
                objetivo.status === 'ATIVO'
                  ? 'objetivo-picker__chip'
                  : 'objetivo-picker__chip objetivo-picker__chip--encerrado'
              }
              disabled={disabled}
              aria-label={`Remover objetivo ${objetivo.titulo}`}
              onClick={() => alternar(objetivo.id)}
            >
              <FlagIcon aria-hidden />
              {objetivo.titulo}
              {objetivo.status === 'ATIVO' ? null : (
                <span className="objetivo-picker__status"> · {STATUS_LABEL[objetivo.status]}</span>
              )}
            </button>
          ))}
        </div>
      ) : null}
      <Popover
        label={
          <>
            <FlagIcon aria-hidden />
            Adicionar objetivos
          </>
        }
        triggerClassName="btn btn--secondary btn--sm"
        ariaLabel="Escolher objetivos"
      >
        <div className="objetivo-picker__painel" role="group" aria-labelledby={rotuloId}>
          <input
            type="search"
            className="objetivo-picker__busca"
            placeholder="Buscar objetivo…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            disabled={disabled || carregando}
            aria-label="Buscar objetivo"
          />
          {erro ? <p role="alert">{erro}</p> : null}
          {carregando ? <p role="status">Carregando objetivos…</p> : null}
          {semOpcoes ? (
            <p role="status">
              Nenhum objetivo ativo.{' '}
              <Link to="/objetivos">Ver objetivos</Link>
            </p>
          ) : null}
          {semResultado ? <p role="status">Nenhum objetivo com esse termo.</p> : null}
          {!carregando && filtradas.length > 0 ? (
            <ul className="objetivo-picker__opcoes">
              {filtradas.map((objetivo) => {
                const ativa = selectedIds.includes(objetivo.id)
                return (
                  <li key={objetivo.id}>
                    <button
                      type="button"
                      className={
                        ativa
                          ? 'objetivo-picker__opcao objetivo-picker__opcao--ativa'
                          : 'objetivo-picker__opcao'
                      }
                      aria-pressed={ativa}
                      disabled={disabled}
                      onClick={() => alternar(objetivo.id)}
                    >
                      {objetivo.titulo}
                    </button>
                  </li>
                )
              })}
            </ul>
          ) : null}
        </div>
      </Popover>
    </div>
  )
}
