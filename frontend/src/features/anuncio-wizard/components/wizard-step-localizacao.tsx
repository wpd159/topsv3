'use client'

import { MapPin } from 'lucide-react'
import { Input } from '@/components/ui/input'
import type { SearchableSelectOption } from '../wizard-constants'
import { Field, StepPanel } from './wizard-ui'
import { SearchableSelect } from './searchable-select'

export function WizardStepLocalizacao({
  estadoId,
  cidadeId,
  cidadeNome,
  bairroId,
  bairroNome,
  pontoReferenciaTexto,
  selectedStateLabel,
  stateOptions,
  cidadeOptions,
  bairroOptions,
  loadingEstados,
  loadingCidades,
  loadingBairros,
  onEstado,
  onCidade,
  onBairro,
  onReferencia,
}: {
  estadoId: string
  cidadeId: string
  cidadeNome: string
  bairroId: string
  bairroNome: string
  pontoReferenciaTexto: string
  selectedStateLabel: string
  stateOptions: SearchableSelectOption[]
  cidadeOptions: SearchableSelectOption[]
  bairroOptions: SearchableSelectOption[]
  loadingEstados: boolean
  loadingCidades: boolean
  loadingBairros: boolean
  onEstado: (value: string) => void
  onCidade: (value: string) => void
  onBairro: (value: string) => void
  onReferencia: (value: string) => void
}) {
  return (
    <StepPanel>
      <div className="rounded-[28px] border border-zinc-200 bg-[linear-gradient(180deg,#ffffff_0%,#fbfbfb_100%)] p-5 shadow-sm sm:p-6">
        <div className="mb-5 flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
            <MapPin className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-zinc-950">Área de atendimento</h3>
            <p className="mt-1 max-w-xl text-sm leading-6 text-zinc-600">
              Cidade, bairro e referência conhecida ficam juntos em um bloco mais leve, com busca
              parcial para reduzir fricção no mobile.
            </p>
          </div>
        </div>

        <div className="grid gap-4">
          <Field label="Estado">
            <SearchableSelect
              value={estadoId}
              label={selectedStateLabel}
              placeholder="Selecione o estado"
              searchPlaceholder="Busque por estado ou UF"
              emptyText={loadingEstados ? 'Carregando estados...' : 'Nenhum estado encontrado.'}
              options={stateOptions}
              disabled={loadingEstados}
              onSelect={onEstado}
            />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Cidade">
              <SearchableSelect
                value={cidadeId}
                label={cidadeNome || 'Selecione a cidade'}
                placeholder={estadoId ? 'Selecione a cidade' : 'Escolha o estado antes'}
                searchPlaceholder="Digite parte do nome da cidade"
                emptyText={
                  !estadoId
                    ? 'Escolha o estado para liberar as cidades.'
                    : loadingCidades
                      ? 'Carregando cidades...'
                      : 'Nenhuma cidade encontrada.'
                }
                options={cidadeOptions}
                disabled={!estadoId || loadingCidades}
                onSelect={onCidade}
              />
            </Field>

            <Field label="Bairro">
              <SearchableSelect
                value={bairroId}
                label={bairroNome || 'Selecione o bairro'}
                placeholder={cidadeId ? 'Selecione o bairro' : 'Escolha a cidade antes'}
                searchPlaceholder="Ex: tremen, campinas, jardim"
                emptyText={
                  !cidadeId
                    ? 'Escolha a cidade para liberar os bairros.'
                    : loadingBairros
                      ? 'Carregando bairros...'
                      : 'Nenhum bairro encontrado.'
                }
                options={bairroOptions}
                disabled={!cidadeId || loadingBairros}
                onSelect={onBairro}
              />
            </Field>
          </div>

          <Field label="Referência conhecida">
            <Input
              value={pontoReferenciaTexto}
              onChange={(event) => onReferencia(event.target.value)}
              placeholder="Ex: Próx ao Flamboyant"
              maxLength={120}
              className="h-12 rounded-xl border-zinc-200 text-base"
            />
          </Field>
        </div>

        <div className="mt-4 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-600">
          Ajude clientes próximos a encontrarem sua região sem mostrar endereço exato.
        </div>
      </div>
    </StepPanel>
  )
}
