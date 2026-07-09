'use client'

import * as React from 'react'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import type { EstadoItem, CidadeItem, BairroItem } from './types'
import { AnuncioEditFields } from './anuncio-edit-fields'
import { AnuncioEditMedia } from './anuncio-edit-media'
import { AnuncioEditLocation } from './anuncio-edit-location'

export default function AnuncioEditForm(p: {
  maxFotos: number
  maxMB: number
  canUploadVideos: boolean
  carregando: boolean
  salvando: boolean
  loadError: string
  mensagem: string
  fotosMsg: string
  fotosErro: string
  titulo: string
  categoria: string
  preco: string
  horario: string
  locaisAtendimento: string[]
  servicos: string[]
  linkConteudo: string
  descricao: string
  estadoId: string
  cidadeId: string
  bairroId: string
  pontoReferenciaTexto: string
  localizacaoLabel: string
  fotosExistentes: string[]
  videosExistentes: string[]
  videosNovos: File[]
  estados: EstadoItem[]
  cidades: CidadeItem[]
  bairros: BairroItem[]
  loadingEstados: boolean
  loadingCidades: boolean
  loadingBairros: boolean

  setTitulo: (v: string) => void
  setCategoria: (v: string) => void
  setPreco: (v: string) => void
  setHorario: (v: string) => void
  toggleLocalAtendimento: (v: string) => void
  toggleServico: (v: string) => void
  setLinkConteudo: (v: string) => void
  setDescricao: (v: string) => void
  setPontoReferenciaTexto: (v: string) => void
  onFotosChange: (files: File[]) => Promise<void>
  setFotosExistentes: (v: string[]) => void
  setVideosExistentes: (v: string[]) => void
  setVideosNovos: (v: File[]) => void
  onMediaTouched?: () => void
  onSelectEstado: (id: string) => void
  onSelectCidade: (id: string) => void
  onSelectBairro: (id: string) => void

  onSubmit: () => Promise<void>
  onRetryLoad: () => void
}) {
  if (p.carregando) {
    return (
      <div className="flex justify-center items-center py-20 text-gray-500">
        Carregando informações...
      </div>
    )
  }

  if (p.loadError) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-20 text-center">
        <div>
          <h2 className="text-xl font-semibold text-gray-900">Não foi possível carregar os dados do anúncio.</h2>
          <p className="mt-2 text-sm text-gray-600">Nenhuma alteração pode ser salva enquanto os dados originais não carregarem.</p>
        </div>
        <Button type="button" onClick={p.onRetryLoad} className="bg-[#FC1EAD] hover:bg-[#e01a9a]">
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <div className="py-14 max-w-[1100px] mx-auto">
      <div className="mb-10 text-center">
        <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 tracking-tight">
          Editar anúncio
        </h1>
        <p className="text-gray-600 mt-2 text-sm sm:text-base">Ajuste rápido. Sem drama. 😉</p>
      </div>

      <div className="mb-6 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-700">
        Você pode usar <strong>até {p.maxFotos} fotos</strong>. Máx. {p.maxMB}MB cada.
      </div>

      <Card className="p-8 shadow-xs border border-gray-300/50">
        <form
          className="space-y-10"
          onSubmit={(e) => {
            e.preventDefault()
            void p.onSubmit()
          }}
        >
          <AnuncioEditFields
            titulo={p.titulo}
            setTitulo={p.setTitulo}
            categoria={p.categoria}
            setCategoria={p.setCategoria}
            preco={p.preco}
            setPreco={p.setPreco}
            horario={p.horario}
            setHorario={p.setHorario}
            locaisAtendimento={p.locaisAtendimento}
            toggleLocalAtendimento={p.toggleLocalAtendimento}
            servicos={p.servicos}
            toggleServico={p.toggleServico}
            linkConteudo={p.linkConteudo}
            setLinkConteudo={p.setLinkConteudo}
            descricao={p.descricao}
            setDescricao={p.setDescricao}
          />

          <AnuncioEditMedia
            maxFotos={p.maxFotos}
            maxMB={p.maxMB}
            fotosExistentes={p.fotosExistentes}
            setFotosExistentes={p.setFotosExistentes}
            onFotosChange={p.onFotosChange}
            fotosMsg={p.fotosMsg}
            fotosErro={p.fotosErro}
            videosExistentes={p.videosExistentes}
            setVideosExistentes={p.setVideosExistentes}
            videosNovos={p.videosNovos}
            setVideosNovos={p.setVideosNovos}
            canUploadVideos={p.canUploadVideos}
            onMediaTouched={p.onMediaTouched}
          />

          <AnuncioEditLocation
            localizacaoLabel={p.localizacaoLabel}
            estados={p.estados}
            cidades={p.cidades}
            bairros={p.bairros}
            loadingEstados={p.loadingEstados}
            loadingCidades={p.loadingCidades}
            loadingBairros={p.loadingBairros}
            estadoId={p.estadoId}
            cidadeId={p.cidadeId}
            bairroId={p.bairroId}
            pontoReferenciaTexto={p.pontoReferenciaTexto}
            setPontoReferenciaTexto={p.setPontoReferenciaTexto}
            onSelectEstado={p.onSelectEstado}
            onSelectCidade={p.onSelectCidade}
            onSelectBairro={p.onSelectBairro}
          />

          <div className="pt-6 border-t border-gray-100">
            <Button
              type="submit"
              className="w-full py-6 bg-[#FC1EAD] hover:bg-[#e01a9a]"
              disabled={p.salvando || !!p.fotosErro}
            >
              {p.salvando ? 'Salvando...' : 'Salvar alterações'}
            </Button>
          </div>

          {p.mensagem && <p className="text-center text-sm mt-3 text-gray-700">{p.mensagem}</p>}
        </form>
      </Card>
    </div>
  )
}
