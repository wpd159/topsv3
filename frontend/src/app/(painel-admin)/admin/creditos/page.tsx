'use client'

import { useEffect, useMemo, useState } from 'react'
import PlanosCreditoCard from '../components/creditos/planos-credito-card'
import FeatureCatalogoCard from '../components/creditos/feature-catalogo-card'
import FeatureCatalogoMatrix from '../components/creditos/feature-catalogo-matrix'

type PlanoCredito = {
  id: number
  nome: string
  creditos: number
  valor: number
  descricao?: string
  ativo: boolean
}

type FeatureCatalogoDuracao = {
  id?: number
  dias: number
  custoCreditos: number
  ativo: boolean
  sortOrder: number
}

type FeatureCatalogo = {
  id: number
  codigo: string
  nome: string
  descricao?: string
  custoCreditos: number
  duracaoHoras?: number | null
  escopo: 'ANUNCIO' | 'USUARIO' | string
  ativo: boolean
  duracoes?: FeatureCatalogoDuracao[]
}

type FeaturePatch = Partial<
  Pick<FeatureCatalogo, 'nome' | 'descricao' | 'custoCreditos' | 'duracaoHoras' | 'ativo'>
> & {
  duracoes?: FeatureCatalogoDuracao[]
}

export default function AdminCreditosPage() {
  const API_URL = process.env.NEXT_PUBLIC_API_URL

  const [planos, setPlanos] = useState<PlanoCredito[]>([])
  const [features, setFeatures] = useState<FeatureCatalogo[]>([])
  const [loadingPlanos, setLoadingPlanos] = useState(true)
  const [loadingFeatures, setLoadingFeatures] = useState(true)

  const coresPlano = useMemo(
    () => (nome: string) =>
      nome === 'Ouro'
        ? 'from-yellow-100 to-transparent text-yellow-700'
        : nome === 'Diamante'
          ? 'from-blue-100 to-transparent text-blue-700'
          : 'from-gray-200 to-transparent text-gray-700',
    []
  )

  const corFeature = useMemo(
    () => (codigo: string) =>
      codigo === 'ANUNCIO_TOPO'
        ? 'from-emerald-100 to-transparent text-emerald-700'
        : codigo === 'CARROSSEL_FOTOS'
          ? 'from-purple-100 to-transparent text-purple-700'
          : codigo === 'FOTOS_EXTRA_5'
            ? 'from-pink-100 to-transparent text-pink-700'
            : codigo === 'VIDEO_1'
              ? 'from-blue-100 to-transparent text-blue-700'
              : codigo === 'STORIES'
                ? 'from-amber-100 to-transparent text-amber-700'
                : 'from-gray-200 to-transparent text-gray-700',
    []
  )

  useEffect(() => {
    const fetchPlanos = async () => {
      try {
        setLoadingPlanos(true)
        const res = await fetch(`${API_URL}/creditos/planos/admin`, {
          credentials: 'include',
        })
        if (!res.ok) throw new Error('Erro ao carregar planos')
        const data = await res.json()
        setPlanos(Array.isArray(data) ? data : [])
      } catch {
        setPlanos([])
      } finally {
        setLoadingPlanos(false)
      }
    }

    fetchPlanos()
  }, [API_URL])

  useEffect(() => {
    const fetchFeatures = async () => {
      try {
        setLoadingFeatures(true)
        const res = await fetch(`${API_URL}/features/catalogo/admin`, {
          credentials: 'include',
        })
        if (!res.ok) throw new Error('Erro ao carregar features')
        const data = await res.json()
        setFeatures(Array.isArray(data) ? data : [])
      } catch {
        setFeatures([])
      } finally {
        setLoadingFeatures(false)
      }
    }

    fetchFeatures()
  }, [API_URL])

  const atualizarPlano = async (id: number, valor: number, creditos: number) => {
    try {
      const res = await fetch(`${API_URL}/creditos/planos/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ valor, creditos }),
      })
      if (!res.ok) throw new Error('Erro ao salvar alteracoes')
      const atualizado = await res.json()
      setPlanos((prev) => prev.map((plano) => (plano.id === id ? atualizado : plano)))
    } catch {
      alert('Falha ao salvar o plano.')
    }
  }

  const atualizarFeature = async (id: number, patch: FeaturePatch) => {
    try {
      const res = await fetch(`${API_URL}/features/catalogo/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(patch),
      })
      if (!res.ok) throw new Error('Erro ao salvar feature')
      const atualizado = await res.json()
      setFeatures((prev) => prev.map((feature) => (feature.id === id ? atualizado : feature)))
    } catch {
      alert('Falha ao salvar a feature.')
    }
  }

  return (
    <section>
      <div className="mb-6 flex flex-col gap-1">
        <h1 className="text-2xl font-bold text-gray-800">Gerenciar Creditos</h1>
        <p className="text-sm text-gray-500">
          Planos de creditos e custo das funcionalidades do catalogo.
        </p>
      </div>

      <div className="mt-8">
        <div className="flex items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-gray-800">Planos de Credito</h2>
            <p className="text-sm text-gray-500">Edite valor e quantidade de creditos por plano.</p>
          </div>
        </div>

        {loadingPlanos ? (
          <p className="mt-4 text-gray-500">Carregando planos...</p>
        ) : (
          <div className="mt-5 grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-3">
            {planos.map((plano) => (
              <PlanosCreditoCard
                key={plano.id}
                plano={{ ...plano, cor: coresPlano(plano.nome) }}
                atualizarPlano={atualizarPlano}
              />
            ))}
          </div>
        )}
      </div>

      <div className="mt-10">
        <div className="flex items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-gray-800">Catalogo de Beneficios</h2>
            <p className="text-sm text-gray-500">
              Edite custo, duracao e matriz comercial dos beneficios sem criar um painel separado.
            </p>
          </div>
        </div>

        {loadingFeatures ? (
          <p className="mt-4 text-gray-500">Carregando funcionalidades...</p>
        ) : (
          <>
            <FeatureCatalogoMatrix features={features} onSave={atualizarFeature} />

            <div className="mt-6 grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-3">
              {features.map((feature) => (
                <FeatureCatalogoCard
                  key={feature.id}
                  feature={{ ...feature, cor: corFeature(feature.codigo) }}
                  onSave={(patch) => atualizarFeature(feature.id, patch)}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </section>
  )
}
