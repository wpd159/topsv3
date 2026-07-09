'use client'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AutoResizeTextarea, Field, StepPanel } from './wizard-ui'

export function WizardStepPerfil({
  titulo,
  categoria,
  descricaoPerfil,
  categorias,
  descricaoPerfilCount,
  descricaoPerfilNeedsMore,
  descricaoPerfilRemaining,
  onTituloChange,
  onCategoriaChange,
  onDescricaoChange,
}: {
  titulo: string
  categoria: string
  descricaoPerfil: string
  categorias: Array<{ value: string; label: string }>
  descricaoPerfilCount: number
  descricaoPerfilNeedsMore: boolean
  descricaoPerfilRemaining: number
  onTituloChange: (value: string) => void
  onCategoriaChange: (value: string) => void
  onDescricaoChange: (value: string) => void
}) {
  return (
    <StepPanel>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="space-y-5 rounded-[26px] border border-zinc-200 bg-[linear-gradient(180deg,#ffffff_0%,#fff8fb_100%)] p-5 shadow-sm">
          <Field label="Nome do anúncio">
            <Input
              value={titulo}
              onChange={(event) => onTituloChange(event.target.value)}
              placeholder="Ex: Alice Loira"
              className="h-12 rounded-xl border-zinc-200 text-base"
            />
          </Field>

          <Field label="Categoria">
            <select
              className="h-12 w-full rounded-xl border border-zinc-200 bg-white px-4 py-2 text-base ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-900 focus-visible:ring-offset-2"
              value={categoria}
              onChange={(event) => onCategoriaChange(event.target.value)}
            >
              <option value="">Selecione</option>
              {categorias.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </Field>

          <div className="space-y-3 rounded-[22px] border border-zinc-200 bg-white p-4 shadow-sm">
            <div className="space-y-1">
              <Label className="text-sm font-semibold text-zinc-800">
                Conte um pouco sobre você
              </Label>
              <p className="text-sm leading-6 text-zinc-600">
                Perfis com descrição personalizada costumam receber mais contatos.
              </p>
            </div>

            <AutoResizeTextarea
              value={descricaoPerfil}
              onChange={(event) => onDescricaoChange(event.target.value)}
              placeholder="Descreva seu estilo, atendimento, diferenciais e a experiência que você deseja transmitir."
              minRows={5}
              maxRows={5}
              maxLength={500}
            />

            <div className="flex items-center justify-between gap-3 text-xs">
              <span className={descricaoPerfilNeedsMore ? 'text-amber-700' : 'text-zinc-500'}>
                {descricaoPerfilCount === 0
                  ? 'Escreva com calma. Esse texto será salvo no mesmo perfil usado em Configurações.'
                  : descricaoPerfilNeedsMore
                    ? `Mais ${descricaoPerfilRemaining} caracteres deixam a descrição mais convincente.`
                    : 'Descrição pronta para acompanhar o anúncio.'}
              </span>
              <span className="shrink-0 text-zinc-400">{descricaoPerfilCount}/500</span>
            </div>
          </div>
        </div>

        <div className="rounded-[26px] border border-zinc-200 bg-zinc-50/90 p-5 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-zinc-500">
            Primeira impressão
          </p>
          <h3 className="mt-3 text-lg font-semibold text-zinc-950">
            Seu perfil começa a ganhar forma aqui.
          </h3>
          <div className="mt-4 space-y-3 text-sm leading-6 text-zinc-600">
            <div className="rounded-2xl border border-white/80 bg-white px-4 py-3 shadow-sm">
              Um nome claro, uma categoria correta e uma descrição autêntica deixam o anúncio
              mais memorável.
            </div>
            <div className="rounded-2xl border border-dashed border-zinc-200 bg-white/70 px-4 py-3">
              O preview completo já acompanha você ao lado no desktop e fica recolhido no mobile
              para não roubar foco nesta etapa.
            </div>
          </div>
        </div>
      </div>
    </StepPanel>
  )
}
