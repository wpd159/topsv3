'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Archive,
  ArrowDown,
  ArrowUp,
  Eye,
  EyeOff,
  FileQuestion,
  Pencil,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  adminFaqError,
  arquivarAdminFaq,
  atualizarAdminFaq,
  criarAdminFaq,
  listarAdminFaqs,
  publicarAdminFaq,
  reordenarAdminFaq,
  retirarAdminFaq,
  type AdminFaq,
  type AdminFaqEdicao,
} from '@/lib/admin-faq-api'

const categorias = [
  ['GERAL', 'Geral'],
  ['CONTA', 'Conta'],
  ['PAGAMENTOS', 'Pagamentos'],
  ['SEGURANCA', 'Segurança'],
  ['ANUNCIOS', 'Anúncios'],
] as const

const estados = [
  ['TODOS', 'Todos os estados'],
  ['RASCUNHO', 'Rascunhos'],
  ['PUBLICADO', 'Publicados'],
  ['ARQUIVADO', 'Arquivados'],
] as const

const vazio: AdminFaqEdicao = {
  pergunta: '',
  resposta: '',
  categoria: 'GERAL',
  ordem: 0,
  versao: null,
}

export default function AdminFaqPage() {
  const [faqs, setFaqs] = useState<AdminFaq[]>([])
  const [termo, setTermo] = useState('')
  const [termoAplicado, setTermoAplicado] = useState('')
  const [status, setStatus] = useState('TODOS')
  const [categoria, setCategoria] = useState('TODAS')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [sucesso, setSucesso] = useState<string | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editandoId, setEditandoId] = useState<string | null>(null)
  const [form, setForm] = useState<AdminFaqEdicao>(vazio)
  const [operando, setOperando] = useState<string | null>(null)

  const filtros = useMemo(() => ({
    termo: termoAplicado,
    status,
    categoria,
  }), [categoria, status, termoAplicado])

  const carregar = useCallback(async (signal?: AbortSignal) => {
    setCarregando(true)
    setErro(null)
    try {
      setFaqs(await listarAdminFaqs(filtros, signal))
    } catch (cause) {
      if (!signal?.aborted) setErro(adminFaqError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [filtros])

  useEffect(() => {
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar])

  const abrirNova = () => {
    setEditandoId(null)
    setForm({ ...vazio, ordem: faqs.length })
    setEditorOpen(true)
    setErro(null)
  }

  const abrirEdicao = (faq: AdminFaq) => {
    setEditandoId(faq.id)
    setForm({
      pergunta: faq.pergunta,
      resposta: faq.resposta,
      categoria: faq.categoria,
      ordem: faq.ordem,
      versao: faq.versao,
    })
    setEditorOpen(true)
    setErro(null)
  }

  const executar = async (id: string, action: () => Promise<AdminFaq>, message: string) => {
    if (operando) return
    setOperando(id)
    setErro(null)
    setSucesso(null)
    try {
      await action()
      setSucesso(message)
      await carregar()
    } catch (cause) {
      setErro(adminFaqError(cause).message)
    } finally {
      setOperando(null)
    }
  }

  const salvar = async () => {
    if (operando) return
    setOperando(editandoId || 'nova')
    setErro(null)
    setSucesso(null)
    try {
      if (editandoId) {
        await atualizarAdminFaq(editandoId, form)
        setSucesso('FAQ atualizada.')
      } else {
        await criarAdminFaq(form)
        setSucesso('FAQ criada como rascunho.')
      }
      setEditorOpen(false)
      await carregar()
    } catch (cause) {
      setErro(adminFaqError(cause).message)
    } finally {
      setOperando(null)
    }
  }

  return (
    <section className="space-y-5">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">FAQs</h1>
          <p className="text-sm text-zinc-600">
            Fonte única das perguntas exibidas na página pública.
          </p>
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="outline" onClick={() => void carregar()}>
            <RefreshCw className={`mr-2 h-4 w-4 ${carregando ? 'animate-spin' : ''}`} />
            Atualizar
          </Button>
          <Button type="button" onClick={abrirNova}>
            <Plus className="mr-2 h-4 w-4" />
            Nova FAQ
          </Button>
        </div>
      </header>

      <div className="grid gap-3 border-y py-4 lg:grid-cols-[minmax(260px,1fr)_210px_210px_auto]">
        <form
          className="relative"
          onSubmit={(event) => {
            event.preventDefault()
            setTermoAplicado(termo.trim())
          }}
        >
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-zinc-400" />
          <Input
            value={termo}
            onChange={(event) => setTermo(event.target.value)}
            placeholder="Buscar pergunta ou resposta"
            className="pl-9"
          />
        </form>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {estados.map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={categoria} onValueChange={setCategoria}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TODAS">Todas as categorias</SelectItem>
            {categorias.map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setTermo('')
            setTermoAplicado('')
            setStatus('TODOS')
            setCategoria('TODAS')
          }}
        >
          Limpar
        </Button>
      </div>

      {erro ? (
        <div className="flex flex-col justify-between gap-3 border-y border-red-200 bg-red-50 py-4 text-sm text-red-800 sm:flex-row sm:items-center">
          <p>{erro}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {sucesso ? (
        <p role="status" className="border-y border-emerald-200 bg-emerald-50 py-3 text-sm text-emerald-800">
          {sucesso}
        </p>
      ) : null}

      {carregando ? (
        <p className="py-16 text-center text-sm text-zinc-500">Carregando FAQs...</p>
      ) : null}

      {!carregando && !erro && faqs.length === 0 ? (
        <div className="flex min-h-64 flex-col items-center justify-center gap-3 border-y py-12 text-center">
          <FileQuestion className="h-8 w-8 text-zinc-400" />
          <div>
            <p className="font-medium text-zinc-900">Nenhuma FAQ encontrada</p>
            <p className="text-sm text-zinc-500">Crie uma pergunta ou ajuste os filtros.</p>
          </div>
        </div>
      ) : null}

      <div className="divide-y border-y">
        {faqs.map((faq) => {
          const busy = operando === faq.id
          return (
            <article
              key={faq.id}
              className="grid gap-3 py-4 lg:grid-cols-[70px_minmax(220px,1fr)_140px_120px_minmax(300px,auto)] lg:items-center"
            >
              <p className="text-sm font-semibold text-zinc-600">#{faq.ordem}</p>
              <div className="min-w-0">
                <p className="font-semibold text-zinc-950">{faq.pergunta}</p>
                <p className="mt-1 line-clamp-2 whitespace-pre-line text-sm text-zinc-500">
                  {faq.resposta}
                </p>
              </div>
              <span className="text-sm text-zinc-700">{faq.categoriaRotulo}</span>
              <span className={`w-fit border px-2 py-1 text-xs font-medium ${
                faq.status === 'PUBLICADO'
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                  : faq.status === 'ARQUIVADO'
                    ? 'border-zinc-300 bg-zinc-100 text-zinc-600'
                    : 'border-amber-200 bg-amber-50 text-amber-800'
              }`}>
                {faq.status === 'PUBLICADO'
                  ? 'Publicada'
                  : faq.status === 'ARQUIVADO'
                    ? 'Arquivada'
                    : 'Rascunho'}
              </span>
              <div className="flex flex-wrap gap-1 lg:justify-end">
                <Button type="button" size="icon" variant="outline" title="Editar FAQ" onClick={() => abrirEdicao(faq)}>
                  <Pencil className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  size="icon"
                  variant="outline"
                  title="Mover para cima"
                  disabled={busy || faq.ordem === 0}
                  onClick={() => void executar(
                    faq.id,
                    () => reordenarAdminFaq(faq.id, Math.max(0, faq.ordem - 1), faq.versao),
                    'Ordem atualizada.',
                  )}
                >
                  <ArrowUp className="h-4 w-4" />
                </Button>
                <Button
                  type="button"
                  size="icon"
                  variant="outline"
                  title="Mover para baixo"
                  disabled={busy}
                  onClick={() => void executar(
                    faq.id,
                    () => reordenarAdminFaq(faq.id, faq.ordem + 1, faq.versao),
                    'Ordem atualizada.',
                  )}
                >
                  <ArrowDown className="h-4 w-4" />
                </Button>
                {faq.status === 'PUBLICADO' ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={busy}
                    onClick={() => void executar(
                      faq.id,
                      () => retirarAdminFaq(faq.id, faq.versao),
                      'FAQ retirada da página pública.',
                    )}
                  >
                    <EyeOff className="mr-2 h-4 w-4" />
                    Retirar
                  </Button>
                ) : faq.status === 'RASCUNHO' ? (
                  <Button
                    type="button"
                    size="sm"
                    disabled={busy}
                    onClick={() => void executar(
                      faq.id,
                      () => publicarAdminFaq(faq.id, faq.versao),
                      'FAQ publicada.',
                    )}
                  >
                    <Eye className="mr-2 h-4 w-4" />
                    Publicar
                  </Button>
                ) : null}
                {faq.status !== 'ARQUIVADO' ? (
                  <Button
                    type="button"
                    size="icon"
                    variant="destructive"
                    title="Arquivar FAQ"
                    disabled={busy}
                    onClick={() => void executar(
                      faq.id,
                      () => arquivarAdminFaq(faq.id, faq.versao),
                      'FAQ arquivada.',
                    )}
                  >
                    <Archive className="h-4 w-4" />
                  </Button>
                ) : null}
              </div>
            </article>
          )
        })}
      </div>

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editandoId ? 'Editar FAQ' : 'Nova FAQ'}</DialogTitle>
            <DialogDescription>
              O conteúdo é salvo como texto seguro. A publicação é uma ação separada.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="faq-question">Pergunta</Label>
              <Input
                id="faq-question"
                maxLength={240}
                value={form.pergunta}
                onChange={(event) => setForm((current) => ({ ...current, pergunta: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="faq-answer">Resposta</Label>
              <Textarea
                id="faq-answer"
                rows={7}
                maxLength={4000}
                value={form.resposta}
                onChange={(event) => setForm((current) => ({ ...current, resposta: event.target.value }))}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label>Categoria</Label>
                <Select
                  value={form.categoria}
                  onValueChange={(value: AdminFaq['categoria']) => (
                    setForm((current) => ({ ...current, categoria: value }))
                  )}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {categorias.map(([value, label]) => (
                      <SelectItem key={value} value={value}>{label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="faq-order">Ordem</Label>
                <Input
                  id="faq-order"
                  type="number"
                  min={0}
                  max={100000}
                  value={form.ordem}
                  onChange={(event) => setForm((current) => ({
                    ...current,
                    ordem: Number(event.target.value),
                  }))}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setEditorOpen(false)}>
              Cancelar
            </Button>
            <Button
              type="button"
              disabled={Boolean(operando) || !form.pergunta.trim() || !form.resposta.trim()}
              onClick={() => void salvar()}
            >
              {operando ? 'Salvando...' : 'Salvar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
