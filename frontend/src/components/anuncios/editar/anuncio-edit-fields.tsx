'use client'

import * as React from 'react'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { SelectItem } from '@/components/ui/select'
import { Row, LabeledInput, LabeledSelect } from './form-ui'
import { CATEGORIAS, HORARIOS, LOCAIS_ATENDIMENTO, SERVICOS } from './constants'
import { formatCurrencyBRL } from '@/utils/formatter'

export function AnuncioEditFields(p: {
  titulo: string
  setTitulo: (v: string) => void
  categoria: string
  setCategoria: (v: string) => void
  preco: string
  setPreco: (v: string) => void
  horario: string
  setHorario: (v: string) => void
  locaisAtendimento: string[]
  toggleLocalAtendimento: (v: string) => void
  servicos: string[]
  toggleServico: (v: string) => void
  linkConteudo: string
  setLinkConteudo: (v: string) => void
  descricao: string
  setDescricao: (v: string) => void
}) {
  return (
    <>
      <Row>
        <LabeledInput
          id="titulo"
          label="Título do anúncio"
          value={p.titulo}
          onChange={p.setTitulo}
          placeholder="Ex: Meu anúncio"
          required
        />

        <LabeledSelect
          label="Categoria"
          value={p.categoria}
          onChange={p.setCategoria}
          placeholder="Selecione uma categoria"
        >
          {CATEGORIAS.map((c) => (
            <SelectItem key={c.value} value={c.value}>
              {c.label}
            </SelectItem>
          ))}
        </LabeledSelect>
      </Row>

      <Row>
        <div className="flex flex-col gap-2">
          <Label htmlFor="preco">Preço</Label>
          <Input
            id="preco"
            type="text"
            inputMode="decimal"
            value={p.preco}
            onChange={(e) => {
              const raw = e.target.value.replace(/\D/g, '')
              if (!raw) {
                p.setPreco('')
                return
              }
              const numero = Number(raw) / 100
              p.setPreco(formatCurrencyBRL(numero))
            }}
            placeholder="R$ 0,00"
            className="py-5"
            required
          />
        </div>

        <LabeledSelect
          label="Horário"
          value={p.horario}
          onChange={p.setHorario}
          placeholder="Selecione o horário"
        >
          {HORARIOS.map((h) => (
            <SelectItem key={h.value} value={h.value}>
              {h.label}
            </SelectItem>
          ))}
        </LabeledSelect>
      </Row>

      <div className="flex flex-col gap-3">
        <Label>Locais de Atendimento</Label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {LOCAIS_ATENDIMENTO.map((opt) => (
            <label key={opt.value} className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                value={opt.value}
                onChange={() => p.toggleLocalAtendimento(opt.value)}
                checked={p.locaisAtendimento.includes(opt.value)}
                className="accent-pink-500 cursor-pointer"
              />
              {opt.label}
            </label>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <Label>Serviços</Label>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {SERVICOS.map((opt) => (
            <label key={opt.value} className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                value={opt.value}
                checked={p.servicos.includes(opt.value)}
                onChange={() => p.toggleServico(opt.value)}
                className="accent-pink-500 cursor-pointer"
              />
              {opt.label}
            </label>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="link">Venda de conteúdo (Link)</Label>
        <Input
          id="link"
          value={p.linkConteudo}
          onChange={(e) => p.setLinkConteudo(e.target.value)}
          placeholder="https://..."
          className="py-5"
        />
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="descricao">Descrição</Label>
        <textarea
          id="descricao"
          value={p.descricao}
          onChange={(e) => p.setDescricao(e.target.value)}
          placeholder="Descreva..."
          rows={6}
          className="py-3 h-[300px] w-full rounded-md border bg-transparent px-3 text-sm"
          required
        />
      </div>
    </>
  )
}