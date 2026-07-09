'use client'

import { useParams, useRouter } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import FotosAnuncioSection from '../../components/fotos-anuncio-section'
import AdminAnuncioStoriesSection from '../../components/anuncios/admin-anuncio-stories-section'
import DocumentosUsuarioSection from '../../components/documentos-usuarios-section'
import ReprovarAnuncioDialog from '../../components/anuncios/reprovar-anuncio-dialog'
import { toast } from 'sonner'
import {
  classificacaoEstaDefinida,
  formatarClassificacaoConteudo,
} from '@/lib/compliance/content-classification'
import { corrigirEstruturaTexto, corrigirTextoCorrompido } from '@/lib/text/encoding'

import {
  ArrowLeftIcon,
  ArrowRightIcon,
  SparklesIcon,
  CheckCircleIcon,
  MapPinIcon,
  TagIcon,
  ClipboardDocumentListIcon,
  PencilSquareIcon,
  BuildingOffice2Icon,
  TrashIcon,
  ExclamationTriangleIcon,
  PauseIcon,
  PlayIcon,
  XCircleIcon,
} from '@heroicons/react/24/solid'

import { normalizarCategoria, normalizarHorario, servicosDisponiveis } from '@/utils/normalizer'
import { formatCPF } from '@/utils/formatter'
import { enviarIndexNowNoCliente, montarUrlsIndexNowAnuncio } from '@/lib/seo/indexnow-client'

type AnuncioStaffDetalhe = {
  id: number
  titulo?: string | null
  username?: string | null
  nomeCompleto?: string | null
  cpf?: string | null
  categoria?: string | null
  preco?: number | null
  horario?: string | null
  status?: string | null
  descricao?: string | null
  linkConteudo?: string | null
  contentClassification?: string | null

  locaisAtendimento?: string[] | null
  servicos?: string[] | null

  fotosUrl?: string[] | null
  videosUrl?: string[] | null
  documentosUsuario?: string[] | null
  pendingRevision?: boolean | null
  pendingRevisionId?: number | null
  pendingRevisionStatus?: string | null

  estadoId?: number | null
  estadoNome?: string | null
  estadoUf?: string | null
  cidadeId?: number | null
  cidadeNome?: string | null
  bairroId?: number | null
  bairroNome?: string | null
  localizacaoLabel?: string | null

  cidadeAnunciante?: string | null
}

type StaffStatus = 'ATIVO' | 'PAUSADO' | 'REJEITADO'

type AnuncioRevisionPendingMediaItem = {
  id: number
  url: string
  mediaType: string
  sourceType: string
  removable: boolean
}

type AnuncioRevisionDetail = {
  revisionId: number
  status: string
  source: string
  submittedByEmail?: string | null
  submittedAt?: string | null
  currentContentClassification?: string | null
  pendingContentClassification?: string | null
  changedFields?: string[] | null
  changes?: { field: string; label: string; currentValue?: string | null; pendingValue?: string | null }[] | null
  pendingFotos?: string[] | null
  pendingVideos?: string[] | null
  pendingMediaItems?: AnuncioRevisionPendingMediaItem[] | null
}

type ModerationClassification = 'SAFE_PUBLIC' | 'ADULT_NON_EXPLICIT' | 'ADULT_EXPLICIT_BLOCKED'

type AdminEditSnapshotDTO = {
  id: number
  slug: string
  titulo: string
  categoria: string
  preco?: string | number | null
  horario?: string | null
  locaisAtendimento: string[]
  servicos: string[]
  descricao?: string | null
  linkConteudo?: string | null
  contentClassification?: string | null
  cidadeId?: number | null
  bairroId?: number | null
  fotos: string[]
  videosAnuncio?: string[]
  pendingRevision?: boolean | null
}

type AdminEditFormState = {
  titulo: string
  categoria: string
  preco: string
  horario: string
  locaisAtendimento: string[]
  servicos: string[]
  descricao: string
  linkConteudo: string
  contentClassification: string
}

const EDIT_CLASSIFICATION_OPTIONS = [
  { value: 'SAFE_PUBLIC', label: 'Conteúdo live (público)' },
  { value: 'ADULT_NON_EXPLICIT', label: 'Adulto — seminudez' },
  { value: 'ADULT_RESTRICTED', label: 'Adulto restrito (verificação)' },
  { value: 'ADULT_EXPLICIT_BLOCKED', label: 'Explícito — bloqueio forte' },
] as const

const MODERATION_CLASSIFICATION_OPTIONS: Array<{
  value: ModerationClassification
  label: string
  helper: string
}> = [
  { value: 'SAFE_PUBLIC', label: 'Conteúdo live', helper: 'Nível leve e público.' },
  { value: 'ADULT_NON_EXPLICIT', label: 'Conteúdo com seminudez', helper: 'Nível médio sem bloqueio forte.' },
  { value: 'ADULT_EXPLICIT_BLOCKED', label: 'Conteúdo explícito', helper: 'Bloqueio com foto borrada.' },
]

function normalizarClassificacaoModeracao(value?: string | null): ModerationClassification {
  switch (value) {
    case 'SAFE_PUBLIC':
      return 'SAFE_PUBLIC'
    case 'ADULT_EXPLICIT_BLOCKED':
      return 'ADULT_EXPLICIT_BLOCKED'
    case 'ADULT_RESTRICTED':
    case 'ADULT_NON_EXPLICIT':
      return 'ADULT_NON_EXPLICIT'
    default:
      return 'SAFE_PUBLIC'
  }
}

export default function DetalhesAnuncioPage() {
  const params = useParams()
  const router = useRouter()

  const idParam = Array.isArray((params as any).id) ? (params as any).id[0] : (params as any).id
  const anuncioId = Number(idParam)

  const [anuncio, setAnuncio] = useState<AnuncioStaffDetalhe | null>(null)
  const [revision, setRevision] = useState<AnuncioRevisionDetail | null>(null)
  const [loading, setLoading] = useState(true)

  const [prevId, setPrevId] = useState<number | null>(null)
  const [nextId, setNextId] = useState<number | null>(null)

  const [openDelete, setOpenDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const [openRejeitar, setOpenRejeitar] = useState(false)
  const [motivoRejeicao, setMotivoRejeicao] = useState('')
  const [changingStatus, setChangingStatus] = useState(false)
  const [selectedClassification, setSelectedClassification] =
    useState<ModerationClassification>('ADULT_NON_EXPLICIT')
  const [moderationFeedback, setModerationFeedback] = useState<{
    type: 'error' | 'info'
    message: string
  } | null>(null)

  const [editOpen, setEditOpen] = useState(false)
  const [editLoading, setEditLoading] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editSnapshot, setEditSnapshot] = useState<AdminEditSnapshotDTO | null>(null)
  const [editForm, setEditForm] = useState<AdminEditFormState | null>(null)

  const [midiaPreview, setMidiaPreview] = useState<{ url: string; label: string; video?: boolean } | null>(
    null
  )
  const [mediaActionBusy, setMediaActionBusy] = useState(false)

  function calcularPrevNext(lista: any[], currentId: number) {
    const numericIds = lista
      .map((item) => Number(item.id))
      .filter((n) => !Number.isNaN(n))
      .sort((a, b) => a - b)

    const index = numericIds.indexOf(currentId)

    if (index === -1) {
      setPrevId(null)
      setNextId(null)
      return
    }

    setPrevId(index > 0 ? numericIds[index - 1] : null)
    setNextId(index < numericIds.length - 1 ? numericIds[index + 1] : null)
  }

  useEffect(() => {
    if (Number.isNaN(anuncioId)) return

    async function fetchIds() {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!res.ok) throw new Error('Falha ao buscar IDs')
        const lista = await res.json().then(corrigirEstruturaTexto)
        calcularPrevNext(lista, anuncioId)
      } catch {
        setPrevId(null)
        setNextId(null)
      }
    }

    fetchIds()
  }, [anuncioId])

  const recarregarAnuncioERevisao = useCallback(async () => {
    if (!idParam) return
    const api = process.env.NEXT_PUBLIC_API_URL
    try {
      const res = await fetch(`${api}/anuncios/staff/${idParam}`, {
        credentials: 'include',
        cache: 'no-store',
      })
      if (res.ok) {
        const data = corrigirEstruturaTexto((await res.json()) as AnuncioStaffDetalhe)
        setAnuncio(data)
      }
    } catch {
      /* ignore */
    }

    try {
      const resRev = await fetch(`${api}/anuncios/staff/${idParam}/revision`, {
        credentials: 'include',
        cache: 'no-store',
      })
      if (resRev.status === 204) {
        setRevision(null)
        return
      }
      if (!resRev.ok) {
        setRevision(null)
        return
      }
      const revData = corrigirEstruturaTexto((await resRev.json()) as AnuncioRevisionDetail)
      setRevision(revData)
    } catch {
      setRevision(null)
    }
  }, [idParam])

  const abrirEditor = useCallback(async () => {
    if (!idParam) return
    const api = process.env.NEXT_PUBLIC_API_URL
    try {
      setEditOpen(true)
      setEditLoading(true)
      const res = await fetch(`${api}/anuncios/staff/id/${idParam}/editar`, {
        credentials: 'include',
        cache: 'no-store',
      })
      if (!res.ok) throw new Error(await res.text().catch(() => 'Falha ao carregar editor.'))
      const raw = corrigirEstruturaTexto((await res.json()) as AdminEditSnapshotDTO)
      setEditSnapshot(raw)

      const precoValue =
        raw.preco === null || raw.preco === undefined
          ? ''
          : typeof raw.preco === 'number'
            ? String(raw.preco)
            : String(raw.preco)
      const cc =
        raw.contentClassification && EDIT_CLASSIFICATION_OPTIONS.some((o) => o.value === raw.contentClassification)
          ? raw.contentClassification
          : 'SAFE_PUBLIC'

      setEditForm({
        titulo: corrigirTextoCorrompido(raw.titulo ?? ''),
        categoria: raw.categoria ?? '',
        preco: precoValue,
        horario: raw.horario ?? '',
        locaisAtendimento: Array.isArray(raw.locaisAtendimento) ? raw.locaisAtendimento : [],
        servicos: Array.isArray(raw.servicos) ? raw.servicos : [],
        descricao: corrigirTextoCorrompido(raw.descricao ?? ''),
        linkConteudo: corrigirTextoCorrompido(raw.linkConteudo ?? ''),
        contentClassification: cc,
      })
    } catch (e) {
      toast.error('Não foi possível carregar o painel de edição.')
      setEditOpen(false)
      setEditSnapshot(null)
      setEditForm(null)
    } finally {
      setEditLoading(false)
    }
  }, [idParam])

  const cancelarEdicao = useCallback(() => {
    setEditOpen(false)
    setEditSnapshot(null)
    setEditForm(null)
  }, [])

  const salvarEdicao = useCallback(async () => {
    if (!idParam || !editForm || !editSnapshot) return
    const api = process.env.NEXT_PUBLIC_API_URL
    try {
      setEditSaving(true)
      const precoRaw = String(editForm.preco ?? '').trim()
      let precoParaEnvio: string | undefined
      if (precoRaw.length > 0) {
        const precoNumerico = precoRaw
          .replace(/[^\d,.-]/g, '')
          .replace(/\./g, '')
          .replace(',', '.')
        const n = parseFloat(precoNumerico)
        if (!Number.isFinite(n) || n <= 0) {
          toast.error('Informe um preço maior que zero.')
          return
        }
        precoParaEnvio = n.toFixed(2)
      }

      const fd = new FormData()
      fd.append('titulo', editForm.titulo ?? '')
      fd.append('categoria', editForm.categoria ?? '')
      if (precoParaEnvio !== undefined) fd.append('preco', precoParaEnvio)
      fd.append('horario', editForm.horario ?? '')
      fd.append('descricao', editForm.descricao ?? '')
      fd.append('linkConteudo', editForm.linkConteudo ?? '')
      fd.append('contentClassification', editForm.contentClassification)

      // Localização: usa a do snapshot (backend exige).
      if (editSnapshot.cidadeId) fd.append('cidadeId', String(editSnapshot.cidadeId))
      if (editSnapshot.bairroId) fd.append('bairroId', String(editSnapshot.bairroId))

      ;(editForm.locaisAtendimento ?? []).forEach((v) => fd.append('locaisAtendimento', v))
      ;(editForm.servicos ?? []).forEach((v) => fd.append('servicos', v))

      // Snapshot coerente de mídia (não faz upload aqui).
      ;(editSnapshot.fotos ?? []).forEach((url) => fd.append('fotosExistentes', url))
      ;(editSnapshot.videosAnuncio ?? []).forEach((url) => fd.append('videosExistentes', url))

      const res = await fetch(`${api}/anuncios/staff/id/${idParam}/editar`, {
        method: 'PUT',
        credentials: 'include',
        body: fd,
      })
      if (!res.ok) throw new Error(await readApiError(res, 'Falha ao salvar alterações.'))

      toast.success('Alterações salvas (revisão atualizada).')
      await recarregarAnuncioERevisao()
      // Recarrega o snapshot do editor para refletir o que ficou pendente.
      await abrirEditor()
    } catch (e) {
      toast.error(e instanceof Error && e.message.trim() ? corrigirTextoCorrompido(e.message) : 'Erro ao salvar.')
    } finally {
      setEditSaving(false)
    }
  }, [idParam, editForm, editSnapshot, abrirEditor, recarregarAnuncioERevisao])

  useEffect(() => {
    if (!idParam) return

    const fetchAnuncio = async () => {
      try {
        setLoading(true)
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}`, {
          credentials: 'include',
          cache: 'no-store',
        })
        if (!res.ok) throw new Error('Falha ao buscar anúncio')

        const data = corrigirEstruturaTexto((await res.json()) as AnuncioStaffDetalhe)
        setAnuncio(data)
      } catch {
        toast.error('Erro ao carregar anúncio.')
      } finally {
        setLoading(false)
      }
    }

    fetchAnuncio()
  }, [idParam])

  useEffect(() => {
    if (!idParam) return

    const fetchRevision = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}/revision`, {
          credentials: 'include',
          cache: 'no-store',
        })

        if (res.status === 204) {
          setRevision(null)
          return
        }

        if (!res.ok) {
          setRevision(null)
          return
        }

        const data = corrigirEstruturaTexto((await res.json()) as AnuncioRevisionDetail)
        setRevision(data)
      } catch {
        setRevision(null)
      }
    }

    fetchRevision()
  }, [idParam])

  useEffect(() => {
    const handler = () => {
      void recarregarAnuncioERevisao()
    }
    window.addEventListener('admin-revisions-updated', handler)
    return () => window.removeEventListener('admin-revisions-updated', handler)
  }, [recarregarAnuncioERevisao])

  useEffect(() => {
    const classificationFromModeration =
      revision?.pendingContentClassification ?? anuncio?.contentClassification

    setSelectedClassification(normalizarClassificacaoModeracao(classificationFromModeration))
  }, [revision?.pendingContentClassification, anuncio?.contentClassification])

  useEffect(() => {
    setModerationFeedback(null)
  }, [selectedClassification, idParam])

  const getBadgeColor = (status?: string | null) => {
    switch (status) {
      case 'ATIVO':
        return 'bg-green-100 text-green-700 border-green-300'
      case 'PENDENTE':
        return 'bg-yellow-100 text-yellow-700 border-yellow-300'
      case 'REJEITADO':
        return 'bg-red-100 text-red-700 border-red-300'
      case 'PAUSADO':
        return 'bg-gray-100 text-gray-700 border-gray-300'
      default:
        return 'bg-gray-100 text-gray-600 border-gray-300'
    }
  }

  const formatBRL = (v?: number | null) => {
    if (typeof v !== 'number' || Number.isNaN(v)) return '-'
    return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
  }

  const texto = (value?: string | null, fallback = '-') => {
    const safe = corrigirTextoCorrompido(value ?? '').trim()
    return safe || fallback
  }

  const readApiError = async (response: Response, fallback: string) => {
    const raw = await response.text().catch(() => '')
    if (!raw.trim()) {
      return fallback
    }

    try {
      const parsed = corrigirEstruturaTexto(JSON.parse(raw)) as { error?: string; message?: string }
      const message =
        corrigirTextoCorrompido(
          String(parsed?.error || parsed?.message || '').trim()
        )
      return message || fallback
    } catch {
      const message = corrigirTextoCorrompido(raw).trim()
      return message || fallback
    }
  }

  const localLabel = useMemo(() => {
    if (!anuncio) return '-'

    const dtoLabel = corrigirTextoCorrompido(anuncio.localizacaoLabel ?? '').trim()
    if (dtoLabel) return dtoLabel

    const uf = corrigirTextoCorrompido(anuncio.estadoUf ?? '').trim()
    const cidade = corrigirTextoCorrompido(anuncio.cidadeNome ?? '').trim()
    const bairro = corrigirTextoCorrompido(anuncio.bairroNome ?? '').trim()

    const cidadeUf = cidade ? (uf ? `${cidade}/${uf}` : cidade) : ''
    const composed = [bairro, cidadeUf].filter(Boolean).join(' - ')
    if (composed) return composed

    const legacy = corrigirTextoCorrompido(anuncio.cidadeAnunciante ?? '').trim()
    return legacy || '-'
  }, [anuncio])

  const hasPendingRevision = Boolean(revision || anuncio?.pendingRevision)
  const isModerationPending = anuncio?.status === 'PENDENTE' || hasPendingRevision

  const excluirFotoPublicada = async (url: string) => {
    if (!idParam) return
    if (
      !window.confirm(
        "Remover esta foto publicada do anúncio? Somente este arquivo será excluído (não apaga o anúncio nem outras mídias)."
      )
    ) {
      return
    }
    try {
      setMediaActionBusy(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}/fotos/remover`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ fotosParaRemover: [url] }),
      })
      if (!res.ok) throw new Error(await readApiError(res, "Falha ao remover foto."))
      toast.success("Foto publicada removida.")
      await recarregarAnuncioERevisao()
    } catch (error) {
      toast.error(
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : "Erro ao remover foto."
      )
    } finally {
      setMediaActionBusy(false)
    }
  }

  const excluirVideoPublicado = async (url: string) => {
    if (!idParam) return
    if (
      !window.confirm(
        "Remover este vídeo publicado do anúncio? Somente este arquivo será excluído (não apaga o anúncio nem outras mídias)."
      )
    ) {
      return
    }
    try {
      setMediaActionBusy(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}/videos/remover`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ videosParaRemover: [url] }),
      })
      if (!res.ok) throw new Error(await readApiError(res, "Falha ao remover vídeo."))
      toast.success("Vídeo publicado removido.")
      await recarregarAnuncioERevisao()
    } catch (error) {
      toast.error(
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : "Erro ao remover vídeo."
      )
    } finally {
      setMediaActionBusy(false)
    }
  }

  const excluirMidiaPendenteRevisao = async (mediaItemId: number) => {
    if (!idParam) return
    if (
      !window.confirm(
        "Remover esta mídia da revisão pendente? Somente este item será excluído (não apaga o anúncio, não remove mídias publicadas nem outros itens da revisão)."
      )
    ) {
      return
    }
    try {
      setMediaActionBusy(true)
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}/revision/media/${mediaItemId}`,
        { method: "DELETE", credentials: "include" }
      )
      if (!res.ok) throw new Error(await readApiError(res, "Falha ao remover mídia da revisão."))
      toast.success("Mídia removida da revisão.")
      await recarregarAnuncioERevisao()
    } catch (error) {
      toast.error(
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : "Erro ao remover mídia da revisão."
      )
    } finally {
      setMediaActionBusy(false)
    }
  }
  const classificationValue = revision?.pendingContentClassification ?? anuncio?.contentClassification ?? null
  const classificationDefined = classificacaoEstaDefinida(classificationValue)
  const selectedClassificationLabel = formatarClassificacaoConteudo(selectedClassification)
  const selectedClassificationIsRestricted = selectedClassification !== 'SAFE_PUBLIC'
  const currentClassificationIsRestricted =
    classificationDefined && normalizarClassificacaoModeracao(classificationValue) !== 'SAFE_PUBLIC'

  /** Retorna true se navegou para outro anúncio da fila. */
  const tentarNavegarFila = () => {
    if (nextId) {
      router.push(`/admin/moderacao-v2/${nextId}`)
      return true
    }
    if (prevId) {
      router.push(`/admin/moderacao-v2/${prevId}`)
      return true
    }
    return false
  }

  const aprovarAnuncio = async () => {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/${idParam}/aprovar`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          classification: selectedClassification,
          reason: 'Classificação definida na tela única de moderação do anúncio.',
        }),
      })
      if (!res.ok) {
        throw new Error(await readApiError(res, 'Falha ao aprovar anúncio.'))
      }

      const payload = await res.json().then(corrigirEstruturaTexto).catch(() => anuncio)
      toast.success('Anúncio aprovado com sucesso!')
      setModerationFeedback(null)
      void enviarIndexNowNoCliente(
        montarUrlsIndexNowAnuncio({
          slug: payload?.slug,
          estadoUf: payload?.estadoUf,
          cidadeNome: payload?.cidadeNome,
          bairroNome: payload?.bairroNome,
        })
      )
      if (!tentarNavegarFila()) {
        await recarregarAnuncioERevisao()
      }
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : 'Falha ao aprovar anúncio.'
      setModerationFeedback({
        type: 'error',
        message,
      })
      toast.error(
        message
      )
    }
  }

  const reprovarAnuncio = async (motivo: string) => {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/${idParam}/rejeitar`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ justificativa: motivo }),
      })
      if (!res.ok) {
        throw new Error(await readApiError(res, 'Falha ao reprovar anúncio.'))
      }

      toast.success('Anúncio reprovado com sucesso!')
      if (!tentarNavegarFila()) {
        await recarregarAnuncioERevisao()
      }
    } catch (error) {
      toast.error(
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : 'Falha ao reprovar anúncio.'
      )
    }
  }

  const alterarStatusStaff = async (status: StaffStatus, justificativa?: string) => {
    try {
      setChangingStatus(true)

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ status, justificativa }),
      })
      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao alterar status.'))
      }

      const data = await res.json().then(corrigirEstruturaTexto)
      toast.success(`Status alterado para ${status}.`)
      setAnuncio((prev) => (prev ? { ...prev, status: data?.status ?? status } : prev))
      if (status === 'ATIVO') {
        void enviarIndexNowNoCliente(
          montarUrlsIndexNowAnuncio({
            slug: data?.slug,
            estadoUf: data?.estadoUf ?? anuncio?.estadoUf,
            cidadeNome: data?.cidadeNome ?? anuncio?.cidadeNome,
            bairroNome: data?.bairroNome ?? anuncio?.bairroNome,
          })
        )
      }
    } catch (error) {
      toast.error(
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : 'Erro ao alterar status.'
      )
    } finally {
      setChangingStatus(false)
    }
  }

  const excluirAnuncio = async () => {
    try {
      setDeleting(true)

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${idParam}`, {
        method: 'DELETE',
        credentials: 'include',
      })
      if (!res.ok) {
        const payload = await res
          .json()
          .then(corrigirEstruturaTexto)
          .catch(async () => {
            const text = await res.text().catch(() => '')
            return text ? { error: corrigirTextoCorrompido(text) } : null
          })

        const message =
          payload && typeof payload === 'object' && 'error' in payload
            ? corrigirTextoCorrompido(String(payload.error || ''))
            : ''

        throw new Error(message || 'Erro ao excluir anúncio')
      }

      toast.success('Anúncio excluído com sucesso.')
      setOpenDelete(false)
      router.push('/admin/moderacao-v2')
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? corrigirTextoCorrompido(error.message)
          : 'Falha ao excluir anúncio.'
      toast.error(message)
    } finally {
      setDeleting(false)
    }
  }


  if (loading) return <div className="py-10 text-center text-gray-500">Carregando...</div>
  if (!anuncio) return <div className="py-10 text-center text-gray-500">Anúncio não encontrado.</div>

  return (
    <section className="pb-10">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push('/admin/moderacao-v2')}
            className="text-gray-600 hover:bg-gray-100"
          >
            <ArrowLeftIcon className="h-5 w-5" />
          </Button>

          <div>
            <h1 className="text-2xl font-bold text-gray-800">Detalhes do anúncio</h1>
            <p className="text-sm text-gray-500">#{idParam}</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            disabled={!prevId}
            onClick={() => prevId && router.push(`/admin/moderacao-v2/${prevId}`)}
            className="border-gray-300 text-gray-700 hover:bg-gray-100"
          >
            <ArrowLeftIcon className="h-4 w-4" />
          </Button>

          <Button
            variant="outline"
            size="icon"
            disabled={!nextId}
            onClick={() => nextId && router.push(`/admin/moderacao-v2/${nextId}`)}
            className="border-gray-300 text-gray-700 hover:bg-gray-100"
          >
            <ArrowRightIcon className="h-4 w-4" />
          </Button>

          {(anuncio.status === 'PENDENTE' || hasPendingRevision) && (
            <>
              <ReprovarAnuncioDialog onConfirm={reprovarAnuncio} />

              <Button
                variant="outline"
                onClick={aprovarAnuncio}
                className="flex items-center gap-1 border-green-300 text-green-600 hover:bg-green-50"
              >
                <CheckCircleIcon className="h-4 w-4" />
                Aprovar
              </Button>
            </>
          )}

          {anuncio.status !== 'PENDENTE' && !hasPendingRevision && (
            <>
              {anuncio.status === 'ATIVO' ? (
                <Button
                  variant="outline"
                  disabled={changingStatus}
                  onClick={() => alterarStatusStaff('PAUSADO')}
                  className="flex items-center gap-1 border-gray-300 text-gray-700 hover:bg-gray-100"
                >
                  <PauseIcon className="h-4 w-4" />
                  Pausar
                </Button>
              ) : (
                <Button
                  variant="outline"
                  disabled={changingStatus}
                  onClick={() => alterarStatusStaff('ATIVO')}
                  className="flex items-center gap-1 border-green-300 text-green-600 hover:bg-green-50"
                >
                  <PlayIcon className="h-4 w-4" />
                  Ativar
                </Button>
              )}

              <Button
                variant="outline"
                disabled={changingStatus}
                onClick={() => {
                  setMotivoRejeicao('')
                  setOpenRejeitar(true)
                }}
                className="flex items-center gap-1 border-red-300 text-red-600 hover:bg-red-50"
              >
                <XCircleIcon className="h-4 w-4" />
                Rejeitar
              </Button>
            </>
          )}

          <Button
            variant="outline"
            onClick={() => router.push(`/admin/beneficios-premium?anuncioId=${idParam}`)}
            className="flex items-center gap-1 border-amber-300 text-amber-700 hover:bg-amber-50"
          >
            <SparklesIcon className="h-4 w-4" />
            Gerenciar benefícios
          </Button>

          <Button
            variant="outline"
            onClick={() => void abrirEditor()}
            className="flex items-center gap-1 border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
          >
            <PencilSquareIcon className="h-4 w-4" />
            Editar anúncio
          </Button>

          <Button
            variant="outline"
            onClick={() => setOpenDelete(true)}
            className="flex items-center gap-1 border-red-300 text-red-600 hover:bg-red-50"
          >
            <TrashIcon className="h-4 w-4" />
            Excluir
          </Button>
        </div>
      </div>

      {editOpen ? (
        <div className="mb-6 rounded-xl border border-pink-200 bg-pink-50/40 p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-gray-900">Editar anúncio (na própria tela)</h2>
              <p className="mt-1 text-xs text-gray-600">
                Alterações em anúncios ativos geram uma revisão. Este painel salva explicitamente e recarrega o detalhe e a revisão.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={cancelarEdicao} disabled={editSaving}>
                Cancelar edição
              </Button>
              <Button type="button" onClick={() => void salvarEdicao()} disabled={editSaving || editLoading || !editForm}>
                {editSaving ? 'Salvando…' : 'Salvar alterações'}
              </Button>
            </div>
          </div>

          {editLoading || !editForm ? (
            <div className="mt-4 rounded-lg border border-pink-200 bg-white p-4 text-sm text-gray-600">
              Carregando dados para edição…
            </div>
          ) : (
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <div className="space-y-3 rounded-lg border border-pink-200 bg-white p-4">
                <p className="text-sm font-semibold text-gray-900">Dados básicos</p>
                <label className="block">
                  <span className="text-xs font-medium text-gray-600">Título</span>
                  <input
                    className="mt-1 h-10 w-full rounded-md border border-gray-200 px-3 text-sm"
                    value={editForm.titulo}
                    onChange={(e) => setEditForm((p) => (p ? { ...p, titulo: e.target.value } : p))}
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-medium text-gray-600">Descrição</span>
                  <textarea
                    className="mt-1 min-h-[110px] w-full rounded-md border border-gray-200 px-3 py-2 text-sm"
                    value={editForm.descricao}
                    onChange={(e) => setEditForm((p) => (p ? { ...p, descricao: e.target.value } : p))}
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-medium text-gray-600">Link de conteúdo</span>
                  <input
                    className="mt-1 h-10 w-full rounded-md border border-gray-200 px-3 text-sm"
                    value={editForm.linkConteudo}
                    onChange={(e) => setEditForm((p) => (p ? { ...p, linkConteudo: e.target.value } : p))}
                  />
                </label>
              </div>

              <div className="space-y-3 rounded-lg border border-pink-200 bg-white p-4">
                <p className="text-sm font-semibold text-gray-900">Classificação de conteúdo (persistida)</p>
                <select
                  className="h-10 w-full rounded-md border border-gray-200 bg-white px-3 text-sm"
                  value={editForm.contentClassification}
                  onChange={(e) => setEditForm((p) => (p ? { ...p, contentClassification: e.target.value } : p))}
                >
                  {EDIT_CLASSIFICATION_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>

                <div className="rounded-md border border-gray-200 bg-gray-50 p-3 text-xs text-gray-600">
                  Esta classificação é salva no endpoint de edição staff e entra na revisão pendente (ou no anúncio se não estiver ativo).
                </div>

                <div className="space-y-2">
                  <p className="text-sm font-semibold text-gray-900">Remover fotos publicadas</p>
                  <p className="text-xs text-gray-600">
                    Use &quot;Apagar&quot; em cada item. Isso remove apenas a mídia publicada selecionada.
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {(anuncio?.fotosUrl ?? []).map((url, i) => (
                      <div key={`edit-foto-${i}-${url}`} className="flex flex-col gap-1 rounded-md border border-gray-200 bg-white p-1.5">
                        <button
                          type="button"
                          className="relative h-20 w-14 overflow-hidden rounded-md bg-gray-100"
                          onClick={() => setMidiaPreview({ url, label: `Foto publicada ${i + 1}`, video: false })}
                        >
                          <img src={url} alt="" className="h-full w-full object-cover" loading="lazy" />
                        </button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="h-7 text-[10px] text-red-700 hover:bg-red-50"
                          disabled={mediaActionBusy}
                          onClick={() => void excluirFotoPublicada(url)}
                        >
                          Apagar
                        </Button>
                      </div>
                    ))}
                    {!(anuncio?.fotosUrl ?? []).length ? (
                      <p className="text-xs text-gray-500">Nenhuma foto publicada.</p>
                    ) : null}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      ) : null}

      <div className="mb-6 rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <div className="grid gap-5 lg:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
          <div className="space-y-3">
            <div>
              <h2 className="text-base font-semibold text-gray-900">Classificação de conteúdo e aprovação</h2>
              <p className="text-sm text-gray-500">
                Esta tela é a fonte operacional da decisão. A classificação escolhida aqui é exatamente a enviada no clique de aprovar.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {classificationDefined ? (
                <Badge className="border border-pink-200 bg-pink-50 text-pink-700 hover:bg-pink-50">
                  Classificação atual: {formatarClassificacaoConteudo(classificationValue)}
                </Badge>
              ) : (
                <Badge className="border border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-50">
                  SEM CLASSIFICAÇÃO DEFINIDA
                </Badge>
              )}

              {currentClassificationIsRestricted ? (
                <Badge className="border border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-50">
                  CONTEÚDO RESTRITO
                </Badge>
              ) : null}

              {isModerationPending ? (
                <Badge className="border border-gray-200 bg-gray-50 text-gray-700 hover:bg-gray-50">
                  Seleção para aprovação: {selectedClassificationLabel}
                </Badge>
              ) : null}
            </div>

            <div className="grid gap-2 sm:grid-cols-3">
              {MODERATION_CLASSIFICATION_OPTIONS.map((option) => {
                const active = selectedClassification === option.value
                return (
                  <button
                    key={option.value}
                    type="button"
                    disabled={!isModerationPending}
                    onClick={() => setSelectedClassification(option.value)}
                    className={[
                      'rounded-2xl border px-4 py-3 text-left transition',
                      active
                        ? 'border-[#FC1EAD]/40 bg-[#FC1EAD]/10 shadow-sm'
                        : 'border-gray-200 bg-white hover:border-pink-200 hover:bg-pink-50/40',
                      !isModerationPending && 'cursor-not-allowed opacity-70',
                    ].join(' ')}
                    title={option.helper}
                  >
                    <p className="text-sm font-semibold text-gray-900">{option.label}</p>
                    <p className="mt-1 text-xs text-gray-500">{option.helper}</p>
                  </button>
                )
              })}
            </div>

            <div className="rounded-2xl border border-dashed border-gray-200 bg-gray-50/70 px-4 py-3 text-xs text-gray-600">
              {!classificationDefined
                ? 'Anúncios antigos sem classificação continuam públicos até classificação manual. O bloqueio só passa a valer quando você definir a classificação nesta tela.'
                : isModerationPending
                  ? 'Ao clicar em Aprovar, o backend usa exatamente esta seleção e aplica a decisão no anúncio.'
                  : 'Sem moderação pendente, esta área fica apenas para consulta do que já foi decidido.'}
            </div>
          </div>

          <div className="space-y-3 rounded-2xl border border-gray-100 bg-gray-50/70 p-4">
            <div>
              <p className="text-sm font-semibold text-gray-900">Validações da aprovação</p>
              <p className="mt-1 text-xs text-gray-500">
                O anúncio só é aprovado se a classificação escolhida nesta tela for aceita pelo backend.
              </p>
            </div>

            {selectedClassificationIsRestricted ? (
              <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-amber-800">
                Conteúdo restrito exige age gate no frontend e proteção de mídias no backend. A decisão de aprovação é sempre do anúncio.
              </div>
            ) : (
              <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm text-emerald-800">
                Conteúdo live pode seguir pela aprovação normal desta mesma tela.
              </div>
            )}

            {moderationFeedback ? (
              <div
                className={`rounded-xl border px-3 py-3 text-sm ${
                  moderationFeedback.type === 'error'
                    ? 'border-red-200 bg-red-50 text-red-700'
                    : 'border-sky-200 bg-sky-50 text-sky-700'
                }`}
              >
                {moderationFeedback.message}
              </div>
            ) : null}
          </div>
        </div>
      </div>

      {(hasPendingRevision || revision) && (
        <div className="mb-6 rounded-xl border-2 border-amber-300 bg-amber-50/90 p-5 shadow-sm">
          <div className="flex flex-col gap-2 border-b border-amber-200/80 pb-4">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-lg font-semibold text-amber-950">Remoderação pendente</h2>
              <Badge className="border border-amber-400 bg-amber-100 text-amber-900 hover:bg-amber-100">
                Em análise
              </Badge>
            </div>
            <p className="text-sm text-amber-900">
              {anuncio.status === "ATIVO"
                ? "O anúncio permanece ativo no ar até a decisão. A revisão contém um snapshot completo do que será publicado ao aprovar (texto, localização, classificação e mídias). As seções abaixo separam: referência do que já está publicado e itens adicionais ou alterações propostas na revisão."
                : "Este anúncio aguarda decisão de moderação. Revise os campos e as mídias da revisão antes de aprovar ou rejeitar."}
            </p>
            {revision ? (
              <p className="text-xs text-amber-800">
                Revisão #{revision.revisionId} • {texto(revision.status, "status")} • origem {texto(revision.source, "—")}{" "}
                {revision.submittedAt ? `• enviada em ${revision.submittedAt}` : ""} •{" "}
                {texto(revision.submittedByEmail, "remetente não informado")}
              </p>
            ) : anuncio.pendingRevision ? (
              <p className="text-xs font-medium text-amber-800">
                Carregando detalhes da revisão… Se não aparecer, atualize a página.
              </p>
            ) : null}
          </div>

          {revision?.changes && revision.changes.length > 0 ? (
            <div className="mt-4">
              <p className="mb-2 text-sm font-semibold text-amber-950">Alterações de texto / metadados</p>
              <div className="grid gap-3 md:grid-cols-2">
                {revision.changes.map((change) => (
                  <div key={change.field} className="rounded-lg border border-amber-200 bg-white p-3 shadow-sm">
                    <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">
                      {corrigirTextoCorrompido(change.label)}
                    </p>
                    <p className="mt-2 text-xs text-gray-500">Publicado hoje (atual)</p>
                    <p className="text-sm text-gray-800">{texto(change.currentValue)}</p>
                    <p className="mt-2 text-xs text-gray-500">Proposto na revisão (pendente)</p>
                    <p className="text-sm font-medium text-gray-900">{texto(change.pendingValue)}</p>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {revision &&
          (anuncio.fotosUrl?.length ||
            (anuncio.videosUrl ?? []).length ||
            revision.pendingMediaItems?.length ||
            revision.pendingFotos?.length ||
            revision.pendingVideos?.length) ? (
            <div className="mt-5 space-y-6">
              {(anuncio.fotosUrl?.length || (anuncio.videosUrl ?? []).length) && (
                <div className="rounded-xl border border-sky-200 bg-sky-50/80 p-4 shadow-sm">
                  <p className="text-sm font-semibold text-gray-900">
                    Mídias atuais do anúncio <span className="font-normal text-sky-800">(publicadas)</span>
                  </p>
                  <p className="mt-1 text-xs text-gray-600">
                    Já estão no ar. A exclusão aqui remove só o arquivo selecionado — não apaga o anúncio nem as
                    outras mídias.
                  </p>
                  <div className="mt-3 flex flex-wrap gap-3">
                    {(anuncio.fotosUrl?.length ? anuncio.fotosUrl : []).map((url, i) => (
                      <div
                        key={`atual-foto-${i}-${url}`}
                        className="flex flex-col gap-1 rounded-md border border-gray-200 bg-white p-1.5 shadow-sm"
                      >
                        <button
                          type="button"
                          className="relative h-20 w-14 overflow-hidden rounded-md bg-gray-100"
                          onClick={() =>
                            setMidiaPreview({ url, label: `Foto publicada ${i + 1}`, video: false })
                          }
                        >
                          <img src={url} alt="" className="h-full w-full object-cover" loading="lazy" />
                        </button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="h-7 text-[10px] text-red-700 hover:bg-red-50"
                          disabled={mediaActionBusy}
                          onClick={() => void excluirFotoPublicada(url)}
                        >
                          <TrashIcon className="mr-0.5 h-3 w-3" />
                          Apagar
                        </Button>
                      </div>
                    ))}
                    {(anuncio.videosUrl ?? []).map((url, i) => (
                      <div
                        key={`atual-vid-${i}-${url}`}
                        className="flex flex-col gap-1 rounded-md border border-gray-200 bg-white p-1.5 shadow-sm"
                      >
                        <div className="relative h-20 w-28 overflow-hidden rounded-md bg-black">
                          <video src={url} muted playsInline preload="metadata" className="h-full w-full object-cover" />
                          <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            className="absolute inset-x-1 bottom-1 h-6 text-[10px]"
                            onClick={() =>
                              setMidiaPreview({ url, label: `Vídeo publicado ${i + 1}`, video: true })
                            }
                          >
                            Abrir
                          </Button>
                        </div>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="h-7 text-[10px] text-red-700 hover:bg-red-50"
                          disabled={mediaActionBusy}
                          onClick={() => void excluirVideoPublicado(url)}
                        >
                          <TrashIcon className="mr-0.5 h-3 w-3" />
                          Apagar
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {(revision.pendingMediaItems?.length ||
                revision.pendingFotos?.length ||
                revision.pendingVideos?.length) && (
                <div className="rounded-xl border-2 border-amber-400 bg-white p-4 shadow-sm">
                  <p className="text-sm font-semibold text-amber-950">
                    Novas mídias na revisão <span className="font-normal text-amber-800">(pendentes)</span>
                  </p>
                  <p className="mt-1 text-xs text-amber-900">
                    Itens propostos nesta revisão. &quot;Referência publicada&quot; espelha uma mídia já ativa (não
                    duplica exclusão pela revisão). Demais itens podem ser removidos só deste pedido, sem apagar o
                    anúncio nem as mídias já publicadas.
                  </p>

                  {revision.pendingMediaItems && revision.pendingMediaItems.length > 0 ? (
                    <div className="mt-4 space-y-3">
                      {revision.pendingMediaItems.map((item, index) => {
                        const isVideo = item.mediaType === 'VIDEO'
                        const isRefPublished = item.sourceType === 'ACTIVE_PUBLIC_URL'
                        return (
                          <div
                            key={`pend-item-${item.id}`}
                            className="flex flex-col gap-2 rounded-lg border border-amber-200 bg-amber-50/50 p-3 sm:flex-row sm:items-start"
                          >
                            <div className="flex items-center gap-3">
                              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber-200 text-xs font-bold text-amber-950">
                                {index + 1}
                              </span>
                              {isVideo ? (
                                <div className="overflow-hidden rounded-md border border-amber-300 bg-black sm:w-64">
                                  <video
                                    src={item.url}
                                    controls
                                    playsInline
                                    preload="metadata"
                                    className="max-h-52 w-full object-contain"
                                  />
                                </div>
                              ) : (
                                <div className="relative h-28 w-20 shrink-0 overflow-hidden rounded-md border border-amber-300 bg-white">
                                  <img
                                    src={item.url}
                                    alt={`Pendente ${index + 1}`}
                                    className="h-full w-full object-cover"
                                    loading="lazy"
                                  />
                                </div>
                              )}
                            </div>
                            <div className="flex flex-1 flex-col gap-2">
                              <div className="flex flex-wrap items-center gap-2">
                                <Badge
                                  variant="outline"
                                  className="border-amber-400 bg-amber-100/80 text-[10px] text-amber-950"
                                >
                                  {isVideo ? 'Vídeo' : 'Foto'} pendente
                                </Badge>
                                {isRefPublished ? (
                                  <Badge
                                    variant="outline"
                                    className="border-sky-300 bg-sky-50 text-[10px] text-sky-900"
                                  >
                                    Referência publicada
                                  </Badge>
                                ) : null}
                              </div>
                              <div className="flex flex-wrap gap-2">
                                <Button
                                  type="button"
                                  variant="outline"
                                  size="sm"
                                  className="h-8 text-xs"
                                  onClick={() =>
                                    setMidiaPreview({
                                      url: item.url,
                                      label: `${isVideo ? 'Vídeo' : 'Foto'} pendente ${index + 1}`,
                                      video: isVideo,
                                    })
                                  }
                                >
                                  {isVideo ? 'Abrir em destaque' : 'Ampliar'}
                                </Button>
                                <Button
                                  type="button"
                                  variant="outline"
                                  size="sm"
                                  className="h-8 text-xs text-red-700 hover:bg-red-50"
                                  disabled={mediaActionBusy || !item.removable}
                                  title={
                                    item.removable
                                      ? 'Remove só este item da revisão aberta.'
                                      : 'Item espelha mídia já publicada; apague pela seção de mídias publicadas se necessário.'
                                  }
                                  onClick={() => void excluirMidiaPendenteRevisao(item.id)}
                                >
                                  <TrashIcon className="mr-1 h-3.5 w-3.5" />
                                  Excluir da revisão
                                </Button>
                              </div>
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  ) : (
                    <>
                      {revision.pendingFotos && revision.pendingFotos.length > 0 ? (
                        <div className="mt-4 space-y-3">
                          <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">
                            Fotos novas ({revision.pendingFotos.length})
                          </p>
                          <p className="text-xs text-amber-800">
                            Atualize a página após deploy do backend para habilitar exclusão individual (lista com
                            ids).
                          </p>
                          {revision.pendingFotos.map((fotoPendente, index) => (
                            <div
                              key={`pend-foto-${index}-${fotoPendente}`}
                              className="flex flex-col gap-2 rounded-lg border border-amber-200 bg-amber-50/50 p-3 sm:flex-row sm:items-center"
                            >
                              <div className="flex items-center gap-3">
                                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber-200 text-xs font-bold text-amber-950">
                                  {index + 1}
                                </span>
                                <div className="relative h-28 w-20 shrink-0 overflow-hidden rounded-md border border-amber-300 bg-white">
                                  <img
                                    src={fotoPendente}
                                    alt={`Nova foto ${index + 1}`}
                                    className="h-full w-full object-cover"
                                    loading="lazy"
                                  />
                                </div>
                              </div>
                              <div className="flex flex-1 flex-wrap gap-2">
                                <Button
                                  type="button"
                                  variant="outline"
                                  size="sm"
                                  className="h-8 text-xs"
                                  onClick={() =>
                                    setMidiaPreview({
                                      url: fotoPendente,
                                      label: `Nova foto pendente ${index + 1}`,
                                    })
                                  }
                                >
                                  Ampliar
                                </Button>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : null}
                      {revision.pendingVideos && revision.pendingVideos.length > 0 ? (
                        <div className="mt-4 space-y-3">
                          <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">
                            Vídeos novos ({revision.pendingVideos.length})
                          </p>
                          {revision.pendingVideos.map((vidPendente, index) => (
                            <div
                              key={`pend-vid-${index}-${vidPendente}`}
                              className="flex flex-col gap-2 rounded-lg border border-amber-200 bg-amber-50/50 p-3"
                            >
                              <div className="flex items-center gap-3">
                                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber-200 text-xs font-bold text-amber-950">
                                  {index + 1}
                                </span>
                                <p className="text-xs font-medium text-amber-900">Vídeo novo {index + 1}</p>
                              </div>
                              <div className="overflow-hidden rounded-md border border-amber-300 bg-black">
                                <video
                                  src={vidPendente}
                                  controls
                                  playsInline
                                  preload="metadata"
                                  className="max-h-52 w-full object-contain"
                                />
                              </div>
                              <div className="flex flex-wrap gap-2">
                                <Button
                                  type="button"
                                  variant="outline"
                                  size="sm"
                                  className="h-8 text-xs"
                                  onClick={() =>
                                    setMidiaPreview({
                                      url: vidPendente,
                                      label: `Novo vídeo pendente ${index + 1}`,
                                      video: true,
                                    })
                                  }
                                >
                                  Abrir em destaque
                                </Button>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : null}
                    </>
                  )}
                </div>
              )}
            </div>
          ) : null}
        </div>
      )}

      <Dialog open={Boolean(midiaPreview)} onOpenChange={(open) => !open && setMidiaPreview(null)}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{midiaPreview?.label}</DialogTitle>
            <DialogDescription>Visualização para análise na moderação.</DialogDescription>
          </DialogHeader>
          {midiaPreview?.video ? (
            <video
              src={midiaPreview.url}
              controls
              playsInline
              className="max-h-[70vh] w-full rounded-lg bg-black object-contain"
            />
          ) : midiaPreview ? (
            <img
              src={midiaPreview.url}
              alt={midiaPreview.label}
              className="max-h-[70vh] w-full rounded-lg object-contain"
            />
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setMidiaPreview(null)}>
              Fechar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <div className="mb-10 grid grid-cols-1 gap-6 md:grid-cols-2">
        <div className="flex min-h-[360px] flex-col justify-between rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div>
            <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
              <ClipboardDocumentListIcon className="h-5 w-5 text-[#C41E73]" />
              Informações do anúncio
            </h2>

            <div className="space-y-2 text-sm">
              <p>
                <span className="font-semibold">Título:</span> {texto(anuncio.titulo)}
              </p>
              <p>
                <span className="font-semibold">Usuário:</span> {texto(anuncio.username)}
              </p>
              <p>
                <span className="font-semibold">Nome real:</span> {texto(anuncio.nomeCompleto)}
              </p>
              <p>
                <span className="font-semibold">CPF:</span> {anuncio.cpf ? formatCPF(anuncio.cpf) : '-'}
              </p>
              <p>
                <span className="font-semibold">Categoria:</span>{' '}
                {normalizarCategoria(corrigirTextoCorrompido(anuncio.categoria ?? '')) || '-'}
              </p>
              <p>
                <span className="font-semibold">Local:</span> {texto(localLabel)}
              </p>
              <p>
                <span className="font-semibold">Preço:</span> {formatBRL(anuncio.preco)}
              </p>
              <p>
                <span className="font-semibold">Horário:</span>{' '}
                {normalizarHorario(corrigirTextoCorrompido(anuncio.horario ?? '-'))}
              </p>
              <p>
                <span className="font-semibold">Status:</span>{' '}
                <span className="inline-flex flex-wrap items-center gap-2">
                  <Badge className={`border px-2 py-1 text-[11px] ${getBadgeColor(anuncio.status)}`}>
                    {texto(anuncio.status)}
                  </Badge>
                  {hasPendingRevision ? (
                    <Badge className="border border-amber-200 bg-amber-50 px-2 py-1 text-[11px] text-amber-700 hover:bg-amber-50">
                      Revisão pendente
                    </Badge>
                  ) : null}
                </span>
              </p>
            </div>
          </div>
        </div>

        <div className="flex min-h-[360px] flex-col justify-between rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
          <div>
            <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
              <MapPinIcon className="h-5 w-5 text-[#C41E73]" />
              Localização
            </h2>

            <p className="mb-3 text-sm text-gray-600">{texto(localLabel, 'Não informada')}</p>
          </div>

          {localLabel !== '-' && (
            <div className="overflow-hidden rounded-lg border border-gray-200">
              <iframe
                src={`https://www.google.com/maps?q=${encodeURIComponent(localLabel)}&z=13&output=embed`}
                width="100%"
                height="220"
                loading="lazy"
                className="w-full rounded-lg"
              />
            </div>
          )}
        </div>
      </div>

      <div className="mb-10 rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
        <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
          <BuildingOffice2Icon className="h-5 w-5 text-[#C41E73]" />
          Locais de atendimento
        </h2>

        <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
          {[
            { label: 'A combinar', value: 'A_COMBINAR' },
            { label: 'Hotel/Motel', value: 'HOTEL_MOTEL' },
            { label: 'Meu local', value: 'MEU_LOCAL' },
          ].map((opt) => (
            <label key={opt.value} className="flex items-center gap-2 text-gray-700">
              <input
                type="checkbox"
                checked={(anuncio.locaisAtendimento ?? []).includes(opt.value)}
                readOnly
                className="cursor-not-allowed accent-pink-500"
              />
              {opt.label}
            </label>
          ))}
        </div>
      </div>

      <div className="mb-10 rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
        <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
          <TagIcon className="h-5 w-5 text-[#C41E73]" />
          Serviços oferecidos
        </h2>

        <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3 md:grid-cols-4">
          {servicosDisponiveis.map((s) => (
            <label key={s.value} className="flex items-center gap-2 text-gray-700">
              <input
                type="checkbox"
                checked={(anuncio.servicos ?? []).includes(s.value)}
                readOnly
                className="cursor-not-allowed accent-pink-500"
              />
              {s.label}
            </label>
          ))}
        </div>
      </div>

      <div className="mb-10 rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
        <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold text-gray-800">
          <TagIcon className="h-5 w-5 text-[#C41E73]" />
          Descrição
        </h2>

        <p className="whitespace-pre-line text-sm text-gray-700">{texto(anuncio.descricao)}</p>
      </div>

      <DocumentosUsuarioSection documentos={anuncio.documentosUsuario || []} />

      <AdminAnuncioStoriesSection anuncioId={anuncioId} />

      {!hasPendingRevision && (
        <FotosAnuncioSection
          anuncioId={anuncioId}
          fotos={anuncio.fotosUrl || []}
          onUpdated={() => void recarregarAnuncioERevisao()}
        />
      )}

      {(anuncio.videosUrl || []).length > 0 && (
        <div className="mb-10 rounded-xl border border-gray-100 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
            <h3 className="text-base font-semibold text-gray-800">Vídeos do anúncio</h3>
          </div>

          <div className="grid grid-cols-1 gap-4 p-6 sm:grid-cols-2">
            {(anuncio.videosUrl || []).map((url, index) => (
              <div
                key={`${url}-${index}`}
                className="flex flex-col gap-2 overflow-hidden rounded-xl border border-gray-200 bg-black/5 p-2"
              >
                <video
                  src={url}
                  controls
                  playsInline
                  preload="metadata"
                  className="h-64 w-full bg-black object-contain"
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="w-fit text-red-700 hover:bg-red-50"
                  disabled={mediaActionBusy}
                  onClick={() => void excluirVideoPublicado(url)}
                >
                  <TrashIcon className="mr-1 h-4 w-4" />
                  Apagar este vídeo
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}

      <Dialog open={openRejeitar} onOpenChange={setOpenRejeitar}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <XCircleIcon className="h-5 w-5 text-red-600" />
              Rejeitar anúncio
            </DialogTitle>
            <DialogDescription>
              Isso vai mudar o status para <strong>REJEITADO</strong>. Informe um motivo
              (recomendado).
            </DialogDescription>
          </DialogHeader>

          <textarea
            value={motivoRejeicao}
            onChange={(e) => setMotivoRejeicao(e.target.value)}
            placeholder="Motivo da rejeição..."
            className="min-h-[110px] w-full rounded-md border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#FC1EAD]/30"
          />

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" onClick={() => setOpenRejeitar(false)} disabled={changingStatus}>
              Cancelar
            </Button>

            <Button
              variant="destructive"
              onClick={async () => {
                await alterarStatusStaff('REJEITADO', motivoRejeicao)
                setOpenRejeitar(false)
              }}
              disabled={changingStatus}
              className="flex items-center gap-1"
            >
              <XCircleIcon className="h-4 w-4" />
              {changingStatus ? 'Rejeitando...' : 'Rejeitar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={openDelete} onOpenChange={setOpenDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ExclamationTriangleIcon className="h-5 w-5 text-red-600" />
              Confirmar exclusão
            </DialogTitle>

            <DialogDescription>
              Esta ação é permanente. O anúncio e suas mídias serão removidos.
            </DialogDescription>
          </DialogHeader>

          <div className="text-sm text-gray-700">
            Tem certeza que deseja excluir o anúncio
            {anuncio?.titulo ? (
              <strong>
                &quot;{texto(anuncio.titulo, '')}&quot;
              </strong>
            ) : (
              ' selecionado'
            )}
            ?
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" onClick={() => setOpenDelete(false)} disabled={deleting}>
              Cancelar
            </Button>

            <Button
              variant="destructive"
              onClick={excluirAnuncio}
              disabled={deleting}
              className="flex items-center gap-1"
            >
              <TrashIcon className="h-4 w-4" />
              {deleting ? 'Excluindo...' : 'Excluir'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
