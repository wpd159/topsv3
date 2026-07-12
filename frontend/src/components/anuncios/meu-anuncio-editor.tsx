'use client'

import Image from 'next/image'
import Link from 'next/link'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { categorias, locais, servicos } from '@/features/anuncio-wizard/wizard-constants'
import {
  atualizarMeuAnuncio,
  buscarMeuAnuncio,
  MeusAnunciosApiError,
  type MeuAnuncio,
  type MeuAnuncioAtualizacao,
} from '@/lib/meus-anuncios-api'

const servicosEdicao = [
  ...servicos,
  { value: 'ATRIZ_PORNO', label: 'Atriz pornô' },
  { value: 'ATOR_PORNO', label: 'Ator pornô' },
  { value: 'EJACULACAO_CORPORAL', label: 'Ejaculação corporal' },
]

function humanizarEnum(value: string) {
  return value
    .toLocaleLowerCase('pt-BR')
    .split('_')
    .map((parte) => parte.charAt(0).toLocaleUpperCase('pt-BR') + parte.slice(1))
    .join(' ')
}

type FormState = {
  titulo: string
  descricao: string
  categoria: string
  preco: string
  uf: string
  cidade: string
  bairro: string
  locaisAtendimento: string[]
  servicos: string[]
  whatsapp: string
}

function formFromAnuncio(anuncio: MeuAnuncio): FormState {
  return {
    titulo: anuncio.titulo || '',
    descricao: anuncio.descricao || '',
    categoria: anuncio.categoria || '',
    preco: anuncio.preco == null ? '' : String(anuncio.preco),
    uf: anuncio.localizacao?.uf || '',
    cidade: anuncio.localizacao?.cidade || '',
    bairro: anuncio.localizacao?.bairro || '',
    locaisAtendimento: anuncio.locaisAtendimento || [],
    servicos: anuncio.servicos || [],
    whatsapp: anuncio.whatsapp || '',
  }
}

function toggleValue(values: string[], value: string) {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value]
}

function mensagemErro(error: unknown) {
  if (!(error instanceof MeusAnunciosApiError)) {
    return error instanceof Error ? error.message : 'Não foi possível salvar o anúncio.'
  }
  if (error.status === 400) return error.message || 'Revise os campos informados.'
  if (error.status === 403) return 'Você não tem permissão para editar este anúncio.'
  if (error.status === 404) return 'Anúncio não encontrado.'
  if (error.status === 409) return 'Este anúncio está em análise e não pode ser alterado agora.'
  return error.message
}

export function MeuAnuncioEditor({ slug }: { slug: string }) {
  const [anuncio, setAnuncio] = useState<MeuAnuncio | null>(null)
  const [form, setForm] = useState<FormState | null>(null)
  const [loading, setLoading] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const categoriasEdicao = form?.categoria && !categorias.some((item) => item.value === form.categoria)
    ? [...categorias, { value: form.categoria, label: humanizarEnum(form.categoria) }]
    : categorias

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      const resultado = await buscarMeuAnuncio(slug)
      setAnuncio(resultado)
      setForm(formFromAnuncio(resultado))
    } catch (error) {
      setErro(mensagemErro(error))
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    void carregar()
  }, [carregar])

  async function salvar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form || salvando) return
    setSalvando(true)
    setErro(null)
    setSucesso(null)

    const payload: MeuAnuncioAtualizacao = {
      titulo: form.titulo,
      descricao: form.descricao,
      categoria: form.categoria,
      preco: form.preco.trim() ? Number(form.preco.replace(',', '.')) : null,
      uf: form.uf,
      cidade: form.cidade,
      bairro: form.bairro.trim() || null,
      locaisAtendimento: form.locaisAtendimento,
      servicos: form.servicos,
      whatsapp: form.whatsapp.trim() || null,
    }

    try {
      const atualizado = await atualizarMeuAnuncio(slug, payload)
      setAnuncio(atualizado)
      setForm(formFromAnuncio(atualizado))
      setSucesso('Alterações salvas e enviadas para revisão.')
    } catch (error) {
      setErro(mensagemErro(error))
    } finally {
      setSalvando(false)
    }
  }

  return (
    <PainelShell
      title="Editar anúncio"
      description="Atualize as informações do seu anúncio. O endereço público e o histórico de publicação serão preservados."
    >
      {loading ? (
        <div className="flex min-h-[280px] items-center justify-center rounded-[28px] border border-slate-200 bg-white text-sm text-slate-500 shadow-sm">
          Carregando anúncio...
        </div>
      ) : !anuncio || !form ? (
        <div className="rounded-[28px] border border-rose-200 bg-rose-50 p-6 text-center shadow-sm">
          <p className="font-semibold text-rose-900">{erro || 'Não foi possível carregar o anúncio.'}</p>
          <Link href="/meus-anuncios" className="mt-4 inline-flex text-sm font-semibold text-rose-800 underline underline-offset-4">
            Voltar para Meus anúncios
          </Link>
        </div>
      ) : (
        <form onSubmit={salvar} className="space-y-6">
          <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
            <div className="grid gap-5 md:grid-cols-2">
              <label className="md:col-span-2">
                <span className="mb-2 block text-sm font-semibold text-slate-800">Título público</span>
                <input
                  value={form.titulo}
                  onChange={(event) => setForm({ ...form, titulo: event.target.value })}
                  minLength={10}
                  maxLength={80}
                  required
                  className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>

              <label className="md:col-span-2">
                <span className="mb-2 block text-sm font-semibold text-slate-800">Descrição</span>
                <textarea
                  value={form.descricao}
                  onChange={(event) => setForm({ ...form, descricao: event.target.value })}
                  minLength={20}
                  maxLength={600}
                  rows={7}
                  required
                  className="w-full resize-y rounded-xl border border-slate-300 px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-semibold text-slate-800">Categoria</span>
                <select
                  value={form.categoria}
                  onChange={(event) => setForm({ ...form, categoria: event.target.value })}
                  required
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                >
                  <option value="">Selecione</option>
                  {categoriasEdicao.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                </select>
              </label>

              <label>
                <span className="mb-2 block text-sm font-semibold text-slate-800">Preço</span>
                <input
                  type="number"
                  min="0.01"
                  max="999999.99"
                  step="0.01"
                  inputMode="decimal"
                  value={form.preco}
                  onChange={(event) => setForm({ ...form, preco: event.target.value })}
                  className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-semibold text-slate-800">UF</span>
                <input
                  value={form.uf}
                  onChange={(event) => setForm({ ...form, uf: event.target.value.toUpperCase().slice(0, 2) })}
                  minLength={2}
                  maxLength={2}
                  required
                  className="w-full rounded-xl border border-slate-300 px-4 py-3 uppercase text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-semibold text-slate-800">Cidade</span>
                <input
                  value={form.cidade}
                  onChange={(event) => setForm({ ...form, cidade: event.target.value })}
                  required
                  className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-semibold text-slate-800">Bairro</span>
                <input
                  value={form.bairro}
                  onChange={(event) => setForm({ ...form, bairro: event.target.value })}
                  className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-semibold text-slate-800">WhatsApp público</span>
                <input
                  type="tel"
                  value={form.whatsapp}
                  onChange={(event) => setForm({ ...form, whatsapp: event.target.value })}
                  placeholder="+55 62 99999-9999"
                  className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 outline-none transition focus:border-[#FC1EAD] focus:ring-4 focus:ring-pink-100"
                />
              </label>
            </div>
          </section>

          <section className="grid gap-6 lg:grid-cols-2">
            <fieldset className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
              <legend className="px-1 text-base font-bold text-slate-900">Locais de atendimento</legend>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {locais.map((item) => (
                  <label key={item.value} className="flex min-w-0 items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm text-slate-800">
                    <input
                      type="checkbox"
                      checked={form.locaisAtendimento.includes(item.value)}
                      onChange={() => setForm({ ...form, locaisAtendimento: toggleValue(form.locaisAtendimento, item.value) })}
                      className="h-4 w-4 accent-[#FC1EAD]"
                    />
                    <span className="min-w-0 break-words">{item.label}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
              <legend className="px-1 text-base font-bold text-slate-900">Serviços</legend>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {servicosEdicao.map((item) => (
                  <label key={item.value} className="flex min-w-0 items-center gap-3 rounded-xl border border-slate-200 px-4 py-3 text-sm text-slate-800">
                    <input
                      type="checkbox"
                      checked={form.servicos.includes(item.value)}
                      onChange={() => setForm({ ...form, servicos: toggleValue(form.servicos, item.value) })}
                      className="h-4 w-4 accent-[#FC1EAD]"
                    />
                    <span className="min-w-0 break-words">{item.label}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          </section>

          <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm sm:p-7">
            <h2 className="text-lg font-bold text-slate-900">Mídias atuais</h2>
            <p className="mt-1 text-sm text-slate-600">A ordem, a moderação e a visibilidade das mídias não são alteradas nesta tela.</p>
            {anuncio.midias.length ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {anuncio.midias.map((midia) => (
                  <article key={midia.id} className="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
                    <div className="relative aspect-[4/3] bg-slate-200">
                      <Image
                        src={midia.urlPublica || '/icone-sem-foto.png'}
                        alt={`Mídia ${midia.ordem ?? ''} do anúncio`}
                        fill
                        className="object-cover"
                        sizes="(max-width: 640px) 100vw, 320px"
                      />
                    </div>
                    <div className="space-y-1 p-4 text-xs text-slate-600">
                      <p className="font-semibold text-slate-900">Ordem {midia.ordem ?? 'não definida'}</p>
                      <p>{midia.tipo || 'Mídia'} · {midia.status || 'Status indisponível'}</p>
                      {midia.restrita ? <p>Conteúdo protegido, sem URL exposta.</p> : null}
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <p className="mt-4 rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">Este anúncio ainda não possui mídias vinculadas.</p>
            )}
          </section>

          {erro ? <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-900">{erro}</p> : null}
          {sucesso ? <p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-900">{sucesso}</p> : null}

          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Link href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}`} className="inline-flex min-h-11 items-center justify-center rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:-translate-y-0.5 hover:shadow-sm">
              Cancelar
            </Link>
            <button
              type="submit"
              disabled={salvando}
              className="inline-flex min-h-11 items-center justify-center rounded-lg bg-[#FC1EAD] px-6 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              {salvando ? 'Salvando...' : 'Salvar alterações'}
            </button>
          </div>
        </form>
      )}
    </PainelShell>
  )
}
