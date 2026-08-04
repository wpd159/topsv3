import { ApiContractError } from '@/lib/api-contract'

const ROTULOS: Record<string, string> = {
  AJUSTE_ADMIN_NEGATIVO: 'Ajuste negativo',
  AJUSTE_ADMIN_POSITIVO: 'Ajuste positivo',
  CREDITO: 'Crédito',
  DEBITO: 'Débito',
  ESTORNO: 'Estorno',
  MIGRACAO_SALDO_INICIAL: 'Saldo inicial migrado',
  ATIVA: 'Ativa',
  CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada',
  ADMIN: 'Concessão administrativa',
  COMPRA_CREDITOS: 'Compra com créditos',
  CORTESIA: 'Cortesia',
  CREDITO_ADMIN_AJUSTAR: 'Ajuste administrativo de créditos',
  CREDITO_ADMIN_ESTORNAR: 'Estorno administrativo de créditos',
  PREMIUM_CATALOGO_ATUALIZAR: 'Catálogo Premium atualizado',
  PREMIUM_ATIVACAO_CANCELAR: 'Ativação Premium cancelada',
  STORY_CONFIGURACAO_ATUALIZAR: 'Configuração de Stories atualizada',
  MOVIMENTO_CREDITO: 'Movimento de créditos',
  BENEFICIO_PREMIUM: 'Benefício Premium',
  ATIVACAO_BENEFICIO: 'Ativação de benefício',
  STORY_CONFIGURACAO_COMERCIAL: 'Configuração comercial de Stories',
}

export function rotuloOperacional(valor: string | null | undefined) {
  if (!valor) return '-'
  return ROTULOS[valor]
    ?? valor.toLowerCase().replaceAll('_', ' ').replace(/^./, (letra) => letra.toUpperCase())
}

export function dataHora(valor: string | null | undefined) {
  if (!valor) return '-'
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(valor))
}

export function mensagemErro(error: unknown, fallback: string) {
  if (!(error instanceof Error)) return fallback
  if (error instanceof ApiContractError && error.requestId) {
    return `${error.message} Referência: ${error.requestId}.`
  }
  return error.message || fallback
}