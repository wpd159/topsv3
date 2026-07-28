'use client'

import { useState } from 'react'
import { FunnelIcon } from '@heroicons/react/24/outline'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { AdminRelatorioReceitaFiltros } from '@/lib/admin-pagamentos-api'

type Props = {
  filtros: AdminRelatorioReceitaFiltros
  onChange: (updates: Record<string, string | number | null>) => void
}

export default function FinanceiroFiltro({ filtros, onChange }: Props) {
  const [usuario, setUsuario] = useState(filtros.usuario ?? '')
  const [produto, setProduto] = useState(filtros.produto ?? '')

  function aplicar(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onChange({ usuario: usuario.trim(), produto: produto.trim(), page: null })
  }

  return (
    <form onSubmit={aplicar} className="grid gap-3 border-y border-zinc-200 py-4 lg:grid-cols-4 xl:grid-cols-[10rem_11rem_10rem_minmax(12rem,1fr)_minmax(12rem,1fr)_11rem_8rem_auto] xl:items-end">
      <Filtro label="Período" value={filtros.periodo} onChange={(value) => onChange({ periodo: value, inicio: null, fim: null, page: null })} options={[
        ['HOJE', 'Hoje'], ['7_DIAS', '7 dias'], ['30_DIAS', '30 dias'], ['PERSONALIZADO', 'Personalizado'],
      ]} />
      {filtros.periodo === 'PERSONALIZADO' ? (
        <>
          <CampoData label="Início" value={filtros.inicio ?? ''} onChange={(value) => onChange({ inicio: value, page: null })} />
          <CampoData label="Fim" value={filtros.fim ?? ''} onChange={(value) => onChange({ fim: value, page: null })} />
        </>
      ) : null}
      <Filtro label="Status" value={filtros.status} onChange={(value) => onChange({ status: value, page: null })} options={[
        ['TODOS', 'Todos'], ['CONFIRMADO', 'Confirmados'], ['PENDENTE', 'Pendentes'], ['FALHO', 'Falhos'], ['CANCELADO', 'Cancelados'], ['EXPIRADO', 'Expirados'], ['ESTORNADO', 'Estornados'], ['LEGADO', 'Legados'],
      ]} />
      <Filtro label="Método" value={filtros.metodo} onChange={(value) => onChange({ metodo: value, page: null })} options={[
        ['TODOS', 'Todos'], ['PIX', 'Pix'], ['LEGADO', 'Legado'], ['DESCONHECIDO', 'Desconhecido'],
      ]} />
      <label>
        <span className="mb-1 block text-xs font-semibold text-zinc-600">Usuário</span>
        <Input value={usuario} onChange={(event) => setUsuario(event.target.value)} placeholder="Nome ou ID" />
      </label>
      <label>
        <span className="mb-1 block text-xs font-semibold text-zinc-600">Pacote</span>
        <Input value={produto} onChange={(event) => setProduto(event.target.value)} placeholder="Nome, código ou ID" />
      </label>
      <Filtro label="Ordenação" value={filtros.ordenacao} onChange={(value) => onChange({ ordenacao: value, page: null })} options={[
        ['MAIS_RECENTES', 'Mais recentes'], ['MAIS_ANTIGOS', 'Mais antigos'], ['MAIOR_VALOR', 'Maior valor'], ['MENOR_VALOR', 'Menor valor'],
      ]} />
      <Filtro label="Por página" value={String(filtros.size)} onChange={(value) => onChange({ size: value, page: null })} options={[
        ['20', '20'], ['30', '30'], ['50', '50'], ['100', '100'],
      ]} />
      <Button type="submit" className="gap-2"><FunnelIcon className="h-4 w-4" />Aplicar</Button>
    </form>
  )
}

function Filtro({ label, value, onChange, options }: {
  label: string
  value: string
  onChange: (value: string) => void
  options: Array<[string, string]>
}) {
  return (
    <label>
      <span className="mb-1 block text-xs font-semibold text-zinc-600">{label}</span>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
        <SelectContent>{options.map(([key, text]) => <SelectItem key={key} value={key}>{text}</SelectItem>)}</SelectContent>
      </Select>
    </label>
  )
}

function CampoData({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <label><span className="mb-1 block text-xs font-semibold text-zinc-600">{label}</span><Input type="date" value={value} onChange={(event) => onChange(event.target.value)} /></label>
}
