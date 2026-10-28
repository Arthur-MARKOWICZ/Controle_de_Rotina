import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { buscarMe } from '../auth/api/meApi'
import { listarCapacidadesSemanais } from '../capacidade/api/capacidadeSemanalApi'
import { listarTarefas } from '../tarefa/api/tarefaApi'
import { dispensarOnboarding, onboardingFoiDispensado } from './onboardingStorage'

export type PassosOnboarding = {
  capacidade: boolean
  tarefa: boolean
  plano: boolean
}

type Props = {
  temPlano: boolean
  onAbrirQuickAdd: () => void
  onGerarPlano?: () => void
}

function capacidadeConfigurada(
  lista: { horas: number; ativo: boolean }[],
): boolean {
  return lista.some((item) => item.ativo && item.horas > 0)
}

export function OnboardingChecklist({ temPlano, onAbrirQuickAdd, onGerarPlano }: Props) {
  const [visivel, setVisivel] = useState(false)
  const [passos, setPassos] = useState<PassosOnboarding | null>(null)
  const [usuarioId, setUsuarioId] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true

    async function carregar() {
      try {
        const [me, capacidades, tarefas] = await Promise.all([
          buscarMe(),
          listarCapacidadesSemanais(),
          listarTarefas({ size: 1 }),
        ])
        if (!ativo) {
          return
        }
        if (onboardingFoiDispensado(me.id)) {
          setVisivel(false)
          return
        }
        const status: PassosOnboarding = {
          capacidade: capacidadeConfigurada(capacidades),
          tarefa: tarefas.totalElements > 0 || tarefas.content.length > 0,
          plano: temPlano,
        }
        setUsuarioId(me.id)
        setPassos(status)
        setVisivel(!(status.capacidade && status.tarefa && status.plano))
      } catch {
        if (ativo) {
          setVisivel(false)
        }
      }
    }

    void carregar()
    return () => {
      ativo = false
    }
  }, [temPlano])

  if (!visivel || !passos) {
    return null
  }

  function handleDispensar() {
    if (usuarioId) {
      dispensarOnboarding(usuarioId)
    }
    setVisivel(false)
  }

  return (
    <section className="onboarding-checklist card" aria-labelledby="onboarding-titulo">
      <div className="onboarding-checklist__cabecalho">
        <h2 id="onboarding-titulo">Primeiros passos</h2>
        <button type="button" className="btn btn--ghost btn--sm" onClick={handleDispensar}>
          Não mostrar de novo
        </button>
      </div>
      <p className="onboarding-checklist__intro">
        Configure o essencial para o sistema montar seu plano do dia.
      </p>
      <ol className="onboarding-checklist__lista">
        <li data-ok={passos.capacidade ? 'true' : 'false'}>
          <span className="onboarding-checklist__status" aria-hidden="true">
            {passos.capacidade ? '✓' : '1.'}
          </span>
          {passos.capacidade ? (
            <span>Capacidade semanal definida</span>
          ) : (
            <Link to="/capacidade">Definir capacidade semanal</Link>
          )}
        </li>
        <li data-ok={passos.tarefa ? 'true' : 'false'}>
          <span className="onboarding-checklist__status" aria-hidden="true">
            {passos.tarefa ? '✓' : '2.'}
          </span>
          {passos.tarefa ? (
            <span>Primeira tarefa criada</span>
          ) : (
            <button type="button" className="onboarding-checklist__link-btn" onClick={onAbrirQuickAdd}>
              Criar primeira tarefa
            </button>
          )}
        </li>
        <li data-ok={passos.plano ? 'true' : 'false'}>
          <span className="onboarding-checklist__status" aria-hidden="true">
            {passos.plano ? '✓' : '3.'}
          </span>
          {passos.plano ? (
            <span>Primeiro plano gerado</span>
          ) : onGerarPlano ? (
            <button type="button" className="onboarding-checklist__link-btn" onClick={onGerarPlano}>
              Gerar primeiro plano
            </button>
          ) : (
            <span>Gere o plano do dia abaixo</span>
          )}
        </li>
      </ol>
    </section>
  )
}
