'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  ArrowLeftIcon,
  EyeIcon,
  MapPinIcon,
  PencilIcon,
  PhoneIcon,
  PhotoIcon,
  ShieldCheckIcon,
  SparklesIcon,
} from '@heroicons/react/24/solid'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  MeuAnuncioAcoesCicloVida,
  type CicloVidaAcao,
} from '@/components/anuncios/meu-anuncio-acoes-ciclo-vida'
import {
  anuncioModeracao,
  anuncioPodeMonetizar,
  anuncioPreco,
  anuncioStatus,
  meuAnuncioUrlPublicaSegura,
} from '@/components/anuncios/meu-anuncio-card'
import {
  CATEGORIAS,
  LOCAIS_ATENDIMENTO,
  SERVICOS,
} from '@/components/anuncios/editar/constants'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import {
  buscarMeuAnuncio,
  MeusAnunciosApiError,
  type MeuAnuncio,
  type MeuAnuncioCicloVida,
  type MeuAnuncioMidia,
} from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'
import { formatarVisualizacoesCanonicas } from '@/lib/visualizacoes-canonicas'

type DetalheErro = {
  tipo: 'SESSAO_NECESSARIA' | 'ACESSO_NEGADO' | 'NAO_ENCONTRADO' | 'TECNICO'
  titulo: string
  mensagem: string
}

type GaleriaItem = {
  id: string
  url: string
  alt: string
}

export function meuAnuncioMidiaPublicaSegura(midia: MeuAnuncioMidia) {
  if (midia.tipo !== 'FOTO' || midia.restrita || midia.visibilidadeMidia === 'RESTRITA_18') {
    return false
  }
  return meuAnuncioUrlPublicaSegura(midia.urlPublica) !== null
}

function rotuloOpcao(
  valor: string,
  opcoes: readonly { label: string; value: string }[]
) {
  return opcoes.find((opcao) => opcao.value === valor)?.label ?? valor
}

function rotuloMidiaProtegida(midia: MeuAnuncioMidia) {
  const tipo = midia.tipo === 'VIDEO' ? 'Vídeo' : 'Foto'
  if (midia.restrita || midia.visibilidadeMidia === 'RESTRITA_18') {
    return `${tipo} com acesso protegido`
  }
  if (midia.status === 'PENDENTE') return `${tipo} aguardando moderação`
  if (midia.status === 'REJEITADA') return `${tipo} rejeitada pela moderação`
  return `${tipo} sem URL pública autorizada`
}

function criarGaleria(anuncio: MeuAnuncio): GaleriaItem[] {
  const itens: GaleriaItem[] = []
  const urls = new Set<string>()
  const capa = !anuncio.capa?.restrita
    ? meuAnuncioUrlPublicaSegura(anuncio.capa?.urlPublica)
    : null

  if (capa) {
    urls.add(capa)
    itens.push({ id: 'capa', url: capa, alt: `Capa de ${anuncio.titulo}` })
  }

  anuncio.midias.forEach((midia) => {
    if (!meuAnuncioMidiaPublicaSegura(midia)) return
    const url = meuAnuncioUrlPublicaSegura(midia.urlPublica)
    if (!url || urls.has(url)) return
    urls.add(url)
    itens.push({
      id: midia.id,
      url,
      alt: `Foto do anúncio ${anuncio.titulo}`,
    })
  })

  return itens
}

export function MeuAnuncioDetalheView({ slug }: { slug: string }) {
  const router = useRouter()
  const [anuncio, setAnuncio] = useState<MeuAnuncio | null>(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<DetalheErro | null>(null)
  const [imagemSelecionadaId, setImagemSelecionadaId] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    setAnuncio(null)
    try {
      setAnuncio(await buscarMeuAnuncio(slug))
    } catch (error) {
      if (error instanceof MeusAnunciosApiError && error.status === 401) {
        setErro({
          tipo: 'SESSAO_NECESSARIA',
          titulo: 'Sessão necessária',
          mensagem: 'Entre novamente para consultar este anúncio.',
        })
      } else if (error instanceof MeusAnunciosApiError && error.status === 403) {
        setErro({
          tipo: 'ACESSO_NEGADO',
          titulo: 'Acesso negado',
          mensagem: 'Você não tem permissão para acessar este anúncio.',
        })
      } else if (error instanceof MeusAnunciosApiError && error.status === 404) {
        setErro({
          tipo: 'NAO_ENCONTRADO',
          titulo: 'Anúncio não encontrado',
          mensagem: 'O anúncio solicitado não existe ou não está mais disponível.',
        })
      } else {
        setErro({
          tipo: 'TECNICO',
          titulo: 'Não foi possível carregar o anúncio',
          mensagem: 'Não foi possível concluir a consulta. Tente novamente em instantes.',
        })
      }
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    void carregar()
  }, [carregar])

  useEffect(() => {
    setImagemSelecionadaId(null)
  }, [anuncio?.id])

  const galeria = useMemo(() => (anuncio ? criarGaleria(anuncio) : []), [anuncio])
  const midiasProtegidas = useMemo(
    () => anuncio?.midias.filter((midia) => !meuAnuncioMidiaPublicaSegura(midia)) ?? [],
    [anuncio]
  )
  const estadosProtegidos = useMemo(
    () => Array.from(new Set(midiasProtegidas.map(rotuloMidiaProtegida))),
    [midiasProtegidas]
  )
  const imagemSelecionada =
    galeria.find((item) => item.id === imagemSelecionadaId) ?? galeria[0] ?? null
  const status = anuncio ? anuncioStatus(anuncio.status) : null
  const podeMonetizar = anuncio ? anuncioPodeMonetizar(anuncio) : false
  const erroAtual: DetalheErro = erro ?? {
    tipo: 'TECNICO',
    titulo: 'Não foi possível carregar o anúncio',
    mensagem: 'Tente novamente em instantes.',
  }

  const aplicarCicloVida = useCallback((
    resultado: MeuAnuncioCicloVida,
    acao: CicloVidaAcao
  ) => {
    if (acao === 'REMOVER' || resultado.status === 'REMOVIDO') {
      router.replace('/meus-anuncios')
      return
    }
    setAnuncio((atual) => atual && atual.id === resultado.id
      ? {
          ...atual,
          status: resultado.status,
          statusModeracao: resultado.statusModeracao,
          atualizadoEm: resultado.atualizadoEm,
          acoesPermitidas: resultado.acoesPermitidas,
        }
      : atual)
  }, [router])

  return (
    <PainelShell
      title="Detalhes do anúncio"
      description="Consulte os dados atuais vinculados à sua conta."
    >
      {loading ? (
        <div className="flex min-h-[280px] items-center justify-center rounded-[28px] border border-slate-200 bg-white text-sm text-slate-500 shadow-sm">
          Carregando anúncio...
        </div>
      ) : erro || !anuncio || !status ? (
        <div className="rounded-[28px] border border-rose-200 bg-rose-50 p-6 text-center shadow-sm">
          <p className="font-semibold text-rose-900">{erroAtual.titulo}</p>
          <p className="mt-2 text-sm leading-6 text-rose-800">{erroAtual.mensagem}</p>
          <div className="mt-5 flex flex-col justify-center gap-3 sm:flex-row">
            <Link
              href="/meus-anuncios"
              className="inline-flex min-h-11 items-center justify-center rounded-lg border border-rose-300 px-4 py-2 text-sm font-semibold text-rose-900 transition hover:bg-rose-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2"
            >
              Voltar para Meus anúncios
            </Link>
            {erroAtual.tipo === 'TECNICO' ? (
              <button
                type="button"
                onClick={() => void carregar()}
                className="inline-flex min-h-11 items-center justify-center rounded-lg bg-rose-800 px-4 py-2 text-sm font-semibold text-white transition hover:bg-rose-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-700 focus-visible:ring-offset-2"
              >
                Tentar novamente
              </button>
            ) : null}
          </div>
        </div>
      ) : (
        <article className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm">
          <div className="grid min-w-0 lg:grid-cols-[minmax(0,1.05fr)_minmax(320px,0.95fr)]">
            <section aria-labelledby="galeria-anuncio" className="min-w-0 bg-slate-50 p-4 sm:p-6">
              <h2 id="galeria-anuncio" className="sr-only">Galeria segura do anúncio</h2>
              <div className="relative aspect-[4/3] min-h-[260px] overflow-hidden rounded-lg bg-slate-200 sm:min-h-[360px]">
                {imagemSelecionada ? (
                  <Image
                    key={imagemSelecionada.id}
                    src={imagemSelecionada.url}
                    alt={imagemSelecionada.alt}
                    fill
                    priority
                    className="object-cover object-center"
                    sizes="(max-width: 1024px) 100vw, 54vw"
                  />
                ) : (
                  <div className="flex h-full min-h-[260px] flex-col items-center justify-center px-6 text-center text-slate-600 sm:min-h-[360px]">
                    {anuncio.capa?.restrita || midiasProtegidas.length > 0 ? (
                      <ShieldCheckIcon className="h-12 w-12 text-slate-500" aria-hidden="true" />
                    ) : (
                      <PhotoIcon className="h-12 w-12 text-slate-400" aria-hidden="true" />
                    )}
                    <p className="mt-4 font-semibold text-slate-800">
                      {anuncio.capa?.restrita || midiasProtegidas.length > 0
                        ? 'Mídia protegida'
                        : 'Nenhuma mídia disponível'}
                    </p>
                    <p className="mt-2 max-w-sm text-sm leading-6">
                      {anuncio.capa?.restrita || midiasProtegidas.length > 0
                        ? 'A mídia ainda não possui URL pública autorizada.'
                        : 'Este anúncio ainda não possui fotos liberadas.'}
                    </p>
                  </div>
                )}
              </div>

              {galeria.length > 1 ? (
                <div className="mt-3 grid grid-cols-4 gap-2 sm:grid-cols-5" aria-label="Escolher foto da galeria">
                  {galeria.map((item) => {
                    const selecionada = item.id === imagemSelecionada?.id
                    return (
                      <button
                        key={item.id}
                        type="button"
                        aria-label={`Exibir ${item.alt}`}
                        aria-pressed={selecionada}
                        onClick={() => setImagemSelecionadaId(item.id)}
                        className={cn(
                          'relative aspect-square overflow-hidden rounded-md border bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2',
                          selecionada ? 'border-[#FC1EAD] ring-1 ring-[#FC1EAD]' : 'border-slate-200'
                        )}
                      >
                        <Image
                          src={item.url}
                          alt=""
                          fill
                          loading="lazy"
                          className="object-cover"
                          sizes="120px"
                        />
                      </button>
                    )
                  })}
                </div>
              ) : null}

              {estadosProtegidos.length > 0 ? (
                <div className="mt-4 rounded-lg border border-slate-200 bg-white px-4 py-3">
                  <p className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                    <ShieldCheckIcon className="h-5 w-5 text-slate-500" aria-hidden="true" />
                    Mídias sem exposição pública
                  </p>
                  <ul className="mt-2 space-y-1 text-sm text-slate-600">
                    {estadosProtegidos.map((estado) => <li key={estado}>{estado}</li>)}
                  </ul>
                </div>
              ) : null}
            </section>

            <section className="flex min-w-0 flex-col p-5 sm:p-7 lg:p-8" aria-labelledby="titulo-anuncio">
              <div className="flex flex-wrap items-center gap-2">
                <span className={cn('rounded-md border px-3 py-1 text-xs font-semibold', status.className)}>
                  {status.label}
                </span>
                <span className="rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-700">
                  {anuncioModeracao(anuncio.statusModeracao)}
                </span>
              </div>

              <h2 id="titulo-anuncio" className="mt-5 break-words text-2xl font-extrabold text-slate-950 sm:text-3xl">
                {anuncio.titulo}
              </h2>
              <p className="mt-3 text-2xl font-bold text-[#b5127a]">{anuncioPreco(anuncio.preco)}</p>
              <p className="mt-3 text-sm font-semibold text-slate-700">
                {rotuloOpcao(anuncio.categoria, CATEGORIAS)}
              </p>

              <dl className="mt-6 grid gap-4 border-t border-slate-200 pt-6 sm:grid-cols-4">
                <div className="min-w-0">
                  <dt className="text-xs font-semibold uppercase text-slate-500">Bairro</dt>
                  <dd className="mt-1 break-words text-sm font-medium text-slate-900">
                    {anuncio.localizacao?.bairro || 'Não informado'}
                  </dd>
                </div>
                <div className="min-w-0">
                  <dt className="text-xs font-semibold uppercase text-slate-500">Cidade</dt>
                  <dd className="mt-1 break-words text-sm font-medium text-slate-900">
                    {anuncio.localizacao?.cidade || 'Não informada'}
                  </dd>
                </div>
                <div className="min-w-0">
                  <dt className="text-xs font-semibold uppercase text-slate-500">UF</dt>
                  <dd className="mt-1 break-words text-sm font-medium text-slate-900">
                    {anuncio.localizacao?.uf || 'Não informada'}
                  </dd>
                </div>
                <div className="min-w-0">
                  <dt className="flex items-center gap-1 text-xs font-semibold uppercase text-slate-500">
                    <EyeIcon className="h-4 w-4" aria-hidden="true" />
                    Visualizações
                  </dt>
                  <dd className="mt-1 text-sm font-medium text-slate-900">
                    {formatarVisualizacoesCanonicas(anuncio.visualizacoes)}
                  </dd>
                </div>
              </dl>

              <p className="mt-5 flex min-w-0 items-start gap-2 text-sm leading-6 text-slate-600">
                <MapPinIcon className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" aria-hidden="true" />
                <span className="break-words">
                  {[anuncio.localizacao?.bairro, anuncio.localizacao?.cidade, anuncio.localizacao?.uf]
                    .filter(Boolean)
                    .join(', ') || 'Localização não informada'}
                </span>
              </p>

              <div className="mt-auto flex flex-col gap-3 pt-8 sm:flex-row sm:flex-wrap">
                <Link
                  href="/meus-anuncios"
                  className="inline-flex min-h-11 items-center justify-center rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:-translate-y-0.5 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500 focus-visible:ring-offset-2"
                >
                  <ArrowLeftIcon className="mr-2 h-4 w-4" aria-hidden="true" />
                  Voltar
                </Link>
                <Link
                  href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}/editar`}
                  className="inline-flex min-h-11 items-center justify-center rounded-lg bg-[#FC1EAD] px-5 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2"
                >
                  <PencilIcon className="mr-2 h-4 w-4" aria-hidden="true" />
                  Editar anúncio
                </Link>
                {podeMonetizar ? (
                  <Link
                    href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}/monetizar`}
                    className="inline-flex min-h-11 items-center justify-center rounded-lg border border-[#FC1EAD]/30 bg-[#FC1EAD]/5 px-5 py-2.5 text-sm font-semibold text-[#b5127a] transition hover:-translate-y-0.5 hover:border-[#FC1EAD]/50 hover:bg-[#FC1EAD]/10 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2"
                  >
                    <SparklesIcon className="mr-2 h-4 w-4" aria-hidden="true" />
                    Monetizar
                  </Link>
                ) : null}
                <MeuAnuncioAcoesCicloVida
                  anuncio={anuncio}
                  onSuccess={aplicarCicloVida}
                  className="w-full pt-2"
                />
              </div>
            </section>
          </div>

          <div className="grid gap-8 border-t border-slate-200 px-5 py-7 sm:px-7 lg:grid-cols-2 lg:px-8">
            <section aria-labelledby="descricao-anuncio" className="min-w-0">
              <h3 id="descricao-anuncio" className="text-lg font-bold text-slate-950">Descrição</h3>
              <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-7 text-slate-700">
                {anuncio.descricao?.trim() || 'Descrição não informada.'}
              </p>
            </section>

            <section aria-labelledby="contato-anuncio" className="min-w-0">
              <h3 id="contato-anuncio" className="text-lg font-bold text-slate-950">Contato do anúncio</h3>
              <div className="mt-3 flex min-w-0 items-start gap-2 text-sm text-slate-700">
                <PhoneIcon className="h-5 w-5 shrink-0 text-emerald-600" aria-hidden="true" />
                <div className="min-w-0">
                  <p className="font-semibold text-slate-900">WhatsApp</p>
                  <p className="mt-1 break-all">{anuncio.whatsapp || 'Não informado'}</p>
                </div>
              </div>
            </section>

            <section aria-labelledby="servicos-anuncio" className="min-w-0">
              <h3 id="servicos-anuncio" className="text-lg font-bold text-slate-950">Serviços</h3>
              {anuncio.servicos.length > 0 ? (
                <ul className="mt-3 flex flex-wrap gap-2">
                  {anuncio.servicos.map((servico) => (
                    <li key={servico} className="max-w-full break-words rounded-md bg-slate-100 px-3 py-1.5 text-sm text-slate-700">
                      {rotuloOpcao(servico, SERVICOS)}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 text-sm text-slate-600">Nenhum serviço informado.</p>
              )}
            </section>

            <section aria-labelledby="locais-anuncio" className="min-w-0">
              <h3 id="locais-anuncio" className="text-lg font-bold text-slate-950">Locais de atendimento</h3>
              {anuncio.locaisAtendimento.length > 0 ? (
                <ul className="mt-3 flex flex-wrap gap-2">
                  {anuncio.locaisAtendimento.map((local) => (
                    <li key={local} className="max-w-full break-words rounded-md bg-emerald-50 px-3 py-1.5 text-sm text-emerald-800">
                      {rotuloOpcao(local, LOCAIS_ATENDIMENTO)}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-3 text-sm text-slate-600">Nenhum local de atendimento informado.</p>
              )}
            </section>
          </div>
        </article>
      )}
    </PainelShell>
  )
}
