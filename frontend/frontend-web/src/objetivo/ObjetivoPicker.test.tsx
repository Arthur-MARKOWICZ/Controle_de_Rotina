import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '../auth/api/authApi'
import * as objetivoApi from './api/objetivoApi'
import type { ObjetivoResponse, ObjetivoResumo, PageObjetivo } from './api/objetivoApi'
import { ObjetivoPicker } from './ObjetivoPicker'

vi.mock('./api/objetivoApi', () => ({
  listarObjetivos: vi.fn(),
}))

function objetivo(id: string, titulo: string, parcial: Partial<ObjetivoResponse> = {}): ObjetivoResponse {
  return {
    id,
    titulo,
    descricao: null,
    prazo: null,
    status: 'ATIVO',
    concluidoEm: null,
    arquivadoEm: null,
    createdAt: '2026-11-01T12:00:00Z',
    updatedAt: '2026-11-01T12:00:00Z',
    progresso: null,
    ...parcial,
  }
}

function pagina(content: ObjetivoResponse[]): PageObjetivo {
  return {
    content,
    page: { size: 100, number: 0, totalElements: content.length, totalPages: content.length ? 1 : 0 },
  }
}

const CORRER = objetivo('o1', 'Correr 10 km')
const LER = objetivo('o2', 'Ler 12 livros')

const resumo = (o: ObjetivoResponse): ObjetivoResumo => ({
  id: o.id,
  titulo: o.titulo,
  status: o.status,
})

function renderPicker(props: Partial<React.ComponentProps<typeof ObjetivoPicker>> = {}) {
  const onChange = vi.fn()
  render(
    <MemoryRouter>
      <ObjetivoPicker selectedIds={[]} onChange={onChange} {...props} />
    </MemoryRouter>,
  )
  return { onChange }
}

async function abrir(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => expect(objetivoApi.listarObjetivos).toHaveBeenCalled())
  await user.click(screen.getByRole('button', { name: /escolher objetivos/i }))
}

describe('ObjetivoPicker', () => {
  beforeEach(() => {
    vi.mocked(objetivoApi.listarObjetivos).mockReset()
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(pagina([CORRER, LER]))
  })

  it('busca só os ativos e escolher uma opção emite ids e resumos', async () => {
    const user = userEvent.setup()
    const { onChange } = renderPicker()

    await abrir(user)
    await user.click(await screen.findByRole('button', { name: 'Correr 10 km' }))

    expect(objetivoApi.listarObjetivos).toHaveBeenCalledWith({ status: 'ATIVO', size: 100 })
    expect(onChange).toHaveBeenCalledWith(['o1'], [resumo(CORRER)])
  })

  it('escolher uma segunda opção mantém a primeira, na ordem escolhida', async () => {
    const user = userEvent.setup()
    const { onChange } = renderPicker({ selectedIds: ['o1'] })

    await abrir(user)
    await user.click(await screen.findByRole('button', { name: 'Ler 12 livros' }))

    expect(onChange).toHaveBeenCalledWith(['o1', 'o2'], [resumo(CORRER), resumo(LER)])
  })

  it('opção já escolhida aparece marcada e clicar nela desmarca', async () => {
    const user = userEvent.setup()
    const { onChange } = renderPicker({ selectedIds: ['o1', 'o2'] })

    await abrir(user)
    const opcao = await screen.findByRole('button', { name: 'Correr 10 km', pressed: true })
    await user.click(opcao)

    expect(onChange).toHaveBeenCalledWith(['o2'], [resumo(LER)])
  })

  it('chip de objetivo concluído mostra o status, não é opção e é removível', async () => {
    const concluido: ObjetivoResumo = { id: 'o9', titulo: 'Perder 5 kg', status: 'CONCLUIDO' }
    const user = userEvent.setup()
    const { onChange } = renderPicker({ selectedIds: ['o9'], selectedObjetivos: [concluido] })

    await abrir(user)
    await screen.findByRole('button', { name: 'Correr 10 km' })

    const chip = screen.getByRole('button', { name: 'Remover objetivo Perder 5 kg' })
    expect(chip).toHaveTextContent('Concluído')
    // Só os ativos viram opção: o encerrado aparece apenas como chip.
    expect(screen.getAllByText(/Perder 5 kg/)).toHaveLength(1)

    await user.click(chip)
    expect(onChange).toHaveBeenCalledWith([], [])
  })

  it('chip de objetivo arquivado mostra "Arquivado"', async () => {
    const arquivado: ObjetivoResumo = { id: 'o8', titulo: 'Aprender violão', status: 'ARQUIVADO' }
    renderPicker({ selectedIds: ['o8'], selectedObjetivos: [arquivado] })

    expect(
      await screen.findByRole('button', { name: 'Remover objetivo Aprender violão' }),
    ).toHaveTextContent('Arquivado')
    await waitFor(() => expect(objetivoApi.listarObjetivos).toHaveBeenCalled())
  })

  it('remover o último chip emite lista vazia', async () => {
    const user = userEvent.setup()
    const { onChange } = renderPicker({ selectedIds: ['o1'], selectedObjetivos: [resumo(CORRER)] })

    await user.click(await screen.findByRole('button', { name: 'Remover objetivo Correr 10 km' }))

    expect(onChange).toHaveBeenCalledWith([], [])
  })

  it('busca filtra pelo título, sem diferenciar maiúsculas', async () => {
    const user = userEvent.setup()
    renderPicker()
    await abrir(user)
    await screen.findByRole('button', { name: 'Correr 10 km' })

    await user.type(screen.getByRole('searchbox', { name: 'Buscar objetivo' }), 'LIVROS')

    expect(screen.getByRole('button', { name: 'Ler 12 livros' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Correr 10 km' })).not.toBeInTheDocument()
  })

  it('busca sem resultado informa que não há objetivo com o termo', async () => {
    const user = userEvent.setup()
    renderPicker()
    await abrir(user)
    await screen.findByRole('button', { name: 'Correr 10 km' })

    await user.type(screen.getByRole('searchbox', { name: 'Buscar objetivo' }), 'inglês')

    expect(screen.getByText('Nenhum objetivo com esse termo.')).toBeInTheDocument()
    expect(screen.queryByText(/Nenhum objetivo ativo/)).not.toBeInTheDocument()
  })

  it('sem objetivos ativos mostra o aviso e o link para a página de Objetivos', async () => {
    vi.mocked(objetivoApi.listarObjetivos).mockResolvedValue(pagina([]))
    const user = userEvent.setup()
    renderPicker()

    await abrir(user)

    expect(await screen.findByText(/Nenhum objetivo ativo/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver objetivos' })).toHaveAttribute('href', '/objetivos')
  })

  it('desabilitado não emite ao escolher nem ao remover', async () => {
    const user = userEvent.setup()
    const { onChange } = renderPicker({
      selectedIds: ['o1'],
      selectedObjetivos: [resumo(CORRER)],
      disabled: true,
    })

    expect(await screen.findByRole('button', { name: 'Remover objetivo Correr 10 km' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /escolher objetivos/i }))
    await user.click(await screen.findByRole('button', { name: 'Ler 12 livros' }))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('falha de rede mostra o alerta e mantém os chips já escolhidos', async () => {
    vi.mocked(objetivoApi.listarObjetivos).mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    renderPicker({ selectedIds: ['o1'], selectedObjetivos: [resumo(CORRER)] })

    await abrir(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar os objetivos.',
    )
    expect(screen.getByRole('button', { name: 'Remover objetivo Correr 10 km' })).toBeInTheDocument()
    expect(screen.queryByText(/Nenhum objetivo ativo/)).not.toBeInTheDocument()
  })

  it('HTML do proxy (resposta inválida) mostra o mesmo alerta', async () => {
    vi.mocked(objetivoApi.listarObjetivos).mockRejectedValue(
      new ApiError(200, 'Resposta inválida da API (não é JSON).'),
    )
    const user = userEvent.setup()
    renderPicker()

    await abrir(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível carregar os objetivos.',
    )
  })

  it('erro 500 da API também mostra o alerta, sem opções', async () => {
    vi.mocked(objetivoApi.listarObjetivos).mockRejectedValue(
      new ApiError(500, 'Não foi possível carregar os objetivos.'),
    )
    const user = userEvent.setup()
    renderPicker()

    await abrir(user)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Correr 10 km' })).not.toBeInTheDocument()
  })
})
