'use client'

import { useRef, useState } from 'react'
import { RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  AdminCreditosApi,
  adminOperationKey,
  type AdminCreditoMovimento,
  type AdminCreditoUsuario,
} from '@/lib/admin-creditos-operacionais-api'
import { dataHora, mensagemErro, rotuloOperacional } from './admin-monetizacao-utils'

export function AdminSaldosAjustes() {
  const [query, setQuery] = useState('')
  const [usuarios, setUsuarios] = useState<AdminCreditoUsuario[]>([])
  const [usuario, setUsuario] = useState<AdminCreditoUsuario | null>(null)
  const [movimentos, setMovimentos] = useState<AdminCreditoMovimento[]>([])
  const [direcao, setDirecao] = useState<'CREDITO' | 'DEBITO'>('CREDITO')
  const [quantidade, setQuantidade] = useState('')
  const [motivo, setMotivo] = useState('')
  const [buscando, setBuscando] = useState(false)
  const [carregandoUsuario, setCarregandoUsuario] = useState(false)
  const [operando, setOperando] = useState(false)
  const [erroBusca, setErroBusca] = useState<string | null>(null)
  const [erroUsuario, setErroUsuario] = useState<string | null>(null)
  const operacaoLock = useRef(false)
  const buscaSeq = useRef(0)
  const usuarioSeq = useRef(0)
  const usuarioSelecionadoId = useRef<string | null>(null)
  const ajusteTentativa = useRef<{ assinatura: string; chave: string } | null>(null)
  const estornoTentativas = useRef(new Map<string, string>())

  const buscar = async () => {
    const termo = query.trim()
    if (termo.length < 2) {
      setErroBusca('Informe ao menos dois caracteres para localizar um usuário.')
      return
    }
    const seq = ++buscaSeq.current
    setBuscando(true)
    setErroBusca(null)
    try {
      const encontrados = await AdminCreditosApi.buscarUsuarios(termo)
      if (seq !== buscaSeq.current) return
      setUsuarios(encontrados)
    } catch (error) {
      if (seq === buscaSeq.current) {
        setErroBusca(mensagemErro(error, 'Não foi possível buscar usuários.'))
      }
    } finally {
      if (seq === buscaSeq.current) setBuscando(false)
    }
  }

  const selecionarUsuario = async (item: AdminCreditoUsuario) => {
    const seq = ++usuarioSeq.current
    usuarioSelecionadoId.current = item.id
    setUsuario(item)
    setMovimentos([])
    setCarregandoUsuario(true)
    setErroUsuario(null)
    try {
      const [saldo, pagina] = await Promise.all([
        AdminCreditosApi.saldo(item.id),
        AdminCreditosApi.movimentos(item.id),
      ])
      if (seq !== usuarioSeq.current) return
      setUsuario({ ...item, saldo: saldo.saldoCalculadoMovimentos })
      setMovimentos(pagina.itens)
    } catch (error) {
      if (seq === usuarioSeq.current) {
        setErroUsuario(mensagemErro(error, 'Não foi possível carregar o saldo e o ledger deste usuário.'))
      }
    } finally {
      if (seq === usuarioSeq.current) setCarregandoUsuario(false)
    }
  }

  const recarregarUsuario = async () => {
    if (usuario) await selecionarUsuario(usuario)
  }

  const ajustar = async () => {
    if (!usuario || operacaoLock.current) return
    const valor = Number(quantidade)
    if (!Number.isInteger(valor) || valor <= 0) {
      setErroUsuario('Informe uma quantidade inteira positiva.')
      return
    }
    if (motivo.trim().length < 5) {
      setErroUsuario('Informe um motivo com ao menos cinco caracteres.')
      return
    }
    if (direcao === 'DEBITO' && !window.confirm(`Remover ${valor} créditos de ${usuario.nome}?`)) return
    operacaoLock.current = true
    setOperando(true)
    setErroUsuario(null)
    const assinatura = JSON.stringify([usuario.id, direcao, valor, motivo.trim()])
    const tentativa = ajusteTentativa.current?.assinatura === assinatura
      ? ajusteTentativa.current
      : { assinatura, chave: adminOperationKey('ajuste') }
    ajusteTentativa.current = tentativa
    try {
      const resultado = await AdminCreditosApi.ajustar(
        usuario.id,
        direcao,
        valor,
        motivo.trim(),
        tentativa.chave,
      )
      setQuantidade('')
      setMotivo('')
      if (usuarioSelecionadoId.current === usuario.id) {
        await selecionarUsuario({ ...usuario, saldo: resultado.saldoPosterior })
      }
      toast.success(`Saldo atualizado para ${resultado.saldoPosterior} créditos.`)
      ajusteTentativa.current = null
    } catch (error) {
      const message = mensagemErro(error, 'Não foi possível ajustar o saldo.')
      setErroUsuario(message)
      toast.error(message)
    } finally {
      operacaoLock.current = false
      setOperando(false)
    }
  }

  const estornar = async (movimento: AdminCreditoMovimento) => {
    if (!usuario || operacaoLock.current || movimento.natureza === 'ESTORNO') return
    const motivoEstorno = window.prompt('Motivo do estorno (mínimo de cinco caracteres):')?.trim() || ''
    if (motivoEstorno.length < 5) return
    operacaoLock.current = true
    setOperando(true)
    setErroUsuario(null)
    const assinatura = JSON.stringify([movimento.id, motivoEstorno])
    const chave = estornoTentativas.current.get(assinatura) ?? adminOperationKey('estorno')
    estornoTentativas.current.set(assinatura, chave)
    try {
      await AdminCreditosApi.estornar(movimento.id, motivoEstorno, chave)
      if (usuarioSelecionadoId.current === usuario.id) {
        await selecionarUsuario(usuario)
      }
      toast.success('Estorno registrado no ledger.')
      estornoTentativas.current.delete(assinatura)
    } catch (error) {
      const message = mensagemErro(error, 'Não foi possível estornar o movimento.')
      setErroUsuario(message)
      toast.error(message)
    } finally {
      operacaoLock.current = false
      setOperando(false)
    }
  }

  return (
    <section className="min-w-0 space-y-5" aria-labelledby="saldos-title">
      <div>
        <h2 id="saldos-title" className="text-base font-semibold text-gray-900">Saldos e ajustes</h2>
        <p className="mt-1 text-sm text-gray-500">Consulte o ledger e faça ajustes auditados em um único usuário por vez.</p>
      </div>

      <div className="grid min-w-0 gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
        <aside className="min-w-0 rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <h3 className="font-semibold text-gray-900">Localizar usuário</h3>
          <form className="mt-3 flex min-w-0 flex-col gap-2 sm:flex-row xl:flex-col" onSubmit={(event) => { event.preventDefault(); void buscar() }}>
            <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nome ou e-mail" aria-label="Buscar usuário para saldo" />
            <Button type="submit" disabled={buscando} aria-busy={buscando}>
              <Search className="mr-2 size-4" aria-hidden="true" />
              {buscando ? 'Buscando...' : 'Buscar'}
            </Button>
          </form>
          {erroBusca ? <p className="mt-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{erroBusca}</p> : null}
          {!buscando && !erroBusca && query.trim().length >= 2 && usuarios.length === 0 ? <p className="mt-4 text-sm text-gray-500">Nenhum usuário localizado.</p> : null}
          <div className="mt-4 space-y-2">
            {usuarios.map((item) => (
              <button key={item.id} type="button" onClick={() => void selecionarUsuario(item)} aria-pressed={usuario?.id === item.id} className="w-full min-w-0 rounded-md border border-gray-200 p-3 text-left transition hover:border-pink-300 hover:bg-pink-50 aria-pressed:border-pink-400 aria-pressed:bg-pink-50">
                <p className="truncate text-sm font-semibold text-gray-900">{item.nome}</p>
                <p className="truncate text-xs text-gray-500">{item.email}</p>
                <p className="mt-1 text-xs font-semibold text-[#C51683]">{item.saldo} créditos</p>
              </button>
            ))}
          </div>
        </aside>

        <div className="min-w-0 space-y-6">
          <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="font-semibold text-gray-900">Saldo do usuário</h3>
                <p className="mt-1 text-sm text-gray-500">{usuario ? `${usuario.nome}: ${usuario.saldo} créditos` : 'Selecione um usuário.'}</p>
              </div>
              {usuario ? <Button type="button" variant="outline" size="icon" aria-label="Atualizar saldo" title="Atualizar saldo" disabled={carregandoUsuario} onClick={() => void recarregarUsuario()}><RefreshCw className={`size-4 ${carregandoUsuario ? 'animate-spin' : ''}`} aria-hidden="true" /></Button> : null}
            </div>

            {carregandoUsuario ? <p className="mt-5 text-sm text-gray-500" role="status">Carregando saldo e movimentos...</p> : null}
            {erroUsuario ? <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3" role="alert"><p className="text-sm text-red-700">{erroUsuario}</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void recarregarUsuario()}>Tentar novamente</Button></div> : null}

            {usuario && !carregandoUsuario ? (
              <div className="mt-5 grid min-w-0 gap-3 md:grid-cols-[150px_150px_minmax(0,1fr)_auto] md:items-end">
                <label className="text-xs font-medium text-gray-600">Operação
                  <select value={direcao} onChange={(event) => setDirecao(event.target.value as 'CREDITO' | 'DEBITO')} className="mt-1 h-10 w-full rounded-md border border-gray-200 bg-white px-3 text-sm">
                    <option value="CREDITO">Adicionar</option>
                    <option value="DEBITO">Remover</option>
                  </select>
                </label>
                <label className="text-xs font-medium text-gray-600">Quantidade
                  <Input className="mt-1" type="number" min={1} inputMode="numeric" value={quantidade} onChange={(event) => setQuantidade(event.target.value)} />
                </label>
                <label className="min-w-0 text-xs font-medium text-gray-600">Motivo obrigatório
                  <Input className="mt-1" value={motivo} maxLength={500} onChange={(event) => setMotivo(event.target.value)} />
                </label>
                <Button type="button" disabled={operando} aria-busy={operando} onClick={() => void ajustar()}>{operando ? 'Salvando...' : 'Confirmar'}</Button>
              </div>
            ) : null}
          </section>

          <section className="min-w-0 rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
            <h3 className="font-semibold text-gray-900">Movimentos do ledger</h3>
            {!usuario ? <p className="mt-4 text-sm text-gray-500">Selecione um usuário para consultar os movimentos.</p> : null}
            {usuario && !carregandoUsuario && movimentos.length === 0 ? <p className="mt-4 text-sm text-gray-500">Nenhum movimento registrado para este usuário.</p> : null}

            {movimentos.length > 0 ? (
              <>
                <div className="mt-4 hidden overflow-x-auto md:block">
                  <table className="min-w-full text-left text-sm">
                    <thead className="text-xs uppercase text-gray-500"><tr><th className="p-2">Natureza</th><th className="p-2">Quantidade</th><th className="p-2">Saldo</th><th className="p-2">Motivo</th><th className="p-2">Data</th><th className="p-2 text-right">Ação</th></tr></thead>
                    <tbody>{movimentos.map((item) => <tr key={item.id} className="border-t border-gray-100"><td className="p-2">{rotuloOperacional(item.natureza)}</td><td className="p-2">{item.quantidade}</td><td className="p-2">{item.saldoAntes} → {item.saldoDepois}</td><td className="max-w-56 truncate p-2">{item.motivo || '-'}</td><td className="p-2">{dataHora(item.criadoEm)}</td><td className="p-2 text-right"><Button size="sm" variant="outline" disabled={operando || item.natureza === 'ESTORNO'} onClick={() => void estornar(item)}>Estornar</Button></td></tr>)}</tbody>
                  </table>
                </div>
                <div className="mt-4 divide-y divide-gray-100 md:hidden">
                  {movimentos.map((item) => <article key={item.id} className="py-4"><div className="flex items-start justify-between gap-3"><p className="font-medium text-gray-900">{rotuloOperacional(item.natureza)}</p><span className="text-sm font-semibold">{item.quantidade}</span></div><p className="mt-1 text-sm text-gray-600">Saldo: {item.saldoAntes} → {item.saldoDepois}</p><p className="mt-1 break-words text-sm text-gray-600">{item.motivo || 'Sem motivo informado'}</p><p className="mt-1 text-xs text-gray-500">{dataHora(item.criadoEm)}</p><Button className="mt-3 w-full" size="sm" variant="outline" disabled={operando || item.natureza === 'ESTORNO'} onClick={() => void estornar(item)}>Estornar</Button></article>)}
                </div>
              </>
            ) : null}
          </section>
        </div>
      </div>
    </section>
  )
}