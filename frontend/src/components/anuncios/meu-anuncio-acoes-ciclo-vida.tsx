'use client'

import { PauseIcon, PlayIcon, TrashIcon } from '@heroicons/react/24/solid'
import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  MeusAnunciosApiError,
  pausarMeuAnuncio,
  reativarMeuAnuncio,
  removerMeuAnuncio,
  type MeuAnuncio,
  type MeuAnuncioCicloVida,
} from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'

export type CicloVidaAcao = 'PAUSAR' | 'REATIVAR' | 'REMOVER'

type Props = {
  anuncio: MeuAnuncio
  onSuccess: (resultado: MeuAnuncioCicloVida, acao: CicloVidaAcao) => void
  className?: string
}

function mensagemErro(error: unknown) {
  if (error instanceof MeusAnunciosApiError) {
    if (error.status === 401) return 'Sua sessão expirou. Entre novamente para continuar.'
    if (error.status === 403) return 'Você não tem permissão para alterar este anúncio.'
    if (error.status === 404) return 'O anúncio não foi encontrado.'
    if (error.status === 409) return 'O estado do anúncio mudou e esta ação não é mais permitida.'
  }
  return 'Não foi possível concluir a ação. Tente novamente.'
}

function mensagemSucesso(acao: CicloVidaAcao) {
  if (acao === 'PAUSAR') return 'Anúncio pausado com sucesso.'
  if (acao === 'REATIVAR') return 'Anúncio reativado com sucesso.'
  return 'Anúncio removido com sucesso.'
}

export function MeuAnuncioAcoesCicloVida({ anuncio, onSuccess, className }: Props) {
  const [confirmacao, setConfirmacao] = useState<'PAUSAR' | 'REMOVER' | null>(null)
  const [processando, setProcessando] = useState<CicloVidaAcao | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const emExecucao = useRef(false)

  const executar = async (acao: CicloVidaAcao) => {
    if (emExecucao.current) return
    emExecucao.current = true
    setProcessando(acao)
    setErro(null)
    setSucesso(null)
    try {
      const resultado = acao === 'PAUSAR'
        ? await pausarMeuAnuncio(anuncio.slug)
        : acao === 'REATIVAR'
          ? await reativarMeuAnuncio(anuncio.slug)
          : await removerMeuAnuncio(anuncio.slug)
      setConfirmacao(null)
      setSucesso(mensagemSucesso(acao))
      onSuccess(resultado, acao)
    } catch (error) {
      setErro(mensagemErro(error))
    } finally {
      emExecucao.current = false
      setProcessando(null)
    }
  }

  const possuiAcao = anuncio.acoesPermitidas.pausar
    || anuncio.acoesPermitidas.reativar
    || anuncio.acoesPermitidas.remover

  if (!possuiAcao) return null

  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex flex-wrap gap-2">
        {anuncio.acoesPermitidas.pausar ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={processando !== null}
            onClick={() => setConfirmacao('PAUSAR')}
          >
            <PauseIcon aria-hidden="true" />
            Pausar
          </Button>
        ) : null}

        {anuncio.acoesPermitidas.reativar ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={processando !== null}
            onClick={() => void executar('REATIVAR')}
          >
            <PlayIcon aria-hidden="true" />
            {processando === 'REATIVAR' ? 'Reativando...' : 'Reativar'}
          </Button>
        ) : null}

        {anuncio.acoesPermitidas.remover ? (
          <Button
            type="button"
            variant="destructive"
            size="sm"
            disabled={processando !== null}
            onClick={() => setConfirmacao('REMOVER')}
          >
            <TrashIcon aria-hidden="true" />
            Remover anúncio
          </Button>
        ) : null}
      </div>

      {erro ? <p role="alert" className="text-sm text-rose-700">{erro}</p> : null}
      {sucesso ? <p role="status" className="text-sm text-emerald-700">{sucesso}</p> : null}

      <Dialog
        open={confirmacao !== null}
        onOpenChange={(open) => {
          if (!open && processando === null) setConfirmacao(null)
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {confirmacao === 'PAUSAR' ? 'Pausar anúncio?' : 'Remover anúncio?'}
            </DialogTitle>
            <DialogDescription>
              {confirmacao === 'PAUSAR'
                ? 'O anúncio deixará de aparecer publicamente até ser reativado.'
                : 'O anúncio deixará de aparecer publicamente. Os dados e as mídias serão preservados.'}
            </DialogDescription>
          </DialogHeader>
          {erro ? <p role="alert" className="text-sm text-rose-700">{erro}</p> : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={processando !== null}
              onClick={() => setConfirmacao(null)}
            >
              Cancelar
            </Button>
            <Button
              type="button"
              variant={confirmacao === 'REMOVER' ? 'destructive' : 'default'}
              disabled={processando !== null || confirmacao === null}
              onClick={() => {
                if (confirmacao) void executar(confirmacao)
              }}
            >
              {processando === 'PAUSAR'
                ? 'Pausando...'
                : processando === 'REMOVER'
                  ? 'Removendo...'
                  : 'Confirmar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
