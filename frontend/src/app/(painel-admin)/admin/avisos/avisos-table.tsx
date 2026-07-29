'use client'

import { Archive, Eye, Pencil, Power, Send } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminAviso, AvisoPagina } from '@/lib/aviso-api'

type Props = {
  dados: AvisoPagina<AdminAviso>
  operando: string | null
  onEditar: (aviso: AdminAviso) => void
  onPrevia: (aviso: AdminAviso) => void
  onPublicar: (aviso: AdminAviso) => void
  onRetirar: (aviso: AdminAviso) => void
  onArquivar: (aviso: AdminAviso) => void
}

function dataHora(value?: string | null) {
  return value
    ? new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'America/Sao_Paulo',
      }).format(new Date(value))
    : 'Sem limite'
}

function badgeVariant(situacao: AdminAviso['situacao']) {
  if (situacao === 'VIGENTE') return 'default'
  if (situacao === 'AGENDADO') return 'outline'
  if (situacao === 'EXPIRADO' || situacao === 'ARQUIVADO') return 'secondary'
  return 'outline'
}

export default function GerenciarAvisosTable({
  dados,
  operando,
  onEditar,
  onPrevia,
  onPublicar,
  onRetirar,
  onArquivar,
}: Props) {
  return (
    <div className="overflow-x-auto rounded-lg border border-zinc-200 bg-white">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Titulo</TableHead>
            <TableHead>Criado por</TableHead>
            <TableHead>Local</TableHead>
            <TableHead>Vigencia</TableHead>
            <TableHead>Situacao</TableHead>
            <TableHead className="text-right">Acoes</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {dados.itens.map((aviso) => {
            const busy = operando === aviso.id
            return (
              <TableRow key={aviso.id}>
                <TableCell className="min-w-64 align-top">
                  <p className="font-semibold text-zinc-950">{aviso.titulo}</p>
                  <p className="mt-1 line-clamp-2 text-xs text-zinc-500">{aviso.descricao}</p>
                  <p className="mt-1 text-xs text-zinc-500">
                    {aviso.frequenciaExibicaoRotulo} - {aviso.permiteDispensar ? 'Dispensavel' : 'Fixo'}
                  </p>
                </TableCell>
                <TableCell className="whitespace-nowrap">{aviso.criadoPorNome}</TableCell>
                <TableCell className="whitespace-nowrap">{aviso.localExibicaoRotulo}</TableCell>
                <TableCell className="min-w-44 text-xs text-zinc-600">
                  <p>Inicio: {dataHora(aviso.ativoDe)}</p>
                  <p>Fim: {dataHora(aviso.ativoAte)}</p>
                </TableCell>
                <TableCell>
                  <Badge variant={badgeVariant(aviso.situacao)}>{aviso.situacao}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex min-w-max justify-end gap-1">
                    <Button size="icon" variant="ghost" title="Previa" onClick={() => onPrevia(aviso)}>
                      <Eye className="h-4 w-4" />
                    </Button>
                    <Button size="icon" variant="ghost" title="Editar" disabled={busy} onClick={() => onEditar(aviso)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    {aviso.status === 'RASCUNHO' ? (
                      <Button size="icon" variant="ghost" title="Publicar" disabled={busy} onClick={() => onPublicar(aviso)}>
                        <Send className="h-4 w-4" />
                      </Button>
                    ) : null}
                    {aviso.status === 'PUBLICADO' ? (
                      <Button size="icon" variant="ghost" title="Retirar de publicacao" disabled={busy} onClick={() => onRetirar(aviso)}>
                        <Power className="h-4 w-4" />
                      </Button>
                    ) : null}
                    {aviso.status !== 'ARQUIVADO' ? (
                      <Button size="icon" variant="ghost" title="Arquivar" disabled={busy} onClick={() => onArquivar(aviso)}>
                        <Archive className="h-4 w-4" />
                      </Button>
                    ) : null}
                  </div>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}
