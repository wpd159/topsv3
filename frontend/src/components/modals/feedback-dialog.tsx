'use client'

import { useRouter } from 'next/navigation'
import { BugAntIcon, ChatBubbleBottomCenterTextIcon, ListBulletIcon } from '@heroicons/react/24/outline'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface FeedbackDialogProps {
  open: boolean
  onOpenChange: (value: boolean) => void
}

const opcoes = [
  {
    titulo: 'Reportar problema',
    descricao: 'Abra um atendimento com a categoria Erro no sistema.',
    href: '/meus-tickets?novo=1&categoria=ERRO_NO_SISTEMA',
    icon: BugAntIcon,
  },
  {
    titulo: 'Enviar sugestão',
    descricao: 'Envie uma ideia e acompanhe a resposta da equipe.',
    href: '/meus-tickets?novo=1&categoria=SUGESTAO',
    icon: ChatBubbleBottomCenterTextIcon,
  },
  {
    titulo: 'Ver meus atendimentos',
    descricao: 'Consulte protocolos, status e mensagens do suporte.',
    href: '/meus-tickets',
    icon: ListBulletIcon,
  },
] as const

export default function FeedbackDialog({ open, onOpenChange }: FeedbackDialogProps) {
  const router = useRouter()

  const navegar = (href: string) => {
    onOpenChange(false)
    router.push(href)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[100dvh] overflow-y-auto rounded-lg p-6 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-center">Suporte e feedback</DialogTitle>
          <DialogDescription className="text-center text-gray-600">
            Escolha como deseja falar com a equipe e acompanhe tudo pelo protocolo.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 grid gap-3">
          {opcoes.map((opcao) => {
            const Icon = opcao.icon
            return (
              <button
                key={opcao.href}
                type="button"
                onClick={() => navegar(opcao.href)}
                className="flex min-h-16 w-full items-center gap-3 rounded-md border border-gray-200 p-4 text-left transition hover:border-[#FC1EAD] hover:bg-pink-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD]"
              >
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-pink-50 text-[#C41E73]">
                  <Icon className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold text-gray-900">{opcao.titulo}</span>
                  <span className="mt-1 block text-sm text-gray-600">{opcao.descricao}</span>
                </span>
              </button>
            )
          })}
        </div>
      </DialogContent>
    </Dialog>
  )
}
