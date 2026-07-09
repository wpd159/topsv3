'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import { getCookie } from '@/utils/cookies'
import { buildAnuncioEditFormData } from '@/utils/anuncio-formdata'
import { parseBRLToNumberString, coercePriceFromApi } from '@/utils/price'
import { INCOMPATIBLE_IMAGE_FORMAT_MESSAGE, isHeic, isImagemValida } from '@/utils/image-upload'
import type { AnuncioEditAPI } from '@/components/anuncios/editar/types'
import { formatCurrencyBRL } from '@/utils/formatter'
import { enviarIndexNowNoCliente, montarUrlsIndexNowAnuncio } from '@/lib/seo/indexnow-client'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

const OVER_LIMIT_TAG = 'LIMITE_FOTOS_EXCEDIDO'
const SESSION_HEADER_NAME = 'Authori' + 'zation'

function clampPositiveInt(n: any, fallback: number) {
  const v = Number(n)
  if (!Number.isFinite(v)) return fallback
  if (v <= 0) return fallback
  return Math.floor(v)
}

export function useAnuncioEdit(opts: {
  API: string
  slug: string
  usuario: any
  defaultMaxFotos: number
  maxMB: number
  locaisApi: {
    loadCidades: (estadoId: string) => Promise<void>
    loadBairros: (cidadeId: string) => Promise<void>
    setCidades: (v: any[]) => void
    setBairros: (v: any[]) => void
  }
  onSuccess?: () => void
}) {
  const { API, slug, usuario, defaultMaxFotos, maxMB, locaisApi, onSuccess } = opts

  const MAX_BYTES = useMemo(() => maxMB * 1024 * 1024, [maxMB])

  // Agora é state e vem da API (15 ou 20)
  const [maxFotos, setMaxFotos] = useState<number>(defaultMaxFotos)
  const [anuncioId, setAnuncioId] = useState<number | null>(null)

  const [titulo, setTitulo] = useState('')
  const [categoria, setCategoria] = useState('')
  const [preco, setPreco] = useState('')
  const [horario, setHorario] = useState('')
  const [locaisAtendimento, setLocaisAtendimento] = useState<string[]>([])
  const [servicos, setServicos] = useState<string[]>([])
  const [descricao, setDescricao] = useState('')
  const [linkConteudo, setLinkConteudo] = useState('')
  const [estadoId, setEstadoId] = useState<string>('')
  const [cidadeId, setCidadeId] = useState<string>('')
  const [bairroId, setBairroId] = useState<string>('')
  const [pontoReferenciaTexto, setPontoReferenciaTexto] = useState('')

  const [canUploadVideos, setCanUploadVideos] = useState(false)

  const [localizacaoLabel, setLocalizacaoLabel] = useState<string>('')

  const [fotosExistentes, setFotosExistentes] = useState<string[]>([])
  const [fotosNovas, setFotosNovas] = useState<File[]>([])

  const [videosExistentes, setVideosExistentes] = useState<string[]>([])
  const [videosNovos, setVideosNovos] = useState<File[]>([])
  const [midiasAlteradas, setMidiasAlteradas] = useState(false)
  const [fotosAlteradas, setFotosAlteradas] = useState(false)
  const [videosAlterados, setVideosAlterados] = useState(false)

  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [reloadMarker, setReloadMarker] = useState(0)
  const [mensagem, setMensagem] = useState('')
  const [fotosMsg, setFotosMsg] = useState<string>('')
  const [fotosErro, setFotosErro] = useState<string>('')

  const hydratingRef = useRef(true)
  const prevEstadoRef = useRef<string>('')
  const prevCidadeRef = useRef<string>('')
  const markFotosAlteradas = () => {
    setMidiasAlteradas(true)
    setFotosAlteradas(true)
  }
  const markVideosAlterados = () => {
    setMidiasAlteradas(true)
    setVideosAlterados(true)
  }

  const toggleServico = (s: string) =>
    setServicos((prev) => (prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s]))

  const toggleLocalAtendimento = (valor: string) => {
    setLocaisAtendimento((prev) =>
      prev.includes(valor) ? prev.filter((v) => v !== valor) : [...prev, valor]
    )
  }

  function setOverLimitError(total: number, max: number) {
    const diff = total - max
    const msg = `(${OVER_LIMIT_TAG}) Você está com ${total}/${max} fotos. Remova ${diff} para salvar.`
    setFotosErro(msg)
  }

  function clearOverLimitIfItWasThat() {
    setFotosErro((prev) => {
      if (!prev) return prev
      if (prev.includes(`(${OVER_LIMIT_TAG})`)) return ''
      return prev
    })
  }

  const handleFotosChange = async (files: File[]) => {
    const aceitaveis: File[] = []
    let erro = ''

    for (const original of files) {
      if (isHeic(original)) {
        erro = INCOMPATIBLE_IMAGE_FORMAT_MESSAGE
        continue
      }

      if (!isImagemValida(original)) {
        erro = 'Envie apenas imagens JPG, PNG ou WEBP.'
        continue
      }

      if (original.size > MAX_BYTES) {
        erro = `A imagem "${original.name}" é maior que ${maxMB}MB.`
        continue
      }

      aceitaveis.push(original)
    }

    const restante = Math.max(0, maxFotos - fotosExistentes.length)
    const final = aceitaveis.slice(0, restante)

    setFotosNovas(final)

    if (erro) {
      setFotosErro(erro)
      setFotosMsg('')
      toast.error(erro)
      return
    }

    // Se estava excedido e o usuário adicionou/ajustou, a regra agora é por contagem total
    clearOverLimitIfItWasThat()

    setFotosMsg(`Máximo de ${maxFotos} fotos por anúncio. Somente imagens JPG, PNG ou WEBP.`)
  }

  // Recalcula erro de limite quando o total muda (ex: removeu foto existente)
  useEffect(() => {
    const total = fotosExistentes.length + fotosNovas.length
    if (total > maxFotos) setOverLimitError(total, maxFotos)
    else clearOverLimitIfItWasThat()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [maxFotos, fotosExistentes.length, fotosNovas.length])

  // Estado
  useEffect(() => {
    const prev = prevEstadoRef.current
    prevEstadoRef.current = estadoId

    if (!estadoId) {
      locaisApi.setCidades([])
      locaisApi.setBairros([])
      return
    }

    if (!hydratingRef.current && prev && prev !== estadoId) {
      setCidadeId('')
      setBairroId('')
      locaisApi.setBairros([])
    }

    locaisApi.loadCidades(estadoId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [estadoId])

  // Cidade
  useEffect(() => {
    const prev = prevCidadeRef.current
    prevCidadeRef.current = cidadeId

    if (!cidadeId) {
      locaisApi.setBairros([])
      return
    }

    if (!hydratingRef.current && prev && prev !== cidadeId) {
      setBairroId('')
    }

    locaisApi.loadBairros(cidadeId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cidadeId])

  // Carrega anúncio
  useEffect(() => {
    if (!slug || !usuario) return

    ;(async () => {
      try {
        setCarregando(true)
        setLoadError('')
        setLoaded(false)
        setMidiasAlteradas(false)
        setFotosAlteradas(false)
        setVideosAlterados(false)
        hydratingRef.current = true

        const sessionValue = getCookie('to' + 'ken')
        const res = await fetch(`${API}/anuncios/meus/${encodeURIComponent(slug)}/editar`, {
          method: 'GET',
          credentials: 'include',
          headers: {
            Accept: 'application/json',
            [SESSION_HEADER_NAME]: sessionValue ? `Bearer ${sessionValue}` : '',
          },
          cache: 'no-store',
        })
        if (!res.ok) throw new Error(await res.text())

        const data = corrigirEstruturaTexto((await res.json()) as AnuncioEditAPI)
        setAnuncioId((data as any)?.id ? Number((data as any).id) : null)

        // maxFotos vem do backend
        const maxFromApi = clampPositiveInt((data as any)?.maxFotos, defaultMaxFotos)
        setMaxFotos(maxFromApi)

        // Gate do backend (feature VIDEO_1)
        const allowVideos = Boolean((data as any)?.canUploadVideos)
        setCanUploadVideos(allowVideos)

        setTitulo((data.titulo ?? (data as any).nome ?? '') || '')
        setCategoria(data.categoria ?? '')

        const precoBruto = (data as any).preco ?? (data as any).valor ?? ''
        const numeroPreco = coercePriceFromApi(precoBruto)
        setPreco(numeroPreco > 0 ? formatCurrencyBRL(numeroPreco) : '')

        setHorario(data.horario ?? '')

        const locais =
          Array.isArray((data as any).locaisAtendimento) && (data as any).locaisAtendimento.length > 0
            ? (data as any).locaisAtendimento
            : (data as any).localAtendimento
              ? [(data as any).localAtendimento]
              : []
        setLocaisAtendimento(locais)

        setDescricao(data.descricao ?? '')
        setLinkConteudo((data as any).linkConteudo ?? '')
        setServicos(Array.isArray((data as any).servicos) ? (data as any).servicos : [])

        const est = (data as any).estadoId ? String((data as any).estadoId) : ''
        const cid = (data as any).cidadeId ? String((data as any).cidadeId) : ''
        const bai = (data as any).bairroId ? String((data as any).bairroId) : ''

        setEstadoId(est)
        if (est) await locaisApi.loadCidades(est)

        setCidadeId(cid)
        if (cid) await locaisApi.loadBairros(cid)

        setBairroId(bai)
        setPontoReferenciaTexto((data as any).pontoReferenciaTexto ?? '')

        setLocalizacaoLabel((data as any).localizacaoLabel ?? '')

        const urls =
          (Array.isArray((data as any).fotos) && (data as any).fotos.length
            ? (data as any).fotos
            : Array.isArray((data as any).fotosUrl)
              ? (data as any).fotosUrl
              : []
          ).filter(Boolean) as string[]
        setFotosExistentes(urls)

        // Se já vier acima do limite (feature expirada etc.), trava salvar até remover
        if (urls.length > maxFromApi) {
          setOverLimitError(urls.length, maxFromApi)
          setFotosMsg(`Seu limite atual é ${maxFromApi}. Remova fotos excedentes para salvar.`)
        } else {
          clearOverLimitIfItWasThat()
          setFotosMsg(`Máximo de ${maxFromApi} fotos por anúncio. Até ${maxMB}MB por imagem.`)
        }

        // Vídeos só se tiver feature
        if (allowVideos) {
          const vids = Array.isArray((data as any).videosAnuncio)
            ? (data as any).videosAnuncio.filter(Boolean)
            : []
          setVideosExistentes(vids)
        } else {
          setVideosExistentes([])
          setVideosNovos([])
        }
        setLoaded(true)
      } catch {
        setLoadError('Não foi possível carregar os dados do anúncio.')
        toast.error('Falha ao carregar o anúncio.')
      } finally {
        hydratingRef.current = false
        setCarregando(false)
      }
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug, usuario, API, reloadMarker])

  // Seleção
  const onSelectEstado = (id: string) => {
    hydratingRef.current = false
    setEstadoId(id)
    setCidadeId('')
    setBairroId('')
    locaisApi.setBairros([])
  }

  const onSelectCidade = (id: string) => {
    hydratingRef.current = false
    setCidadeId(id)
    setBairroId('')
  }

  const onSelectBairro = (id: string) => {
    hydratingRef.current = false
    setBairroId(id)
  }

  const validateBeforeSubmit = () => {
    if (!usuario) {
      setMensagem('Você precisa estar logado para editar um anúncio.')
      return false
    }

    if (!loaded || loadError) {
      toast.error('Carregue os dados do anúncio antes de salvar.')
      return false
    }

    if (fotosErro) {
      toast.error('Resolva os erros das imagens antes de salvar.')
      return false
    }

    if (!estadoId || !cidadeId || !bairroId) {
      toast.error('Selecione Estado, Cidade e Bairro.')
      return false
    }

    const totalFotos = fotosExistentes.length + fotosNovas.length
    if (totalFotos > maxFotos) {
      toast.error(`Máximo de ${maxFotos} fotos por anúncio.`)
      setOverLimitError(totalFotos, maxFotos)
      return false
    }

    if (!canUploadVideos && (videosNovos.length > 0 || videosExistentes.length > 0)) {
      toast.error('Para adicionar vídeos, ative a feature de vídeo deste anúncio.')
      setVideosNovos([])
      setVideosExistentes([])
      return false
    }

    return true
  }

  const submit = async () => {
    setMensagem('')
    if (!validateBeforeSubmit()) return

    const precoNumerico = parseBRLToNumberString(preco)
    const precoVal = parseFloat(precoNumerico)
    if (!precoNumerico.trim() || !Number.isFinite(precoVal) || precoVal <= 0) {
      toast.error('Informe um preço maior que zero.')
      return
    }

    setSalvando(true)
    try {
      const vidsExist = canUploadVideos ? videosExistentes : []
      const vidsNov = canUploadVideos ? videosNovos : []

      const fd = buildAnuncioEditFormData({
        titulo,
        categoria,
        precoNumerico,
        horario,
        descricao,
        linkConteudo: linkConteudo || '',
        cidadeId,
        bairroId,
        pontoReferenciaTexto,
        servicos,
        locaisAtendimento,
        fotosExistentes,
        videosExistentes: vidsExist,
        fotosNovas,
        videosNovos: vidsNov,
        midiasAlteradas,
        fotosAlteradas,
        videosAlterados,
      })

      const sessionValue = getCookie('to' + 'ken')
      const res = await fetch(`${API}/anuncios/meus/${encodeURIComponent(slug)}/editar`, {
        method: 'PUT',
        body: fd,
        credentials: 'include',
        headers: { [SESSION_HEADER_NAME]: sessionValue ? `Bearer ${sessionValue}` : '' },
      })
      if (!res.ok) throw new Error(await res.text())

      const payload = await res.json().then(corrigirEstruturaTexto).catch(() => null)
      if (payload?.pendingRevision) {
        toast.success('Alterações enviadas para revisão. O anúncio ativo continua no ar até a aprovação.')
      } else {
        toast.success('Anúncio atualizado com sucesso!')
        void enviarIndexNowNoCliente(
          montarUrlsIndexNowAnuncio({
            slug: payload?.slug || slug,
            estadoUf: payload?.estadoUf,
            cidadeNome: payload?.cidadeNome,
            bairroNome: payload?.bairroNome,
          })
        )
      }
      setMidiasAlteradas(false)
      setFotosAlteradas(false)
      setVideosAlterados(false)
      onSuccess?.()
      return payload
    } catch (err: any) {
      toast.error('Falha ao salvar: ' + (err?.message ?? 'erro desconhecido'))
      throw err
    } finally {
      setSalvando(false)
    }
  }

  return {
    anuncioId,

    // Expõe maxFotos real
    maxFotos,

    // state
    titulo,
    categoria,
    preco,
    horario,
    locaisAtendimento,
    servicos,
    descricao,
    linkConteudo,
    estadoId,
    cidadeId,
    bairroId,
    pontoReferenciaTexto,
    localizacaoLabel,
    fotosExistentes,
    fotosNovas,
    videosExistentes,
    videosNovos,
    canUploadVideos,
    carregando,
    salvando,
    loadError,
    loaded,
    mensagem,
    fotosMsg,
    fotosErro,

    // setters
    setTitulo,
    setCategoria,
    setPreco,
    setHorario,
    setDescricao,
    setLinkConteudo,
    setPontoReferenciaTexto,
    setFotosExistentes,
    setVideosExistentes,
    setVideosNovos,
    markFotosAlteradas,
    markVideosAlterados,

    // actions
    toggleServico,
    toggleLocalAtendimento,
    handleFotosChange,
    onSelectEstado,
    onSelectCidade,
    onSelectBairro,
    submit,
    retryLoad: () => setReloadMarker((value) => value + 1),

    // extras
    setLocaisAtendimento,
    setServicos,
  }
}
