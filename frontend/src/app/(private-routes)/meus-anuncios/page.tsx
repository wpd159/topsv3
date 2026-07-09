'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { MeuAnuncioCard } from '@/components/anuncios/meu-anuncio-card'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { StoryCreateDialog } from '@/components/stories/story-create-dialog'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

type FeatureCatalogoItem = {
  codigo: string
  nome: string
  descricao?: string | null
  custoCreditos: number
  duracaoHoras?: number | null
  escopo?: 'ANUNCIO' | 'USUARIO'
  ativo?: boolean
}

type Anuncio = {
  id: number
  titulo: string
  fotoCapa: string | null
  localizacao: string
  slug: string | null
  valor: string
  status: string
  impulsionado?: boolean
  dataFimImpulsionamento?: string
  featuresAtivas?: { codigo: string; nome?: string | null; expiraEm?: unknown }[]
  pendingRevision?: boolean
  pendingRevisionId?: number | null
  pendingRevisionStatus?: string | null
}

export default function MeusAnunciosPage() {
  const [anuncios, setAnuncios] = useState<Anuncio[]>([])
  const [catalogo, setCatalogo] = useState<FeatureCatalogoItem[]>([])
  const [loading, setLoading] = useState(true)
  const [storyDialogOpen, setStoryDialogOpen] = useState(false)
  const [storyAnuncioId, setStoryAnuncioId] = useState<number | null>(null)

  const API = process.env.NEXT_PUBLIC_API_URL
  const router = useRouter()

  const carregarCatalogo = async () => {
    if (!API) return
    try {
      const res = await fetch(`${API}/features/catalogo`, { credentials: 'include' })
      if (!res.ok) return
      const data = corrigirEstruturaTexto(await res.json())
      if (Array.isArray(data)) setCatalogo(data)
    } catch {
      // noop
    }
  }

  const carregarAnuncios = async () => {
    try {
      if (!API) throw new Error('API não definida')

      const res = await fetch(`${API}/anuncios/meus`, {
        credentials: 'include',
        cache: 'no-store',
      })

      if (!res.ok) throw new Error('Erro ao carregar anúncios')
      const data = corrigirEstruturaTexto((await res.json()) as Anuncio[])

      setAnuncios((Array.isArray(data) ? data : []).filter((a) => a.slug && a.slug.trim().length > 0))
    } catch {
      setAnuncios([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregarCatalogo()
    carregarAnuncios()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const storyDisponivelNoCatalogo = useMemo(
    () => catalogo.some((c) => c.codigo === 'STORIES' && (c.ativo ?? true)),
    [catalogo]
  )

  const storyFeature = useMemo(
    () => catalogo.find((c) => c.codigo === 'STORIES' && (c.ativo ?? true)),
    [catalogo]
  )

  const temAnuncioAtivo = useMemo(() => anuncios.some((a) => a.status === 'ATIVO'), [anuncios])

  const resumo = useMemo(() => {
    const ativos = anuncios.filter((a) => a.status === 'ATIVO').length
    const pendentes = anuncios.filter((a) => a.status === 'PENDENTE').length
    const pausados = anuncios.filter((a) => a.status === 'PAUSADO').length
    const premium = anuncios.filter((a) => (a.featuresAtivas?.length ?? 0) > 0 || a.impulsionado).length

    return { ativos, pendentes, pausados, premium }
  }, [anuncios])

  async function publicarStory(file: File, _dias: number, anuncioId: number) {
    if (!API) {
      toast.error('API não configurada.')
      return false
    }
    if (anuncioId == null || anuncioId === undefined || Number.isNaN(Number(anuncioId)) || anuncioId <= 0) {
      toast.error('Selecione um anúncio ativo')
      return false
    }
    try {
      const fd = new FormData()
      fd.append('codigo', 'STORIES')
      fd.append('anuncioId', String(anuncioId))
      fd.append('midia', file)

      console.log('[story] POST /stories — FormData', {
        codigo: 'STORIES',
        anuncioId,
        midia: { name: file.name, type: file.type, size: file.size },
      })

      const res = await fetch(`${API}/stories`, {
        method: 'POST',
        credentials: 'include',
        body: fd,
      })

      const data = await res.json().catch(() => null)
      if (!res.ok) {
        const msg =
          data?.message ||
          data?.error ||
          (res.status === 400 ? 'Não foi possível publicar o story. Verifique créditos e se o anúncio está ativo.' : 'Falha ao publicar story.')
        throw new Error(typeof msg === 'string' ? msg : 'Falha ao publicar story.')
      }

      toast.success('Story publicado com sucesso.')
      await carregarAnuncios()
      return true
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Erro ao publicar story.')
      return false
    }
  }

  return (
    <PainelShell
      title="Meus anúncios"
      description="Gerencie sua vitrine, abra o fluxo de impulsionamento e acompanhe rapidamente quais anúncios já estão prontos para receber upgrades."
    >
      <div className="space-y-6">
        <section className="rounded-[32px] border border-slate-200 bg-[linear-gradient(135deg,#fff5fb_0%,#ffffff_60%,#f8fbff_100%)] p-6 shadow-sm md:p-7">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className="text-2xl font-extrabold tracking-tight text-slate-900">
                Sua vitrine comercial em um só lugar
              </h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                Edite anúncios, abra impulsionamento e identifique rapidamente quais perfis já estão ativos, premium ou aguardando moderação.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Link
                href="/anunciar/wizard"
                className="inline-flex items-center justify-center rounded-2xl bg-[#FC1EAD] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[#df1698]"
              >
                Publicar novo anúncio
              </Link>
              <Link
                href="/painel"
                className="inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition hover:border-slate-300 hover:bg-slate-50"
              >
                Voltar ao painel
              </Link>
            </div>
          </div>

          {!loading ? (
            <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {[
                { label: 'Ativos', value: resumo.ativos },
                { label: 'Pendentes', value: resumo.pendentes },
                { label: 'Pausados', value: resumo.pausados },
                { label: 'Com premium', value: resumo.premium },
              ].map((item) => (
                <div key={item.label} className="rounded-2xl border border-white/80 bg-white/90 px-4 py-4 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{item.label}</p>
                  <p className="mt-2 text-3xl font-extrabold tracking-tight text-slate-900">{item.value}</p>
                </div>
              ))}
            </div>
          ) : null}
        </section>

        {loading ? (
          <div className="flex min-h-[240px] items-center justify-center rounded-[32px] border border-slate-200 bg-white text-slate-500 shadow-sm">
            Carregando seus anúncios...
          </div>
        ) : anuncios.length === 0 ? (
          <div className="flex min-h-[260px] flex-col items-center justify-center rounded-[32px] border border-dashed border-slate-300 bg-white px-6 text-center shadow-sm">
            <p className="text-lg font-semibold text-slate-800">Você ainda não possui anúncios.</p>
            <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
              Publique seu primeiro anúncio para começar a usar o painel comercial, acompanhar score de visibilidade e ativar upgrades pagos.
            </p>
            <Link href="/anunciar/wizard" className="mt-5">
              <Button className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-medium">
                Publicar meu primeiro anúncio
              </Button>
            </Link>
          </div>
        ) : (
          <>
            {!loading && anuncios.length > 0 && !temAnuncioAtivo ? (
              <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                Você precisa de um anúncio ativo para publicar um story
              </div>
            ) : null}
            {!loading && temAnuncioAtivo && !storyDisponivelNoCatalogo ? (
              <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
                O recurso de stories não está disponível no catálogo no momento.
              </div>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
              {anuncios.map((a) => (
                <MeuAnuncioCard
                  key={a.id}
                  id={a.id}
                  slug={a.slug!}
                  nome={a.titulo}
                  cidade={a.localizacao}
                  valor={a.valor}
                  imagens={a.fotoCapa ? [a.fotoCapa] : []}
                  status={
                    a.status === 'ATIVO'
                      ? 'postado'
                      : a.status === 'PENDENTE'
                        ? 'pendente'
                        : 'pausado'
                  }
                  impulsionado={a.impulsionado ?? false}
                  dataFimImpulsionamento={a.dataFimImpulsionamento}
                  onAtualizar={carregarAnuncios}
                  featuresAtivas={a.featuresAtivas ?? []}
                  catalogoFeatures={catalogo}
                  pendingRevision={a.pendingRevision ?? false}
                  podeAdicionarStory={a.status === 'ATIVO' && storyDisponivelNoCatalogo}
                  onAdicionarStory={() => {
                    setStoryAnuncioId(a.id)
                    setStoryDialogOpen(true)
                  }}
                />
              ))}
            </div>
          </>
        )}
      </div>

      {storyAnuncioId != null ? (
        <StoryCreateDialog
          open={storyDialogOpen}
          onOpenChange={(o) => {
            setStoryDialogOpen(o)
            if (!o) setStoryAnuncioId(null)
          }}
          anuncioId={storyAnuncioId}
          custoCreditos={storyFeature?.custoCreditos ?? 0}
          duracaoHoras={storyFeature?.duracaoHoras ?? 24}
          onPublish={publicarStory}
          onBuyCredits={() => router.push('/creditos')}
        />
      ) : null}
    </PainelShell>
  )
}
