'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2, Save } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { MaskedPhoneInput } from '@/components/forms/masked-phone-input'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { categorias, locais, servicos } from '@/features/anuncio-wizard/wizard-constants'
import { getAdminSession } from '@/lib/admin-auth-api'
import { ApiContractError } from '@/lib/api-contract'
import { maskPhoneBR, phoneToE164BR } from '@/lib/phone-mask'
import {
  anuncioEstaPublicamenteIndexavel,
  enviarIndexNowNoCliente,
  montarEventoIndexNowAnuncio,
} from '@/lib/seo/indexnow-client'

import { getAdminAd, updateAdminAd } from './api'
import type { AdminAdDetail, AdminAdUpdate } from './types'

function initial(ad: AdminAdDetail): AdminAdUpdate {
  return {
    titulo: ad.titulo,
    descricao: ad.descricao || '',
    categoria: ad.categoria || '',
    preco: ad.preco ?? null,
    uf: ad.localizacao?.uf || '',
    cidade: ad.localizacao?.cidade || '',
    bairro: ad.localizacao?.bairro || null,
    enderecoResumido: ad.localizacao?.enderecoResumido || null,
    locaisAtendimento: ad.locaisAtendimento,
    servicos: ad.servicos,
    whatsapp: ad.whatsapp ? maskPhoneBR(ad.whatsapp) : null,
    atendimentoExclusivamenteVirtual: ad.atendimentoExclusivamenteVirtual,
  }
}

export function AdminAnuncioEditForm({ anuncioId }: { anuncioId: string }) {
  const router = useRouter()
  const [form, setForm] = useState<AdminAdUpdate | null>(null)
  const [original, setOriginal] = useState<AdminAdDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [hadWhatsapp, setHadWhatsapp] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setSaveError(null)
    setForm(null)
    setOriginal(null)
    try {
      const [ad, session] = await Promise.all([getAdminAd(anuncioId), getAdminSession()])
      if (!session?.papeis.includes('ADMIN') || !session.permissoes.includes('ANUNCIO_MODERAR')) {
        throw new Error('Seu perfil não possui permissão para editar dados comerciais.')
      }
      setOriginal(ad)
      if (ad.status === 'REMOVIDO') return
      setForm(initial(ad))
      setHadWhatsapp(Boolean(ad.whatsapp))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [anuncioId])

  useEffect(() => { void load() }, [load])

  function toggle(field: 'servicos' | 'locaisAtendimento', value: string) {
    setForm((current) => current ? {
      ...current,
      [field]: current[field].includes(value) ? current[field].filter((item) => item !== value) : [...current[field], value],
      ...(field === 'servicos' && value === 'VIDEOCHAMADA' && current[field].includes(value)
        ? { atendimentoExclusivamenteVirtual: false }
        : {}),
    } : current)
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!form || saving || original?.status === 'REMOVIDO') return
    const normalizedWhatsapp = phoneToE164BR(form.whatsapp || '')
    if ((form.whatsapp && !normalizedWhatsapp) || (hadWhatsapp && !normalizedWhatsapp)) {
      setSaveError('Informe DDD e número completo antes de salvar o WhatsApp.')
      return
    }
    setSaving(true)
    setSaveError(null)
    try {
      const updated = await updateAdminAd(anuncioId, {
        ...form,
        whatsapp: normalizedWhatsapp,
      })
      if (original && anuncioEstaPublicamenteIndexavel(original.status)) {
        void enviarIndexNowNoCliente(montarEventoIndexNowAnuncio({
          eventType: 'ATUALIZACAO',
          previous: {
            slug: original.slug,
            estadoUf: original.localizacao?.uf,
            cidadeNome: original.localizacao?.cidade,
            bairroNome: original.localizacao?.bairro,
          },
          current: {
            slug: updated.slug,
            estadoUf: updated.localizacao?.uf,
            cidadeNome: updated.localizacao?.cidade,
            bairroNome: updated.localizacao?.bairro,
          },
          changeFingerprint: updated.atualizadoEm,
        }))
      }
      router.push(`/admin/anuncios/${anuncioId}`)
      router.refresh()
    } catch (reason) {
      // A recusa do salvamento, inclusive 404, nao comprova remocao por si so.
      const current = await getAdminAd(anuncioId).catch(() => null)
      if (current?.status === 'REMOVIDO') {
        setOriginal(current)
        setForm(null)
      } else {
        setSaveError(reason instanceof ApiContractError
          && ['INVALID_REQUEST', 'CONFLICT', 'SESSION_REQUIRED', 'ACCESS_DENIED'].includes(reason.kind)
          ? reason.message
          : 'O salvamento não foi confirmado. Tente novamente.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="flex items-center justify-center gap-2 py-16 text-sm text-zinc-500"><Loader2 className="h-4 w-4 animate-spin" />Carregando anúncio...</p>
  if (error && !form) return <ContractState error={error} onRetry={() => void load()} />
  if (original?.status === 'REMOVIDO') return (
    <section className="mx-auto max-w-4xl space-y-4">
      <div role="status" className="border border-amber-300 bg-amber-50 p-4 text-amber-900">
        <h1 className="font-semibold">Anúncio removido</h1>
        <p className="mt-1">Este anúncio foi removido e não pode ser editado.</p>
      </div>
      <Button asChild type="button" variant="outline"><Link href={`/admin/anuncios/${anuncioId}`}>Voltar ao detalhe</Link></Button>
    </section>
  )
  if (!form) return null

  return (
    <form onSubmit={submit} className="mx-auto max-w-4xl space-y-6">
      <header className="flex flex-col gap-3 border-b border-zinc-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div><p className="text-sm font-semibold text-pink-700">Edição administrativa</p><h1 className="mt-1 text-2xl font-bold text-zinc-950">Editar anúncio</h1><p className="mt-1 text-sm text-zinc-600">As mesmas regras canônicas do wizard são aplicadas e a ação fica auditada.</p></div>
        <Button asChild type="button" variant="outline"><Link href={`/admin/anuncios/${anuncioId}`}>Cancelar</Link></Button>
      </header>
      {saveError ? (
        <div role="alert" className="border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          <p className="font-semibold">Não foi possível salvar o anúncio</p>
          <p className="mt-1">{saveError}</p>
        </div>
      ) : null}
      <div className="grid gap-5 sm:grid-cols-2">
        <label className="sm:col-span-2"><span className="mb-1 block text-sm font-semibold">Título</span><Input value={form.titulo} minLength={10} maxLength={80} onChange={(event) => setForm({ ...form, titulo: event.target.value })} required /></label>
        <label className="sm:col-span-2"><span className="mb-1 block text-sm font-semibold">Descrição</span><Textarea value={form.descricao} minLength={20} maxLength={600} rows={7} onChange={(event) => setForm({ ...form, descricao: event.target.value })} required /></label>
        <label><span className="mb-1 block text-sm font-semibold">Categoria</span><select className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={form.categoria} onChange={(event) => setForm({ ...form, categoria: event.target.value })} required><option value="">Selecione</option>{categorias.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
        <label><span className="mb-1 block text-sm font-semibold">Preço</span><Input type="number" min="0.01" max="999999.99" step="0.01" value={form.preco ?? ''} onChange={(event) => setForm({ ...form, preco: event.target.value ? Number(event.target.value) : null })} /></label>
        <label><span className="mb-1 block text-sm font-semibold">UF</span><Input value={form.uf} minLength={2} maxLength={2} onChange={(event) => setForm({ ...form, uf: event.target.value.toUpperCase() })} required /></label>
        <label><span className="mb-1 block text-sm font-semibold">Cidade</span><Input value={form.cidade} minLength={2} maxLength={80} onChange={(event) => setForm({ ...form, cidade: event.target.value })} required /></label>
        <label><span className="mb-1 block text-sm font-semibold">Bairro</span><Input value={form.bairro || ''} maxLength={80} onChange={(event) => setForm({ ...form, bairro: event.target.value || null })} /></label>
        <label><span className="mb-1 block text-sm font-semibold">Região</span><Input value={form.enderecoResumido || ''} maxLength={120} placeholder="Ex.: Centro" onChange={(event) => setForm({ ...form, enderecoResumido: event.target.value || null })} /></label>
        <label>
          <span className="mb-1 block text-sm font-semibold">WhatsApp</span>
          <MaskedPhoneInput
            value={form.whatsapp || ''}
            onValueChange={(value) => setForm({ ...form, whatsapp: value || null })}
            placeholder="(00) 00000-0000"
          />
        </label>
      </div>
      <fieldset><legend className="text-sm font-semibold text-zinc-950">Serviços</legend><div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{servicos.map((item) => <label key={item.value} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.servicos.includes(item.value)} onChange={() => toggle('servicos', item.value)} />{item.label}</label>)}</div></fieldset>
      {form.servicos.includes('VIDEOCHAMADA') ? (
        <label className="flex items-start gap-3 rounded-lg border border-zinc-200 bg-zinc-50 p-4">
          <input
            type="checkbox"
            className="mt-0.5 size-4 accent-pink-500"
            checked={form.atendimentoExclusivamenteVirtual}
            onChange={(event) => setForm({
              ...form,
              atendimentoExclusivamenteVirtual: event.target.checked,
            })}
          />
          <span>
            <span className="block text-sm font-semibold text-zinc-950">
              Atendimento exclusivamente virtual
            </span>
            <span className="mt-1 block text-sm text-zinc-600">
              Marque apenas se você não realiza atendimento presencial
            </span>
          </span>
        </label>
      ) : null}
      <fieldset><legend className="text-sm font-semibold text-zinc-950">Locais de atendimento</legend><div className="mt-3 flex flex-wrap gap-4">{locais.map((item) => <label key={item.value} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.locaisAtendimento.includes(item.value)} onChange={() => toggle('locaisAtendimento', item.value)} />{item.label}</label>)}</div></fieldset>
      <div className="flex justify-end border-t border-zinc-200 pt-5"><Button type="submit" disabled={saving}>{saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}Salvar alterações</Button></div>
    </form>
  )
}
