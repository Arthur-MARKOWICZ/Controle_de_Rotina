import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError } from '../../auth/api/authApi'
import {
  atualizarCapacidadeOverride,
  criarCapacidadeOverride,
  excluirCapacidadeOverride,
  listarCapacidadesOverride,
  restaurarCapacidadeOverride,
  type CapacidadeOverrideResponse,
} from '../api/capacidadeOverrideApi'
import {
  consultarCapacidadeResolvida,
  type CapacidadeResolvidaResponse,
  type OrigemCapacidade,
} from '../api/capacidadeResolvidaApi'
import {
  atualizarCapacidadeSemanal,
  criarCapacidadeSemanal,
  excluirCapacidadeSemanal,
  listarCapacidadesSemanais,
  restaurarCapacidadeSemanal,
  type CapacidadeSemanalResponse,
  type DiaSemana,
} from '../api/capacidadeSemanalApi'
import {
  adicionarDiasIso,
  datasDaSemana,
  formatarIntervaloSemana,
  inicioSemana,
  parseDataIsoLocal,
} from '../semana'

type VisibilidadeDelecao = 'ativas' | 'incluir' | 'somente'

const DIAS: { valor: DiaSemana; label: string }[] = [
  { valor: 'SEGUNDA', label: 'Segunda' },
  { valor: 'TERCA', label: 'Terça' },
  { valor: 'QUARTA', label: 'Quarta' },
  { valor: 'QUINTA', label: 'Quinta' },
  { valor: 'SEXTA', label: 'Sexta' },
  { valor: 'SABADO', label: 'Sábado' },
  { valor: 'DOMINGO', label: 'Domingo' },
]

const ORIGEM_LABEL: Record<OrigemCapacidade, string> = {
  SEMANAL: 'Semanal',
  OVERRIDE: 'Override',
  VAZIO: 'Vazio',
}

function paramsDelecao(visibilidade: VisibilidadeDelecao): {
  incluirDeletadas?: boolean
  somenteDeletadas?: boolean
} {
  if (visibilidade === 'incluir') {
    return { incluirDeletadas: true, somenteDeletadas: false }
  }
  if (visibilidade === 'somente') {
    return { incluirDeletadas: false, somenteDeletadas: true }
  }
  return {}
}

function mapaPorDia(
  lista: CapacidadeSemanalResponse[],
): Partial<Record<DiaSemana, CapacidadeSemanalResponse>> {
  const mapa: Partial<Record<DiaSemana, CapacidadeSemanalResponse>> = {}
  for (const item of lista) {
    mapa[item.diaSemana] = item
  }
  return mapa
}

function mapaResolvidaPorData(
  lista: CapacidadeResolvidaResponse[],
): Record<string, CapacidadeResolvidaResponse> {
  const mapa: Record<string, CapacidadeResolvidaResponse> = {}
  for (const item of lista) {
    mapa[item.data] = item
  }
  return mapa
}

function formatarDataBr(iso: string): string {
  return parseDataIsoLocal(iso).toLocaleDateString('pt-BR')
}

function textoEfetivas(resolvida: CapacidadeResolvidaResponse | undefined): string {
  if (!resolvida || resolvida.origem === 'VAZIO' || resolvida.horas == null) {
    return 'Efetivas: vazio'
  }
  return `Efetivas: ${resolvida.horas}h`
}

export function CapacidadePage() {
  const [visibilidade, setVisibilidade] = useState<VisibilidadeDelecao>('ativas')
  const [aplicada, setAplicada] = useState<VisibilidadeDelecao>('ativas')
  const [capacidades, setCapacidades] = useState<CapacidadeSemanalResponse[]>([])
  const [rascunhos, setRascunhos] = useState<Partial<Record<DiaSemana, string>>>({})
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [acaoDia, setAcaoDia] = useState<DiaSemana | null>(null)
  const [recarregarEm, setRecarregarEm] = useState(0)

  const [semanaInicio, setSemanaInicio] = useState(() => inicioSemana())
  const [resolvidas, setResolvidas] = useState<CapacidadeResolvidaResponse[]>([])

  const [visibilidadeOverride, setVisibilidadeOverride] =
    useState<VisibilidadeDelecao>('ativas')
  const [aplicadaOverride, setAplicadaOverride] =
    useState<VisibilidadeDelecao>('ativas')
  const [overrides, setOverrides] = useState<CapacidadeOverrideResponse[]>([])
  const [overrideData, setOverrideData] = useState('')
  const [overrideHoras, setOverrideHoras] = useState('')
  const [editandoOverrideId, setEditandoOverrideId] = useState<string | null>(null)
  const [acaoOverrideId, setAcaoOverrideId] = useState<string | null>(null)
  const [salvandoOverride, setSalvandoOverride] = useState(false)
  const [recarregarOverridesEm, setRecarregarOverridesEm] = useState(0)

  useEffect(() => {
    let ativo = true

    async function carregar() {
      setCarregando(true)
      setErro(null)
      try {
        const dataAte = adicionarDiasIso(semanaInicio, 6)
        const [semanais, resolvida] = await Promise.all([
          listarCapacidadesSemanais(paramsDelecao(aplicada)),
          consultarCapacidadeResolvida({ dataDe: semanaInicio, dataAte }),
        ])
        if (ativo) {
          setCapacidades(semanais)
          setResolvidas(resolvida)
          const iniciais: Partial<Record<DiaSemana, string>> = {}
          for (const item of semanais) {
            iniciais[item.diaSemana] = String(item.horas)
          }
          setRascunhos(iniciais)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar as capacidades.')
          setCapacidades([])
          setResolvidas([])
        }
      } finally {
        if (ativo) {
          setCarregando(false)
        }
      }
    }

    void carregar()
    return () => {
      ativo = false
    }
  }, [aplicada, recarregarEm, semanaInicio])

  useEffect(() => {
    let ativo = true

    async function carregarOverrides() {
      try {
        const data = await listarCapacidadesOverride({
          ...paramsDelecao(aplicadaOverride),
          dataDe: adicionarDiasIso(semanaInicio, -14),
          dataAte: adicionarDiasIso(semanaInicio, 20),
        })
        if (ativo) {
          setOverrides(data)
        }
      } catch {
        if (ativo) {
          setErro('Não foi possível carregar os overrides.')
          setOverrides([])
        }
      }
    }

    void carregarOverrides()
    return () => {
      ativo = false
    }
  }, [aplicadaOverride, recarregarOverridesEm, semanaInicio])

  const porDia = mapaPorDia(capacidades)
  const porDataResolvida = mapaResolvidaPorData(resolvidas)
  const datasSemana = datasDaSemana(semanaInicio)

  function handleFiltrar(event: FormEvent) {
    event.preventDefault()
    setAplicada(visibilidade)
  }

  function handleFiltrarOverrides(event: FormEvent) {
    event.preventDefault()
    setAplicadaOverride(visibilidadeOverride)
  }

  function valorInput(dia: DiaSemana): string {
    return rascunhos[dia] ?? ''
  }

  function setValorInput(dia: DiaSemana, valor: string) {
    setRascunhos((atual) => ({ ...atual, [dia]: valor }))
  }

  async function handleSalvar(dia: DiaSemana) {
    setErro(null)
    const texto = valorInput(dia).trim()
    if (texto === '') {
      setErro('Informe as horas (0 ou mais) para salvar.')
      return
    }
    const horas = Number(texto)
    if (!Number.isInteger(horas) || horas < 0) {
      setErro('Horas deve ser um inteiro maior ou igual a zero.')
      return
    }

    setAcaoDia(dia)
    try {
      const existente = porDia[dia]
      if (existente && existente.ativo) {
        await atualizarCapacidadeSemanal(existente.id, { horas })
      } else if (existente && !existente.ativo) {
        setErro('Restaure o dia antes de editar as horas.')
        return
      } else {
        await criarCapacidadeSemanal({ diaSemana: dia, horas })
      }
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível salvar a capacidade.',
      )
    } finally {
      setAcaoDia(null)
    }
  }

  async function handleExcluir(dia: DiaSemana) {
    const existente = porDia[dia]
    if (!existente || !existente.ativo) {
      return
    }
    setErro(null)
    setAcaoDia(dia)
    try {
      await excluirCapacidadeSemanal(existente.id)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível excluir a capacidade.',
      )
    } finally {
      setAcaoDia(null)
    }
  }

  async function handleRestaurar(dia: DiaSemana) {
    const existente = porDia[dia]
    if (!existente || existente.ativo) {
      return
    }
    setErro(null)
    setAcaoDia(dia)
    try {
      await restaurarCapacidadeSemanal(existente.id)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível restaurar a capacidade.',
      )
    } finally {
      setAcaoDia(null)
    }
  }

  function limparFormOverride() {
    setOverrideData('')
    setOverrideHoras('')
    setEditandoOverrideId(null)
  }

  function iniciarEdicaoOverride(item: CapacidadeOverrideResponse) {
    setEditandoOverrideId(item.id)
    setOverrideData(item.data)
    setOverrideHoras(String(item.horas))
  }

  async function handleSalvarOverride(event: FormEvent) {
    event.preventDefault()
    setErro(null)

    const data = overrideData.trim()
    const textoHoras = overrideHoras.trim()
    if (!data) {
      setErro('Informe a data do override.')
      return
    }
    if (textoHoras === '') {
      setErro('Informe as horas do override (0 ou mais).')
      return
    }
    const horas = Number(textoHoras)
    if (!Number.isInteger(horas) || horas < 0) {
      setErro('Horas do override deve ser um inteiro maior ou igual a zero.')
      return
    }

    setSalvandoOverride(true)
    try {
      if (editandoOverrideId) {
        await atualizarCapacidadeOverride(editandoOverrideId, { data, horas })
      } else {
        await criarCapacidadeOverride({ data, horas })
      }
      limparFormOverride()
      setRecarregarOverridesEm((n) => n + 1)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível salvar o override.',
      )
    } finally {
      setSalvandoOverride(false)
    }
  }

  async function handleExcluirOverride(id: string) {
    setErro(null)
    setAcaoOverrideId(id)
    try {
      await excluirCapacidadeOverride(id)
      setRecarregarOverridesEm((n) => n + 1)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível excluir o override.',
      )
    } finally {
      setAcaoOverrideId(null)
    }
  }

  async function handleRestaurarOverride(id: string) {
    setErro(null)
    setAcaoOverrideId(id)
    try {
      await restaurarCapacidadeOverride(id)
      setRecarregarOverridesEm((n) => n + 1)
      setRecarregarEm((n) => n + 1)
    } catch (error) {
      setErro(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível restaurar o override.',
      )
    } finally {
      setAcaoOverrideId(null)
    }
  }

  return (
    <main className="capacidade-page">
      <header className="capacidade-header">
        <div>
          <h1>Capacidade</h1>
          <p className="capacidade-subtitle">
            Defina quantas horas você tem disponíveis em cada dia da semana.
          </p>
        </div>
        <div className="capacidade-header-actions">
          <Link to="/">Início</Link>
        </div>
      </header>

      <form className="capacidade-filtros" onSubmit={handleFiltrar}>
        <label>
          Excluídas
          <select
            value={visibilidade}
            onChange={(e) => setVisibilidade(e.target.value as VisibilidadeDelecao)}
          >
            <option value="ativas">Somente ativas</option>
            <option value="incluir">Incluir excluídas</option>
            <option value="somente">Somente excluídas</option>
          </select>
        </label>
        <button type="submit">Aplicar filtro</button>
      </form>

      <div className="capacidade-semana-nav" aria-label="Semana selecionada">
        <button
          type="button"
          className="button-table"
          onClick={() => setSemanaInicio((atual) => adicionarDiasIso(atual, -7))}
        >
          Semana anterior
        </button>
        <p className="capacidade-semana-label">
          Semana {formatarIntervaloSemana(semanaInicio)}
        </p>
        <button
          type="button"
          className="button-table"
          onClick={() => setSemanaInicio((atual) => adicionarDiasIso(atual, 7))}
        >
          Semana seguinte
        </button>
      </div>

      {erro ? (
        <p className="auth-error" role="alert">
          {erro}
        </p>
      ) : null}

      {carregando ? <p>Carregando capacidade…</p> : null}

      {!carregando ? (
        <div className="capacidade-grade" role="list">
          {DIAS.map(({ valor, label }) => {
            const registro = porDia[valor]
            const inativo = registro != null && !registro.ativo
            const ocupado = acaoDia === valor
            const labelLower = label.toLowerCase()
            const dataDia = datasSemana[valor]
            const resolvida = porDataResolvida[dataDia]
            const comOverride = resolvida?.origem === 'OVERRIDE'

            return (
              <article
                key={valor}
                className={[
                  'capacidade-celula',
                  inativo ? 'capacidade-celula-deletada' : '',
                  comOverride ? 'capacidade-celula-override' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                role="listitem"
              >
                <div className="capacidade-celula-topo">
                  <h2>{label}</h2>
                  {inativo ? (
                    <span className="status-badge status-deletada">Excluída</span>
                  ) : null}
                </div>

                <p className="capacidade-data-celula">{formatarDataBr(dataDia)}</p>

                <p className="capacidade-efetivas">
                  {textoEfetivas(resolvida)}
                  {resolvida ? (
                    <span className="capacidade-origem">
                      {' '}
                      ({ORIGEM_LABEL[resolvida.origem]})
                    </span>
                  ) : null}
                </p>

                <label>
                  Horas de {labelLower}
                  <input
                    type="number"
                    min={0}
                    step={1}
                    value={valorInput(valor)}
                    disabled={inativo || ocupado}
                    onChange={(e) => setValorInput(valor, e.target.value)}
                  />
                </label>

                <div className="capacidade-celula-acoes">
                  {inativo ? (
                    <button
                      type="button"
                      className="button-table"
                      disabled={ocupado}
                      onClick={() => void handleRestaurar(valor)}
                    >
                      {ocupado ? 'Restaurando…' : `Restaurar ${labelLower}`}
                    </button>
                  ) : (
                    <>
                      <button
                        type="button"
                        disabled={ocupado}
                        onClick={() => void handleSalvar(valor)}
                      >
                        {ocupado ? 'Salvando…' : `Salvar ${labelLower}`}
                      </button>
                      {registro?.ativo ? (
                        <button
                          type="button"
                          className="button-table button-table-danger"
                          disabled={ocupado}
                          onClick={() => void handleExcluir(valor)}
                        >
                          {`Excluir ${labelLower}`}
                        </button>
                      ) : null}
                    </>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      ) : null}

      <section className="capacidade-overrides" aria-labelledby="overrides-titulo">
        <h2 id="overrides-titulo">Overrides por data</h2>
        <p className="capacidade-subtitle">
          Exceções para um dia concreto (prevalecem sobre o template da semana).
        </p>

        <form className="capacidade-filtros" onSubmit={handleFiltrarOverrides}>
          <label>
            Overrides excluídos
            <select
              value={visibilidadeOverride}
              onChange={(e) =>
                setVisibilidadeOverride(e.target.value as VisibilidadeDelecao)
              }
            >
              <option value="ativas">Somente ativos</option>
              <option value="incluir">Incluir excluídos</option>
              <option value="somente">Somente excluídos</option>
            </select>
          </label>
          <button type="submit">Aplicar filtro de overrides</button>
        </form>

        <form className="capacidade-override-form" onSubmit={(e) => void handleSalvarOverride(e)}>
          <label>
            Data do override
            <input
              type="date"
              value={overrideData}
              onChange={(e) => setOverrideData(e.target.value)}
              disabled={salvandoOverride}
            />
          </label>
          <label>
            Horas do override
            <input
              type="number"
              min={0}
              step={1}
              value={overrideHoras}
              onChange={(e) => setOverrideHoras(e.target.value)}
              disabled={salvandoOverride}
            />
          </label>
          <div className="capacidade-override-form-acoes">
            <button type="submit" disabled={salvandoOverride}>
              {salvandoOverride
                ? 'Salvando…'
                : editandoOverrideId
                  ? 'Atualizar override'
                  : 'Salvar override'}
            </button>
            {editandoOverrideId ? (
              <button
                type="button"
                className="button-table"
                disabled={salvandoOverride}
                onClick={limparFormOverride}
              >
                Cancelar edição
              </button>
            ) : null}
          </div>
        </form>

        <ul className="capacidade-override-lista">
          {overrides.length === 0 ? (
            <li className="capacidade-override-vazio">Nenhum override neste período.</li>
          ) : (
            overrides.map((item) => {
              const dataBr = formatarDataBr(item.data)
              const ocupado = acaoOverrideId === item.id
              return (
                <li
                  key={item.id}
                  className={
                    item.ativo
                      ? 'capacidade-override-item'
                      : 'capacidade-override-item capacidade-override-item-deletado'
                  }
                >
                  <div>
                    <strong>{dataBr}</strong>
                    <span> — {item.horas}h</span>
                    {!item.ativo ? (
                      <span className="status-badge status-deletada">Excluído</span>
                    ) : null}
                  </div>
                  <div className="capacidade-celula-acoes">
                    {item.ativo ? (
                      <>
                        <button
                          type="button"
                          className="button-table"
                          disabled={ocupado}
                          onClick={() => iniciarEdicaoOverride(item)}
                        >
                          {`Editar override ${dataBr}`}
                        </button>
                        <button
                          type="button"
                          className="button-table button-table-danger"
                          disabled={ocupado}
                          onClick={() => void handleExcluirOverride(item.id)}
                        >
                          {ocupado ? 'Excluindo…' : `Excluir override ${dataBr}`}
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        className="button-table"
                        disabled={ocupado}
                        onClick={() => void handleRestaurarOverride(item.id)}
                      >
                        {ocupado ? 'Restaurando…' : `Restaurar override ${dataBr}`}
                      </button>
                    )}
                  </div>
                </li>
              )
            })
          )}
        </ul>
      </section>
    </main>
  )
}
