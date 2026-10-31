import { CheckIcon, FunnelSimpleIcon, SortAscendingIcon, XIcon } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'

import { Popover } from '../feedback/Popover'
import { PRIORIDADE_LABEL } from '../home/labels'
import type { ProjetoResponse } from '../projeto/api/projetoApi'
import type { SortCampo, SortDirection } from './api/tarefaApi'
import { chipsAtivos, filtrosLimpos } from './filtrosAtivos'
import { FILTROS_LISTA_INICIAIS, type FiltrosListaUrl, type VisaoLista } from './listaUrl'
import type { Prioridade } from './validation/tarefaForm'

export const DEBOUNCE_FILTRO_MS = 400

const PRIORIDADE_OPCOES: Prioridade[] = ['ALTA', 'MEDIA', 'BAIXA']

const VISOES: { id: VisaoLista; label: string }[] = [
  { id: 'todas', label: 'Todas' },
  { id: 'pendentes', label: 'Pendentes' },
  { id: 'em_progresso', label: 'Em progresso' },
  { id: 'finalizadas', label: 'Finalizadas' },
  { id: 'excluidas', label: 'Excluídas' },
]

type CamposDebounce = Pick<FiltrosListaUrl, 'prazoDe' | 'prazoAte' | 'duracaoDe' | 'duracaoAte'>

function camposDebounce(filtros: FiltrosListaUrl): CamposDebounce {
  return {
    prazoDe: filtros.prazoDe,
    prazoAte: filtros.prazoAte,
    duracaoDe: filtros.duracaoDe,
    duracaoAte: filtros.duracaoAte,
  }
}

function toggleValor<T extends string>(lista: T[], valor: T): T[] {
  return lista.includes(valor) ? lista.filter((item) => item !== valor) : [...lista, valor]
}

function classeGatilho(ativo: boolean): string {
  const base = 'btn btn--secondary btn--sm filtro-gatilho'
  return ativo ? `${base} filtro-gatilho--ativo` : base
}

function RotuloFiltro({ texto }: { texto: string }) {
  return (
    <>
      <FunnelSimpleIcon aria-hidden />
      {texto}
    </>
  )
}

type Props = {
  filtros: FiltrosListaUrl
  projetosAtivos: ProjetoResponse[]
  onAplicar: (proximo: FiltrosListaUrl) => void
}

export function FiltrosBarra({ filtros, projetosAtivos, onAplicar }: Props) {
  const [rascunho, setRascunho] = useState<CamposDebounce>(() => camposDebounce(filtros))
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    if (timerRef.current == null) {
      setRascunho(camposDebounce(filtros))
    }
  }, [filtros])

  useEffect(
    () => () => {
      if (timerRef.current != null) {
        window.clearTimeout(timerRef.current)
      }
    },
    [],
  )

  function cancelarDebounce() {
    if (timerRef.current != null) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  /** Aplica na hora, incorporando digitação ainda pendente no debounce. */
  function aplicar(parcial: Partial<FiltrosListaUrl>) {
    const pendente = timerRef.current != null ? rascunho : {}
    cancelarDebounce()
    onAplicar({ ...filtros, ...pendente, ...parcial, page: 0 })
  }

  function alterarComDebounce(campo: keyof CamposDebounce, valor: string) {
    const proximo = { ...rascunho, [campo]: valor }
    setRascunho(proximo)
    cancelarDebounce()
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null
      onAplicar({ ...filtros, ...proximo, page: 0 })
    }, DEBOUNCE_FILTRO_MS)
  }

  function limpar() {
    cancelarDebounce()
    onAplicar(filtrosLimpos(filtros))
  }

  const chips = chipsAtivos(filtros, projetosAtivos)
  const ordenacaoAlterada =
    filtros.sort !== FILTROS_LISTA_INICIAIS.sort ||
    filtros.direction !== FILTROS_LISTA_INICIAIS.direction

  return (
    <div className="filtros-barra">
      <div className="segmented" role="group" aria-label="Visão da lista">
        {VISOES.map((visao) => {
          const ativa = filtros.visao === visao.id
          return (
            <button
              key={visao.id}
              type="button"
              className={ativa ? 'segmented__item segmented__item--ativo' : 'segmented__item'}
              aria-pressed={ativa}
              onClick={() => aplicar({ visao: visao.id })}
            >
              {ativa ? <CheckIcon aria-hidden /> : null}
              {visao.label}
            </button>
          )
        })}
      </div>

      <div className="filtros-barra__popovers" role="group" aria-label="Filtros">
        <Popover
          label={<RotuloFiltro texto="Prioridade" />}
          triggerClassName={classeGatilho(filtros.prioridade.length > 0)}
        >
          <fieldset className="filtros-fieldset">
            <legend>Prioridade</legend>
            <div className="chip-group">
              {PRIORIDADE_OPCOES.map((prioridade) => (
                <label key={prioridade} className="chip">
                  <input
                    type="checkbox"
                    checked={filtros.prioridade.includes(prioridade)}
                    onChange={() =>
                      aplicar({ prioridade: toggleValor(filtros.prioridade, prioridade) })
                    }
                  />
                  {PRIORIDADE_LABEL[prioridade]}
                </label>
              ))}
            </div>
          </fieldset>
        </Popover>

        <Popover
          label={<RotuloFiltro texto="Prazo" />}
          triggerClassName={classeGatilho(Boolean(filtros.prazoDe || filtros.prazoAte))}
        >
          <div className="popover__campos">
            <label className="field">
              Prazo de
              <input
                type="datetime-local"
                value={rascunho.prazoDe}
                onChange={(e) => alterarComDebounce('prazoDe', e.target.value)}
              />
            </label>
            <label className="field">
              Prazo até
              <input
                type="datetime-local"
                value={rascunho.prazoAte}
                onChange={(e) => alterarComDebounce('prazoAte', e.target.value)}
              />
            </label>
          </div>
        </Popover>

        <Popover
          label={<RotuloFiltro texto="Duração" />}
          triggerClassName={classeGatilho(Boolean(filtros.duracaoDe || filtros.duracaoAte))}
        >
          <div className="popover__campos">
            <label className="field">
              Duração de (min)
              <input
                type="number"
                min={1}
                value={rascunho.duracaoDe}
                onChange={(e) => alterarComDebounce('duracaoDe', e.target.value)}
              />
            </label>
            <label className="field">
              Duração até (min)
              <input
                type="number"
                min={1}
                value={rascunho.duracaoAte}
                onChange={(e) => alterarComDebounce('duracaoAte', e.target.value)}
              />
            </label>
          </div>
        </Popover>

        <Popover
          label={<RotuloFiltro texto="Projeto" />}
          triggerClassName={classeGatilho(Boolean(filtros.projetoId))}
        >
          <label className="field">
            Projeto
            <select
              value={filtros.projetoId}
              onChange={(e) => aplicar({ projetoId: e.target.value })}
            >
              <option value="">Todos os projetos</option>
              {projetosAtivos.map((projeto) => (
                <option key={projeto.id} value={projeto.id}>
                  {projeto.nome}
                </option>
              ))}
            </select>
          </label>
        </Popover>

        <Popover
          label={
            <>
              <SortAscendingIcon aria-hidden />
              Ordenar
            </>
          }
          align="end"
          triggerClassName={classeGatilho(ordenacaoAlterada)}
        >
          <div className="popover__campos">
            <label className="field">
              Ordenar por
              <select
                value={filtros.sort}
                onChange={(e) => aplicar({ sort: e.target.value as SortCampo })}
              >
                <option value="prazo">Prazo</option>
                <option value="duracao">Duração</option>
                <option value="prioridade">Prioridade</option>
              </select>
            </label>
            <label className="field">
              Direção
              <select
                value={filtros.direction}
                onChange={(e) => aplicar({ direction: e.target.value as SortDirection })}
              >
                <option value="asc">Ascendente</option>
                <option value="desc">Descendente</option>
              </select>
            </label>
            <label className="field">
              Por página
              <select
                value={filtros.size}
                onChange={(e) => aplicar({ size: Number(e.target.value) })}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
            </label>
          </div>
        </Popover>
      </div>

      {chips.length ? (
        <div className="filtros-barra__chips" role="group" aria-label="Filtros ativos">
          {chips.map((chip) => (
            <button
              key={chip.id}
              type="button"
              className="filtro-chip"
              aria-label={`Remover filtro ${chip.texto}`}
              onClick={() => aplicar(chip.remover)}
            >
              {chip.texto}
              <XIcon aria-hidden />
            </button>
          ))}
          <button type="button" className="btn btn--ghost btn--sm" onClick={limpar}>
            Limpar filtros
          </button>
        </div>
      ) : null}
    </div>
  )
}
