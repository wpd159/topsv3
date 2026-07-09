'use client'

import { useRouter } from 'next/navigation'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface CreditoBloqueioModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function CreditoBloqueioModal({ open, onOpenChange }: CreditoBloqueioModalProps) {
  const router = useRouter()

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Compra de créditos indisponível</DialogTitle>
          <DialogDescription>
            Você só pode comprar créditos quando tiver pelo menos um anúncio ativo e aprovado.
          </DialogDescription>
        </DialogHeader>

        <DialogFooter className="mt-4">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
          >
            Fechar
          </Button>
          <Button
            className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
            onClick={() => {
              onOpenChange(false)
              router.push('/meus-anuncios')
            }}
          >
            Ir para meus anúncios
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
