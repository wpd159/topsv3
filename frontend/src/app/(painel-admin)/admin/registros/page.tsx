'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { getAdminSession } from '@/lib/admin-auth-api'
import {
  baixarMidiaPublicidade,
  baixarMidiaStory,
  detalharRegistroPublicidade,
  detalharRegistroStory,
  exportarRegistroPublicidade,
  exportarRegistroStory,
  FINALIDADES_ACESSO_ARQUIVO,
  listarRegistrosPublicidade,
  listarRegistrosStory,
  type FinalidadeAcessoArquivo,
  type PublicidadeRegistroDetalhe,
  type PublicidadeRegistrosPagina,
  type StoryRegistroDetalhe,
  type StoryRegistrosPagina,
} from '@/lib/admin-registros-api'

const TRILHAS = [
  { href: '/admin/anuncios', titulo: 'Anúncios', descricao: 'Histórico administrativo no detalhe de cada anúncio.' },
  { href: '/admin/usuarios', titulo: 'Usuários', descricao: 'Histórico administrativo no detalhe de cada conta.' },
  { href: '/admin/creditos', titulo: 'Créditos e benefícios', descricao: 'Auditoria operacional financeira.' },
  { href: '/admin/compliance#visitor-logs', titulo: 'Visitantes', descricao: 'Eventos de verificação etária, em área separada.' },
] as const

function dataHora(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

function tituloVersao(conteudo: unknown) {
  if (!conteudo || typeof conteudo !== 'object' || !('titulo' in conteudo)) return 'Conteúdo capturado'
  return typeof conteudo.titulo === 'string' && conteudo.titulo.trim()
    ? conteudo.titulo
    : 'Conteúdo capturado'
}

function slugVersao(conteudo: unknown) {
  if (!conteudo || typeof conteudo !== 'object' || !('slug' in conteudo)) return null
  return typeof conteudo.slug === 'string' && conteudo.slug.trim() ? conteudo.slug : null
}

function extensaoMidia(mimeType: string) {
  switch (mimeType) {
    case 'image/jpeg': return 'jpg'
    case 'image/png': return 'png'
    case 'image/webp': return 'webp'
    case 'video/mp4': return 'mp4'
    case 'video/webm': return 'webm'
    default: return 'bin'
  }
}

function salvarArquivo(blob: Blob, nome: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = nome
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

function StoryArchiveSection({ finalidade, podeExportar }: { finalidade: FinalidadeAcessoArquivo | ''; podeExportar: boolean }) {
  const [page, setPage] = useState(0)
  const [tentativa, setTentativa] = useState(0)
  const [pagina, setPagina] = useState<StoryRegistrosPagina | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<unknown>(null)
  const [selecionadoId, setSelecionadoId] = useState<string | null>(null)
  const [detalhe, setDetalhe] = useState<StoryRegistroDetalhe | null>(null)
  const [carregandoDetalhe, setCarregandoDetalhe] = useState(false)
  const [erroDetalhe, setErroDetalhe] = useState<unknown>(null)
  const [exportando, setExportando] = useState(false)
  const [baixandoMidiaId, setBaixandoMidiaId] = useState<string | null>(null)
  const [erroArquivo, setErroArquivo] = useState<unknown>(null)

  useEffect(() => {
    setSelecionadoId(null)
    setPage(0)
  }, [finalidade])

  useEffect(() => {
    if (!finalidade) {
      setPagina(null)
      setErro(null)
      setCarregando(false)
      return
    }
    const controller = new AbortController()
    setCarregando(true)
    setErro(null)
    setPagina(null)
    void listarRegistrosStory(page, finalidade, controller.signal)
      .then(setPagina)
      .catch((nextError) => {
        if (!controller.signal.aborted) setErro(nextError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [page, finalidade, tentativa])

  useEffect(() => {
    if (!selecionadoId || !finalidade) {
      setDetalhe(null)
      setErroDetalhe(null)
      return
    }
    const controller = new AbortController()
    setDetalhe(null)
    setErroDetalhe(null)
    setErroArquivo(null)
    setCarregandoDetalhe(true)
    void detalharRegistroStory(selecionadoId, finalidade, controller.signal)
      .then(setDetalhe)
      .catch((nextError) => {
        if (!controller.signal.aborted) setErroDetalhe(nextError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregandoDetalhe(false)
      })
    return () => controller.abort()
  }, [selecionadoId, finalidade])

  const exportar = async (id: string) => {
    if (!finalidade || !podeExportar || exportando) return
    setExportando(true)
    setErroArquivo(null)
    try {
      salvarArquivo(await exportarRegistroStory(id, finalidade), `registro-publicidade-story-${id}.json`)
    } catch (nextError) {
      setErroArquivo(nextError)
    } finally {
      setExportando(false)
    }
  }

  const baixarMidia = async (registroId: string, midiaId: string, mimeType: string) => {
    if (!finalidade || !podeExportar || baixandoMidiaId) return
    setBaixandoMidiaId(midiaId)
    setErroArquivo(null)
    try {
      salvarArquivo(await baixarMidiaStory(registroId, midiaId, finalidade),
        `publicidade-story-midia-${midiaId}.${extensaoMidia(mimeType)}`)
    } catch (nextError) {
      setErroArquivo(nextError)
    } finally {
      setBaixandoMidiaId(null)
    }
  }

  return (
    <section className="rounded-lg border bg-white p-4" aria-labelledby="arquivo-story-title">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="arquivo-story-title" className="text-lg font-semibold">Stories comerciais preservados</h2>
          <p className="mt-1 text-sm text-gray-600">Consulta privada apenas de Stories capturados prospectivamente.</p>
        </div>
        <Button type="button" variant="outline" disabled={!finalidade || carregando} onClick={() => setTentativa((value) => value + 1)}>Atualizar Stories</Button>
      </div>
      {!finalidade ? <p className="mt-3 text-sm text-gray-600">Selecione a finalidade do acesso acima para consultar.</p> : null}
      {carregando ? <p className="mt-3 text-sm text-gray-600" role="status">Carregando Stories...</p> : null}
      {erro ? <div className="mt-3"><ContractState error={erro} onRetry={() => setTentativa((value) => value + 1)} /></div> : null}
      {pagina ? (
        <>
          <div className="mt-4 overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader><TableRow>
                <TableHead>Story</TableHead><TableHead>Anúncio</TableHead><TableHead>Conteúdo</TableHead>
                <TableHead>Modo</TableHead><TableHead>Estado</TableHead><TableHead>Início</TableHead>
                <TableHead>Fim</TableHead><TableHead>Ação</TableHead>
              </TableRow></TableHeader>
              <TableBody>
                {pagina.itens.length === 0 ? <TableRow><TableCell colSpan={8}>Nenhum Story preservado nesta página.</TableCell></TableRow> : null}
                {pagina.itens.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-mono text-xs">{item.storyId}</TableCell>
                    <TableCell className="font-mono text-xs">{item.anuncioId || '—'}</TableCell>
                    <TableCell>{item.titulo || '—'}</TableCell><TableCell>{item.modoConteudo}</TableCell>
                    <TableCell>{item.status}</TableCell><TableCell>{dataHora(item.inicioEm)}</TableCell>
                    <TableCell>{dataHora(item.fimEm)}</TableCell>
                    <TableCell><Button type="button" size="sm" variant="outline" onClick={() => setSelecionadoId(item.id)}>Consultar</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <nav className="mt-4 flex flex-wrap items-center justify-between gap-3" aria-label="Paginação do arquivo de Stories">
            <p className="text-sm text-gray-600">Página {pagina.page + 1} de {Math.max(1, pagina.totalPages)} · {pagina.totalElements} registro(s)</p>
            <div className="flex gap-2">
              <Button type="button" variant="outline" disabled={page === 0} onClick={() => { setSelecionadoId(null); setPage((value) => value - 1) }}>Anterior</Button>
              <Button type="button" variant="outline" disabled={pagina.last} onClick={() => { setSelecionadoId(null); setPage((value) => value + 1) }}>Próxima</Button>
            </div>
          </nav>
        </>
      ) : null}
      {selecionadoId ? (
        <section className="mt-5 rounded-lg border border-gray-200 bg-gray-50 p-4" aria-labelledby="detalhe-story-title">
          <div className="flex items-center justify-between gap-2">
            <h3 id="detalhe-story-title" className="font-semibold">Detalhe do Story preservado</h3>
            <Button type="button" size="sm" variant="outline" onClick={() => setSelecionadoId(null)}>Fechar</Button>
          </div>
          {carregandoDetalhe ? <p className="mt-3 text-sm" role="status">Carregando detalhe...</p> : null}
          {erroDetalhe ? <div className="mt-3"><ContractState error={erroDetalhe} /></div> : null}
          {detalhe ? (
            <>
              <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                <div><dt className="text-gray-500">Story</dt><dd className="break-all font-mono text-xs">{detalhe.storyId}</dd></div>
                <div><dt className="text-gray-500">Anúncio</dt><dd className="break-all font-mono text-xs">{detalhe.anuncioId || '—'}</dd></div>
                <div><dt className="text-gray-500">Modo</dt><dd>{detalhe.modoConteudo}</dd></div>
                <div><dt className="text-gray-500">Natureza</dt><dd>{detalhe.natureza}</dd></div>
                <div><dt className="text-gray-500">Cobertura</dt><dd>{detalhe.cobertura}</dd></div>
                <div><dt className="text-gray-500">Período</dt><dd>{dataHora(detalhe.inicioEm)} até {dataHora(detalhe.fimEm)}</dd></div>
                <div><dt className="text-gray-500">Guarda mínima até</dt><dd>{dataHora(detalhe.retencaoAte)}</dd></div>
                <div><dt className="text-gray-500">Encerramento</dt><dd>{detalhe.encerramentoMotivo}</dd></div>
              </dl>
              <h4 className="mt-5 font-semibold">Versões preservadas ({detalhe.versoes.length})</h4>
              <ol className="mt-2 space-y-2">
                {detalhe.versoes.map((versao) => (
                  <li key={versao.id} className="rounded-md border bg-white p-3 text-sm">
                    <p className="font-medium">Versão {versao.numero}: {tituloVersao(versao.conteudo)}</p>
                    <p className="mt-1 text-gray-600">{dataHora(versao.vigenteDesde)} até {dataHora(versao.vigenteAte)} · {versao.motivo} · {versao.midias.length} mídia(s)</p>
                    {podeExportar && versao.midias.length > 0 ? <div className="mt-2 flex flex-wrap gap-2">{versao.midias.map((midia) => (
                      <Button key={midia.id} type="button" size="sm" variant="outline" disabled={baixandoMidiaId !== null} onClick={() => void baixarMidia(detalhe.id, midia.id, midia.mimeType)}>
                        {baixandoMidiaId === midia.id ? 'Baixando mídia...' : `Baixar mídia ${midia.variante} (${midia.ordem})`}
                      </Button>
                    ))}</div> : null}
                  </li>
                ))}
              </ol>
              {podeExportar ? <Button className="mt-4" type="button" variant="outline" disabled={exportando} onClick={() => void exportar(detalhe.id)}>{exportando ? 'Preparando exportação...' : 'Exportar JSON deste Story'}</Button> : null}
              {erroArquivo ? <div className="mt-3"><ContractState error={erroArquivo} /></div> : null}
            </>
          ) : null}
        </section>
      ) : null}
    </section>
  )
}

export default function AdminRegistrosPage() {
  const [podeExportar, setPodeExportar] = useState(false)
  const [page, setPage] = useState(0)
  const [pagina, setPagina] = useState<PublicidadeRegistrosPagina | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<unknown>(null)
  const [tentativa, setTentativa] = useState(0)
  const [selecionadoId, setSelecionadoId] = useState<string | null>(null)
  const [finalidade, setFinalidade] = useState<FinalidadeAcessoArquivo | ''>('')
  const [detalhe, setDetalhe] = useState<PublicidadeRegistroDetalhe | null>(null)
  const [carregandoDetalhe, setCarregandoDetalhe] = useState(false)
  const [erroDetalhe, setErroDetalhe] = useState<unknown>(null)
  const [erroExportacao, setErroExportacao] = useState<unknown>(null)
  const [exportando, setExportando] = useState(false)
  const [baixandoMidiaId, setBaixandoMidiaId] = useState<string | null>(null)
  const [erroMidia, setErroMidia] = useState<unknown>(null)

  useEffect(() => {
    let ativo = true
    void getAdminSession()
      .then((sessao) => {
        if (ativo) setPodeExportar(sessao?.permissoes.includes('ARQUIVO_PUBLICIDADE_EXPORTAR') ?? false)
      })
      .catch(() => {
        if (ativo) setPodeExportar(false)
      })
    return () => { ativo = false }
  }, [])

  useEffect(() => {
    if (!finalidade) {
      setPagina(null)
      setErro(null)
      setCarregando(false)
      return
    }
    const controller = new AbortController()
    setCarregando(true)
    setErro(null)
    setPagina(null)
    void listarRegistrosPublicidade(page, finalidade, controller.signal)
      .then(setPagina)
      .catch((nextError) => {
        if (!controller.signal.aborted) setErro(nextError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregando(false)
      })
    return () => controller.abort()
  }, [page, tentativa, finalidade])

  useEffect(() => {
    if (!selecionadoId || !finalidade) {
      setDetalhe(null)
      setErroDetalhe(null)
      return
    }
    const controller = new AbortController()
    setCarregandoDetalhe(true)
    setErroDetalhe(null)
    setErroExportacao(null)
    setErroMidia(null)
    setDetalhe(null)
    void detalharRegistroPublicidade(selecionadoId, finalidade, controller.signal)
      .then(setDetalhe)
      .catch((nextError) => {
        if (!controller.signal.aborted) setErroDetalhe(nextError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setCarregandoDetalhe(false)
      })
    return () => controller.abort()
  }, [selecionadoId, finalidade])

  const exportar = async (id: string) => {
    if (exportando || !finalidade || !podeExportar) return
    setExportando(true)
    setErroExportacao(null)
    try {
      const blob = await exportarRegistroPublicidade(id, finalidade)
      salvarArquivo(blob, `registro-publicidade-${id}.json`)
    } catch (nextError) {
      setErroExportacao(nextError)
    } finally {
      setExportando(false)
    }
  }

  const baixarMidia = async (registroId: string, midiaId: string, mimeType: string) => {
    if (baixandoMidiaId || !finalidade || !podeExportar) return
    setBaixandoMidiaId(midiaId)
    setErroMidia(null)
    try {
      const blob = await baixarMidiaPublicidade(registroId, midiaId, finalidade)
      salvarArquivo(blob, `publicidade-midia-${midiaId}.${extensaoMidia(mimeType)}`)
    } catch (nextError) {
      setErroMidia(nextError)
    } finally {
      setBaixandoMidiaId(null)
    }
  }

  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Registros e arquivo de publicidade</h1>
        <p className="mt-1 text-sm text-gray-600">Consulta privada das veiculações preservadas e acesso às trilhas já disponíveis em cada módulo.</p>
        {!podeExportar ? <p className="mt-1 text-sm text-gray-600">A exportação e o download das mídias exigem permissão adicional.</p> : null}
      </div>

      <section className="rounded-lg border bg-white p-4" aria-labelledby="arquivo-publicidade-title">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 id="arquivo-publicidade-title" className="text-lg font-semibold">Arquivo privado de publicidade</h2>
            <p className="mt-1 text-sm text-gray-600">Os registros anteriores sem captura histórica não são reconstruídos a partir do estado atual.</p>
          </div>
          <Button type="button" variant="outline" disabled={carregando || !finalidade} onClick={() => setTentativa((value) => value + 1)}>Atualizar</Button>
        </div>

        {carregando ? <p className="mt-4 text-sm text-gray-600" role="status">Carregando registros...</p> : null}
        {erro ? <div className="mt-4"><ContractState error={erro} onRetry={() => setTentativa((value) => value + 1)} /></div> : null}
        <div className="mt-4 max-w-sm">
          <label htmlFor="arquivo-publicidade-finalidade" className="block text-sm font-medium">Finalidade do acesso privado</label>
          <select
            id="arquivo-publicidade-finalidade"
            value={finalidade}
            onChange={(event) => {
              setSelecionadoId(null)
              setPagina(null)
              setErro(null)
              setPage(0)
              setFinalidade(event.target.value as FinalidadeAcessoArquivo | '')
            }}
            className="mt-1 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-pink-600"
          >
            <option value="">Selecione antes de consultar</option>
            {FINALIDADES_ACESSO_ARQUIVO.map((item) => (
              <option key={item.codigo} value={item.codigo}>{item.rotulo}</option>
            ))}
          </select>
          <p className="mt-1 text-xs text-gray-600">A finalidade escolhida fica registrada na auditoria de cada consulta ou exportação.</p>
        </div>
        {pagina ? (
          <>
            <div className="mt-4 overflow-x-auto rounded-lg border">
              <Table>
                <TableHeader><TableRow>
                  <TableHead>Anúncio</TableHead>
                  <TableHead>Conteúdo</TableHead>
                  <TableHead>Natureza</TableHead>
                  <TableHead>Estado</TableHead>
                  <TableHead>Cobertura</TableHead>
                  <TableHead>Início</TableHead>
                  <TableHead>Fim</TableHead>
                  <TableHead>Ação</TableHead>
                </TableRow></TableHeader>
                <TableBody>
                  {pagina.itens.length === 0 ? <TableRow><TableCell colSpan={8}>Nenhuma veiculação preservada nesta página.</TableCell></TableRow> : null}
                  {pagina.itens.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-mono text-xs">{item.anuncioId}</TableCell>
                      <TableCell>{item.titulo || '—'}</TableCell>
                      <TableCell>{item.natureza}</TableCell>
                      <TableCell>{item.status}</TableCell>
                      <TableCell>{item.cobertura}</TableCell>
                      <TableCell>{dataHora(item.inicioEm)}</TableCell>
                      <TableCell>{dataHora(item.fimEm)}</TableCell>
                      <TableCell><Button type="button" size="sm" variant="outline" disabled={!finalidade} onClick={() => setSelecionadoId(item.id)}>Consultar</Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <nav className="mt-4 flex flex-wrap items-center justify-between gap-3" aria-label="Paginação do arquivo de publicidade">
              <p className="text-sm text-gray-600">Página {pagina.page + 1} de {Math.max(1, pagina.totalPages)} · {pagina.totalElements} registro(s)</p>
              <div className="flex gap-2">
                <Button type="button" variant="outline" disabled={page === 0} onClick={() => { setSelecionadoId(null); setPage((value) => value - 1) }}>Anterior</Button>
                <Button type="button" variant="outline" disabled={pagina.last} onClick={() => { setSelecionadoId(null); setPage((value) => value + 1) }}>Próxima</Button>
              </div>
            </nav>
          </>
        ) : null}

        {selecionadoId ? (
          <section className="mt-5 rounded-lg border border-gray-200 bg-gray-50 p-4" aria-labelledby="detalhe-publicidade-title">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 id="detalhe-publicidade-title" className="font-semibold">Detalhe da veiculação</h3>
              <Button type="button" size="sm" variant="outline" onClick={() => setSelecionadoId(null)}>Fechar</Button>
            </div>
            {carregandoDetalhe ? <p className="mt-3 text-sm" role="status">Carregando detalhe...</p> : null}
            {erroDetalhe ? <div className="mt-3"><ContractState error={erroDetalhe} /></div> : null}
            {detalhe ? (
              <>
                <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                  <div><dt className="text-gray-500">Registro</dt><dd className="break-all font-mono text-xs">{detalhe.id}</dd></div>
                  <div><dt className="text-gray-500">Anúncio</dt><dd className="break-all font-mono text-xs">{detalhe.anuncioId}</dd></div>
                  <div><dt className="text-gray-500">Natureza</dt><dd>{detalhe.natureza}</dd></div>
                  <div><dt className="text-gray-500">Relação material</dt><dd>{detalhe.relacaoMaterial}</dd></div>
                  <div><dt className="text-gray-500">Cobertura</dt><dd>{detalhe.cobertura}</dd></div>
                  <div><dt className="text-gray-500">Período</dt><dd>{dataHora(detalhe.inicioEm)} até {dataHora(detalhe.fimEm)}</dd></div>
                  <div><dt className="text-gray-500">Guarda mínima até</dt><dd>{dataHora(detalhe.retencaoAte)}</dd></div>
                  <div><dt className="text-gray-500">Motivo do encerramento</dt><dd>{detalhe.encerramentoMotivo || '—'}</dd></div>
                  <div><dt className="text-gray-500">Contratante</dt><dd className="break-all font-mono text-xs">{detalhe.contratanteUsuarioId || '—'}</dd></div>
                  <div><dt className="text-gray-500">Benefício</dt><dd className="break-all font-mono text-xs">{detalhe.ativacaoBeneficioId || '—'}</dd></div>
                  <div><dt className="text-gray-500">Crédito</dt><dd className="break-all font-mono text-xs">{detalhe.movimentoCreditoId || '—'}</dd></div>
                  <div><dt className="text-gray-500">Pagamento</dt><dd className="break-all font-mono text-xs">{detalhe.pagamentoId || '—'}</dd></div>
                </dl>
                <div className="mt-5">
                  <h4 className="font-semibold">Versões preservadas ({detalhe.versoes.length})</h4>
                  {detalhe.versoes.length === 0 ? <p className="mt-2 text-sm text-gray-600">Nenhuma versão capturada.</p> : null}
                  <ol className="mt-2 space-y-2">
                    {detalhe.versoes.map((versao) => (
                      <li key={versao.id} className="rounded-md border bg-white p-3 text-sm">
                        <p className="font-medium">Versão {versao.numero}: {tituloVersao(versao.conteudo)}</p>
                        {slugVersao(versao.conteudo) ? <p className="mt-1 break-all font-mono text-xs text-gray-600">Slug: {slugVersao(versao.conteudo)}</p> : null}
                        <p className="mt-1 text-gray-600">{dataHora(versao.vigenteDesde)} até {dataHora(versao.vigenteAte)} · {versao.motivo} · {versao.midias.length} mídia(s)</p>
                        {podeExportar && versao.midias.length > 0 ? <div className="mt-2 flex flex-wrap gap-2">{versao.midias.map((midia) => (
                          <Button key={midia.id} type="button" size="sm" variant="outline" disabled={baixandoMidiaId !== null || !finalidade} onClick={() => void baixarMidia(detalhe.id, midia.id, midia.mimeType)}>
                            {baixandoMidiaId === midia.id ? 'Baixando mídia...' : `Baixar mídia ${midia.variante} (${midia.ordem})`}
                          </Button>
                        ))}</div> : null}
                      </li>
                    ))}
                  </ol>
                  {erroMidia ? <div className="mt-3"><ContractState error={erroMidia} /></div> : null}
                </div>
                {podeExportar ? <Button className="mt-4" type="button" variant="outline" disabled={exportando || !finalidade} onClick={() => void exportar(detalhe.id)}>{exportando ? 'Preparando exportação...' : 'Exportar JSON deste registro'}</Button> : null}
                {erroExportacao ? <div className="mt-3"><ContractState error={erroExportacao} /></div> : null}
              </>
            ) : null}
          </section>
        ) : null}
      </section>

      <StoryArchiveSection finalidade={finalidade} podeExportar={podeExportar} />

      <section className="grid gap-3 sm:grid-cols-2" aria-label="Outras trilhas disponíveis">
        {TRILHAS.map((trilha) => (
          <Link key={trilha.href} href={trilha.href} className="rounded-lg border bg-white p-4 transition hover:border-pink-300 hover:bg-pink-50">
            <h2 className="font-semibold">{trilha.titulo}</h2>
            <p className="mt-1 text-sm text-gray-600">{trilha.descricao}</p>
          </Link>
        ))}
      </section>
    </section>
  )
}
