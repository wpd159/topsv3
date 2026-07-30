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
  categoriasLoading,
  categoriasError,
  showProfileDescription = true,
  onTituloChange,
  onCategoriaChange,
  onDescricaoChange,
  onReloadCategorias,
}: {
  titulo: string
  categoria: string
  descricaoPerfil: string
  categorias: Array<{ value: string; label: string }>
  descricaoPerfilCount: number
  descricaoPerfilNeedsMore: boolean
  descricaoPerfilRemaining: number
  categoriasLoading: boolean
  categoriasError: string | null
  showProfileDescription?: boolean
  onTituloChange: (value: string) => void
  onCategoriaChange: (value: string) => void
  onDescricaoChange: (value: string) => void
  onReloadCategorias: () => void
}) {
  return (
    <StepPanel>
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
              disabled={categoriasLoading || Boolean(categoriasError)}
              onChange={(event) => onCategoriaChange(event.target.value)}
            >
              <option value="">{categoriasLoading ? 'Carregando categorias...' : 'Selecione'}</option>
              {categorias.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
            {categoriasError ? (
              <span className="flex flex-wrap items-center gap-2 text-xs text-red-700">
                {categoriasError}
                <button
                  type="button"
                  className="font-semibold underline underline-offset-2"
                  onClick={onReloadCategorias}
                >
                  Tentar novamente
                </button>
              </span>
            ) : null}
          </Field>

          {showProfileDescription ? <div className="space-y-3 rounded-[22px] border border-zinc-200 bg-white p-4 shadow-sm">
            <div className="space-y-1">
              <Label className="text-sm font-semibold text-zinc-800">
                Conte um pouco sobre você
              </Label>
              <p className="text-sm leading-6 text-zinc-600">
                Uma boa descrição ajuda clientes a conhecerem seu estilo e entrarem em contato com mais confiança.
              </p>
            </div>

            <AutoResizeTextarea
              value={descricaoPerfil}
              onChange={(event) => onDescricaoChange(event.target.value)}
              placeholder="Fale sobre seu estilo, atendimento, diferenciais e a experiência que você oferece."
              minRows={5}
              maxRows={5}
              maxLength={500}
            />

            <div className="flex items-center justify-between gap-3 text-xs">
              <span className={descricaoPerfilNeedsMore ? 'text-amber-700' : 'text-zinc-500'}>
                {descricaoPerfilCount === 0
                  ? 'Essa descrição será exibida no seu perfil. Seja clara, autêntica e destaque o que torna seu atendimento especial.'
                  : descricaoPerfilNeedsMore
                    ? `Mais ${descricaoPerfilRemaining} caracteres deixam a descrição mais convincente.`
                    : 'Essa descrição será exibida no seu perfil. Seja clara, autêntica e destaque o que torna seu atendimento especial.'}
              </span>
              <span className="shrink-0 text-zinc-400">{descricaoPerfilCount}/500</span>
            </div>
          </div> : null}
      </div>
    </StepPanel>
  )
}
