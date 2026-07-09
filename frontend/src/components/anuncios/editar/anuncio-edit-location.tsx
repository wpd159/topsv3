'use client'

import * as React from 'react'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Combo } from '@/components/ui/combo'
import { Row } from './form-ui'
import type { EstadoItem, CidadeItem, BairroItem } from './types'

export function AnuncioEditLocation(p: {
  localizacaoLabel: string

  estados: EstadoItem[]
  cidades: CidadeItem[]
  bairros: BairroItem[]
  loadingEstados: boolean
  loadingCidades: boolean
  loadingBairros: boolean

  estadoId: string
  cidadeId: string
  bairroId: string
  pontoReferenciaTexto: string

  onSelectEstado: (id: string) => void
  onSelectCidade: (id: string) => void
  onSelectBairro: (id: string) => void
  setPontoReferenciaTexto: (value: string) => void
}) {
  const [openEstado, setOpenEstado] = React.useState(false)
  const [openCidade, setOpenCidade] = React.useState(false)
  const [openBairro, setOpenBairro] = React.useState(false)

  const selectedEstado = p.estados.find((e) => String(e.id) === p.estadoId) ?? null
  const selectedCidade = p.cidades.find((c) => String(c.id) === p.cidadeId) ?? null
  const selectedBairro = p.bairros.find((b) => String(b.id) === p.bairroId) ?? null

  const labelEstado = p.loadingEstados
    ? 'Carregando estados...'
    : selectedEstado
      ? `${selectedEstado.nome}${selectedEstado.uf ? ` - ${selectedEstado.uf}` : ''}`
      : 'Selecione o estado'

  const labelCidade = !p.estadoId
    ? 'Selecione um estado primeiro'
    : p.loadingCidades
      ? 'Carregando cidades...'
      : selectedCidade
        ? selectedCidade.nome
        : 'Selecione a cidade'

  const labelBairro = !p.cidadeId
    ? 'Selecione uma cidade primeiro'
    : p.loadingBairros
      ? 'Carregando bairros...'
      : selectedBairro
        ? selectedBairro.nome
        : 'Selecione o bairro'

  return (
    <div className="space-y-4">
      {p.localizacaoLabel ? (
        <div className="text-xs text-gray-500">
          Atual: <span className="font-medium">{p.localizacaoLabel}</span>
        </div>
      ) : null}

      <Row>
        <div className="flex flex-col gap-2">
          <Label>Estado</Label>
          <Combo<EstadoItem>
            open={openEstado}
            onOpenChange={setOpenEstado}
            disabled={p.loadingEstados || !p.estados.length}
            label={labelEstado}
            items={p.estados.map((e) => ({ ...e, nome: `${e.nome}${e.uf ? ` - ${e.uf}` : ''}` }))}
            placeholder="Buscar estado..."
            emptyText={p.loadingEstados ? 'Carregando...' : 'Nenhum estado encontrado.'}
            isSelected={(x) => String(x.id) === p.estadoId}
            onSelect={(x) => {
              p.onSelectEstado(String(x.id))
              setOpenEstado(false)
            }}
          />
        </div>

        <div className="flex flex-col gap-2">
          <Label>Cidade</Label>
          <Combo<CidadeItem>
            open={openCidade}
            onOpenChange={setOpenCidade}
            disabled={!p.estadoId || p.loadingCidades}
            label={labelCidade}
            items={p.cidades}
            placeholder={!p.estadoId ? 'Selecione um estado' : 'Buscar cidade...'}
            emptyText={
              !p.estadoId
                ? 'Selecione o estado primeiro.'
                : p.loadingCidades
                  ? 'Carregando...'
                  : 'Nenhuma cidade encontrada.'
            }
            isSelected={(x) => String(x.id) === p.cidadeId}
            onSelect={(x) => {
              p.onSelectCidade(String(x.id))
              setOpenCidade(false)
            }}
          />
        </div>
      </Row>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <div className="flex flex-col gap-2">
          <Label>Bairro</Label>
          <Combo<BairroItem>
            open={openBairro}
            onOpenChange={setOpenBairro}
            disabled={!p.cidadeId || p.loadingBairros}
            label={labelBairro}
            items={p.bairros}
            placeholder={!p.cidadeId ? 'Selecione uma cidade' : 'Buscar bairro...'}
            emptyText={
              !p.cidadeId
                ? 'Selecione a cidade primeiro.'
                : p.loadingBairros
                  ? 'Carregando...'
                  : 'Nenhum bairro encontrado.'
            }
            isSelected={(x) => String(x.id) === p.bairroId}
            onSelect={(x) => {
              p.onSelectBairro(String(x.id))
              setOpenBairro(false)
            }}
          />
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor="pontoReferenciaTexto">Ponto de referência</Label>
          <Input
            id="pontoReferenciaTexto"
            value={p.pontoReferenciaTexto}
            maxLength={120}
            onChange={(event) => p.setPontoReferenciaTexto(event.target.value)}
            placeholder="Ex.: Próximo ao shopping, estádio ou avenida conhecida"
          />
          <p className="text-xs text-gray-500">
            Ajuda usuários a encontrarem sua região sem mostrar endereço exato.
          </p>
        </div>
      </div>
    </div>
  )
}
