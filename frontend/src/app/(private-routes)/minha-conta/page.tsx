'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DocumentTextIcon,
  ChartBarIcon,
  TrophyIcon,
  CurrencyDollarIcon,
  TrashIcon,
} from '@heroicons/react/24/solid'
import InformacoesPessoaisCard from '@/components/minha-conta/informacoes-pessoais-card'
import SegurancaContaCard from '@/components/minha-conta/seguranca-conta-card'
import { useAuth } from '@/context/AuthContext'

type MovimentacaoCredito = {
  titulo: string
  descricao?: string | null
  quantidade: number
  status?: string | null
  provider?: string | null
  data?: string | null
  metodoPagamento?: string | null
}

export default function MinhaContaPage() {
  const router = useRouter()
  const { usuario, carregando } = useAuth()

  const [dados, setDados] = useState({
    nome: '',
    email: '',
    telefone: '',
    cidade: '',
    descricao: '',
    estadoId: null as number | null,
    cidadeId: null as number | null,
    bairroId: null as number | null,
    creditos: 0,
    anunciosPostados: 0,
    documentosVerificados: 0,
    status: 'INATIVO',
  })

  const [mensagem, setMensagem] = useState<string | null>(null)
  const [carregandoEnvio, setCarregandoEnvio] = useState(false)
  const [resumoCreditos, setResumoCreditos] = useState<{
    recargasRecentes: MovimentacaoCredito[]
    consumosRecentes: MovimentacaoCredito[]
  }>({
    recargasRecentes: [],
    consumosRecentes: [],
  })

  useEffect(() => {
    if (usuario) {
      setDados({
        nome: usuario.username || '',
        email: usuario.email || '',
        telefone: usuario.telefone || '',
        cidade: usuario.cidade || usuario.localizacao || '', // legado
        descricao: usuario.descricao || '',
        estadoId: usuario.estadoId ?? null,
        cidadeId: usuario.cidadeId ?? null,
        bairroId: usuario.bairroId ?? null,
        creditos: usuario.creditos || 0,
        anunciosPostados: usuario.totalAnuncios || 0,
        documentosVerificados: usuario.totalDocumentos || 0,
        status: usuario.status || 'INATIVO',
      })
    }
  }, [usuario])

  useEffect(() => {
    if (usuario?.creditos !== undefined && usuario?.creditos !== dados.creditos) {
      setDados((prev) => ({ ...prev, creditos: usuario.creditos }))
    }
  }, [usuario?.creditos, dados.creditos])

  useEffect(() => {
    const carregarResumoCreditos = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/creditos/resumo`, {
          credentials: 'include',
          cache: 'no-store',
        })

        if (!res.ok) return

        const data = await res.json()
        setResumoCreditos({
          recargasRecentes: Array.isArray(data?.recargasRecentes) ? data.recargasRecentes : [],
          consumosRecentes: Array.isArray(data?.consumosRecentes) ? data.consumosRecentes : [],
        })
      } catch {
      }
    }

    if (usuario) {
      carregarResumoCreditos()
    }
  }, [usuario?.id, usuario?.creditos])

  if (carregando)
    return (
      <div className="flex justify-center items-center py-20 text-gray-500">
        Carregando suas informações...
      </div>
    )

  if (!usuario)
    return (
      <div className="flex justify-center items-center py-20 text-gray-500">
        Você precisa estar logado para acessar esta página.
      </div>
    )

  const handleExcluirConta = async () => {
    if (!usuario?.email) {
      setMensagem('⚠️ Não foi possível identificar seu e-mail.')
      return
    }

    const confirmar = confirm(
      'Tem certeza que deseja excluir sua conta? Essa ação é irreversível e todos os seus dados serão apagados.'
    )
    if (!confirmar) return

    try {
      setCarregandoEnvio(true)
      setMensagem('Excluindo sua conta...')

      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/usuarios/${usuario.email}/excluir-conta`,
        {
          method: 'DELETE',
          credentials: 'include',
        }
      )

      if (!res.ok) {
        throw new Error('Falha ao excluir conta. Tente novamente.')
      }

      setMensagem('✅ Conta excluída com sucesso! Você será desconectado em instantes.')

      setTimeout(() => {
        sessionStorage.clear()
        window.location.href = '/'
      }, 2500)
    } catch (err: any) {
      setMensagem('❌ Erro ao excluir conta: ' + err.message)
    } finally {
      setCarregandoEnvio(false)
    }
  }

  return (
    <div className="grid grid-cols-1 px-4 lg:grid-cols-3 gap-6 mt-10">
      <div className="lg:col-span-2 flex flex-col gap-6">
        <InformacoesPessoaisCard dados={dados} setDados={setDados} />
        <SegurancaContaCard />

        <div className="flex flex-col items-center mt-6 border-t border-gray-200 pt-6">
          <Button
            onClick={handleExcluirConta}
            disabled={carregandoEnvio}
            className="bg-red-600 hover:bg-red-700 w-full text-white font-semibold px-6 py-4 text-base flex items-center gap-2 shadow-md"
          >
            <TrashIcon className="w-5 h-5" />
            {carregandoEnvio ? 'Excluindo...' : 'Excluir minha conta'}
          </Button>
          <p className="text-xs text-gray-500 mt-2 text-center">
            ⚠️ Esta ação é permanente. Todos os seus anúncios, créditos e documentos serão removidos.
          </p>
          {mensagem && <p className="text-sm mt-3 text-gray-700 text-center">{mensagem}</p>}
        </div>
      </div>

      <div className="flex flex-col gap-6">
        <Card className="border border-gray-300/50">
          <CardContent className="p-6 flex flex-col items-start gap-3">
            <div className="flex items-center justify-between w-full">
              <h2 className="text-base font-semibold text-gray-900">Créditos disponíveis</h2>
              <Badge
                variant="outline"
                className={`text-xs font-semibold border ${
                  dados.creditos > 0
                    ? 'border-green-400 text-green-700 bg-green-50'
                    : 'border-red-300 text-red-700 bg-red-50'
                }`}
              >
                {dados.creditos} crédito{dados.creditos === 1 ? '' : 's'}
              </Badge>
            </div>

            <p className="text-gray-600 text-sm">
              Use seus créditos para impulsionar seus anúncios e aumentar sua visibilidade.
            </p>

            <Button
              onClick={() => router.push('/creditos')}
              className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white mt-2 flex items-center justify-center gap-2"
            >
              <CurrencyDollarIcon className="w-5 h-5" />
              Comprar créditos
            </Button>
          </CardContent>
        </Card>

        <Card className="border border-gray-300/50">
          <CardContent className="p-6 space-y-4">
            <h2 className="text-base font-semibold text-gray-900">Resumo</h2>
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between border-b pb-2">
                <div className="flex items-center gap-2 text-gray-700 text-sm">
                  <ChartBarIcon className="w-5 h-5 text-[#FC1EAD]" />
                  Total de anúncios
                </div>
                <span className="font-semibold text-gray-900">{dados.anunciosPostados}</span>
              </div>

              <div className="flex items-center justify-between border-b pb-2">
                <div className="flex items-center gap-2 text-gray-700 text-sm">
                  <DocumentTextIcon className="w-5 h-5 text-[#FC1EAD]" />
                  Documentos verificados
                </div>
                <span className="font-semibold text-gray-900">{dados.documentosVerificados}</span>
              </div>

              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-gray-700 text-sm">
                  <TrophyIcon className="w-5 h-5 text-[#FC1EAD]" />
                  Status da conta
                </div>
                <Badge
                  className={`text-xs font-medium border ${
                    dados.status === 'ATIVO'
                      ? 'bg-green-100 text-green-700 border-green-300'
                      : 'bg-red-100 text-red-700 border-red-300'
                  }`}
                >
                  {dados.status === 'ATIVO' ? 'Ativa' : 'Inativa'}
                </Badge>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border border-gray-300/50">
          <CardContent className="p-6 space-y-4">
            <h2 className="text-base font-semibold text-gray-900">Recargas recentes</h2>
            {resumoCreditos.recargasRecentes.length === 0 ? (
              <p className="text-sm text-gray-500">Nenhuma recarga recente encontrada.</p>
            ) : (
              <div className="space-y-3">
                {resumoCreditos.recargasRecentes.map((item, index) => (
                  <div key={`${item.titulo}-${index}`} className="rounded-xl border border-gray-200 p-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold text-gray-900">{item.titulo}</p>
                      <Badge variant="outline" className="border-green-300 bg-green-50 text-green-700">
                        +{item.quantidade}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-gray-500">{item.descricao || 'Recarga de créditos'}</p>
                    <p className="mt-1 text-[11px] text-gray-400">
                      {(item.provider || 'Plataforma')} {item.metodoPagamento ? `· ${item.metodoPagamento}` : ''} {item.data ? `· ${new Date(item.data).toLocaleString('pt-BR')}` : ''}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border border-gray-300/50">
          <CardContent className="p-6 space-y-4">
            <h2 className="text-base font-semibold text-gray-900">Consumo recente</h2>
            {resumoCreditos.consumosRecentes.length === 0 ? (
              <p className="text-sm text-gray-500">Nenhum consumo recente encontrado.</p>
            ) : (
              <div className="space-y-3">
                {resumoCreditos.consumosRecentes.map((item, index) => (
                  <div key={`${item.titulo}-${index}`} className="rounded-xl border border-gray-200 p-3">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold text-gray-900">{item.titulo}</p>
                      <Badge variant="outline" className="border-red-300 bg-red-50 text-red-700">
                        {item.quantidade}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-gray-500">{item.descricao || 'Uso de créditos'}</p>
                    <p className="mt-1 text-[11px] text-gray-400">
                      {item.data ? new Date(item.data).toLocaleString('pt-BR') : ''}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
