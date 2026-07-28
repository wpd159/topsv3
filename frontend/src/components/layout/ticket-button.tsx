'use client'

import { useEffect, useRef, useState } from 'react'
import { usePathname } from 'next/navigation'
import { ChatBubbleLeftRightIcon, PaperAirplaneIcon } from '@heroicons/react/24/solid'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select'
import { useAuth } from '@/context/AuthContext'
import { useSuporteChat } from '@/hooks/useSuporteChat'
import { ContractState } from '@/components/feedback/contract-state'

type MotivoUI = 'erro' | 'pagamento' | 'conta' | 'outros'
const motivoMap: Record<MotivoUI, string> = {
  erro: 'ERRO_NO_SISTEMA',
  pagamento: 'PROBLEMAS_COM_PAGAMENTO',
  conta: 'ACESSO_CONTA',
  outros: 'OUTROS',
}

export default function AbrirTicketButton() {
  const pathname = usePathname()
  const { usuario } = useAuth()
  const [open, setOpen] = useState(false)
  const [problema, setProblema] = useState<MotivoUI | ''>('')
  const [assunto, setAssunto] = useState('')
  const [descricao, setDescricao] = useState('')
  const endOfMessagesRef = useRef<HTMLDivElement | null>(null)

  const {
    ticketAberto,
    mensagens,
    novaMensagem,
    setNovaMensagem,
    abrirChamado,
    enviarMensagem,
    encerrarTicket,
    contractError,
    processando,
  } = useSuporteChat(usuario)

  const emitChanged = () => window.dispatchEvent(new Event('suporte-ticket-changed'))

  // Auto-scroll
  useEffect(() => {
    if (mensagens.length > 0) endOfMessagesRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensagens])

  // Ouve evento global
  useEffect(() => {
    const handler = () => setOpen(true)
    window.addEventListener('open-suporte-ticket', handler as EventListener)
    return () => window.removeEventListener('open-suporte-ticket', handler as EventListener)
  }, [])

  // não renderiza em rotas internas
  if (!usuario || pathname.startsWith('/admin') || pathname.startsWith('/chat')) return null

  const hideFab = pathname.startsWith('/meus-tickets') // ✅ aqui a tela já tem botão

  const handleAbrirChamado = async () => {
    if (!problema || assunto.trim().length < 6 || descricao.trim().length < 10) return
    await abrirChamado(assunto, motivoMap[problema as MotivoUI], descricao)
    emitChanged()
  }

  const handleEncerrar = async () => {
    await encerrarTicket()
    emitChanged()
  }

  return (
    <>
      {!hideFab && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-4 right-6 z-50 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold rounded-full w-14 h-14 flex items-center justify-center shadow-lg transition-all hover:scale-105"
          aria-label="Abrir suporte"
        >
          <ChatBubbleLeftRightIcon className="w-7 h-7" />
        </button>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md p-0 overflow-hidden rounded-xl">
          {!ticketAberto ? (
            <div className="p-6 space-y-4">
              <DialogHeader>
                <DialogTitle className="text-lg font-semibold text-gray-800 text-center">
                  Abrir Ticket de Suporte
                </DialogTitle>
              </DialogHeader>

              {contractError ? <ContractState error={contractError} compact /> : null}

              <Input
                placeholder="Assunto"
                value={assunto}
                onChange={(event) => setAssunto(event.target.value)}
                maxLength={160}
              />

              <Select value={problema} onValueChange={(v: MotivoUI) => setProblema(v)}>
                <SelectTrigger className="mt-1 w-full py-5 bg-gray-200 border-gray-500/40">
                  <SelectValue placeholder="Tipo de problema" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="erro">Erro no sistema</SelectItem>
                  <SelectItem value="pagamento">Pagamento</SelectItem>
                  <SelectItem value="conta">Acesso / Conta</SelectItem>
                  <SelectItem value="outros">Outros</SelectItem>
                </SelectContent>
              </Select>

              <Textarea
                placeholder="Descreva brevemente o problema..."
                value={descricao}
                onChange={(e) => setDescricao(e.target.value)}
                rows={4}
              />

              <Button
                onClick={handleAbrirChamado}
                disabled={processando || !problema || assunto.trim().length < 6 || descricao.trim().length < 10}
                className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-5 disabled:opacity-60"
              >
                <PaperAirplaneIcon className="w-5 h-5 -rotate-45 mr-2" />
                Abrir Chamado
              </Button>
            </div>
          ) : (
            <div className="flex flex-col h-[450px]">
              <div className="border-b px-4 py-3">
                <DialogTitle className="font-semibold text-gray-800">Chat com Suporte</DialogTitle>
              </div>

              <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50">
                {mensagens.map((msg) => (
                  <div
                    key={msg.id}
                    className={`flex ${msg.remetente === 'usuario' ? 'justify-end' : 'justify-start'}`}
                  >
                    <div
                      className={`max-w-[75%] px-3 py-2 rounded-lg text-sm ${
                        msg.remetente === 'usuario' ? 'bg-[#FC1EAD]/20' : 'bg-white border'
                      }`}
                    >
                      <p className="whitespace-pre-wrap break-words">{msg.texto}</p>
                      <span className="block text-[10px] text-gray-400 mt-1">{msg.data}</span>
                    </div>
                  </div>
                ))}
                <div ref={endOfMessagesRef} />
              </div>

              <div className="border-t p-3 bg-white space-y-2">
                <Textarea
                  placeholder="Digite sua mensagem..."
                  value={novaMensagem}
                  onChange={(e) => setNovaMensagem(e.target.value)}
                  rows={2}
                />

                <Button
                  onClick={async () => {
                    await enviarMensagem()
                    setTimeout(() => endOfMessagesRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
                    emitChanged()
                  }}
                  disabled={processando || !novaMensagem.trim()}
                  className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-5"
                >
                  <PaperAirplaneIcon className="w-5 h-5 -rotate-45 mr-2" />
                  Enviar
                </Button>

                <Button
                  onClick={handleEncerrar}
                  disabled={processando}
                  variant="outline"
                  className="w-full border-red-400 text-red-500 hover:bg-red-50 font-semibold py-5"
                >
                  Encerrar Ticket
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
