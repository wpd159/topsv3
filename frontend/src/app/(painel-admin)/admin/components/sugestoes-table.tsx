'use client'

import { Button } from '@/components/ui/button'
import type { AdminSugestaoResumo } from '@/lib/admin-sugestao-api'

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function SugestoesTable({
  itens,
  onOpen,
}: {
  itens: AdminSugestaoResumo[]
  onOpen: (id: string) => void
}) {
  return (
    <div className="divide-y border-y">
      {itens.map((sugestao) => (
        <article
          key={sugestao.id}
          className="grid gap-3 px-1 py-4 sm:px-3 lg:grid-cols-[minmax(0,1.3fr)_140px_150px_180px_auto] lg:items-center"
        >
          <div className="min-w-0">
            <p className="truncate font-semibold text-zinc-950">{sugestao.titulo}</p>
            <p className="mt-1 text-xs text-zinc-400">{sugestao.protocolo}</p>
            <p className="truncate text-xs text-zinc-500">
              {sugestao.usuarioNome}
              {sugestao.usuarioEmail ? ` - ${sugestao.usuarioEmail}` : ''}
            </p>
          </div>
          <p className="text-sm text-zinc-700">{sugestao.tipoRotulo}</p>
          <div>
            <span
              className={`inline-flex rounded-md border px-2 py-1 text-xs font-medium ${
                sugestao.status === 'PENDENTE'
                  ? 'border-amber-200 bg-amber-50 text-amber-800'
                  : sugestao.status === 'EM_ANALISE'
                    ? 'border-blue-200 bg-blue-50 text-blue-800'
                    : sugestao.status === 'RESOLVIDO'
                      ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                      : 'border-zinc-200 bg-zinc-100 text-zinc-700'
              }`}
            >
              {sugestao.statusRotulo}
            </span>
          </div>
          <p className="text-sm text-zinc-600">{dataHora(sugestao.criadoEm)}</p>
          <div className="lg:text-right">
            <Button type="button" size="sm" onClick={() => onOpen(sugestao.id)}>
              Abrir detalhe
            </Button>
          </div>
        </article>
      ))}
    </div>
  )
}
