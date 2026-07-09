'use client'

import { useState } from "react"
import { MagnifyingGlassIcon, PlusIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import GerenciarAvisosTable from "./avisos-table"

const STATUS_OPCOES = [
  { label: "Todos", value: "TODOS" },
  { label: "Ativos", value: "ATIVO" },
  { label: "Inativos", value: "INATIVO" },
]

const LOCAIS_OPCOES = [
  { label: "Todos", value: "TODOS" },
  { label: "Site", value: "SITE" },
  { label: "Rodapé anúncio", value: "ANUNCIO_RODAPE" },
  { label: "Popup login", value: "LOGIN_POPUP" },
]

const FREQUENCIA_OPCOES = [
  { label: "Sempre", value: "SEMPRE" },
  { label: "Uma vez", value: "UMA_VEZ" },
  { label: "Diário", value: "DIARIO" },
]

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export default function AdminAvisosPage() {
  const [busca, setBusca] = useState("")
  const [status, setStatus] = useState("TODOS")
  const [filtroLocalExibicao, setFiltroLocalExibicao] = useState("TODOS")
  const [refreshKey, setRefreshKey] = useState(0)
  const [modalOpen, setModalOpen] = useState(false)
  const [salvando, setSalvando] = useState(false)

  const [titulo, setTitulo] = useState("")
  const [descricao, setDescricao] = useState("")
  const [novoLocalExibicao, setNovoLocalExibicao] = useState("SITE")
  const [frequenciaExibicao, setFrequenciaExibicao] = useState("SEMPRE")
  const [ativoDe, setAtivoDe] = useState("")
  const [ativoAte, setAtivoAte] = useState("")
  const [permiteDispensar, setPermiteDispensar] = useState(true)

  const resetModal = () => {
    setTitulo("")
    setDescricao("")
    setNovoLocalExibicao("SITE")
    setFrequenciaExibicao("SEMPRE")
    setAtivoDe("")
    setAtivoAte("")
    setPermiteDispensar(true)
  }

  const abrirModal = () => {
    resetModal()
    setModalOpen(true)
  }

  const fecharModal = () => {
    setModalOpen(false)
    resetModal()
  }

  const criarAviso = async () => {
    if (titulo.trim().length < 3 || descricao.trim().length < 5) {
      toast.warning("Preencha título e descrição do aviso.")
      return
    }

    setSalvando(true)
    try {
      const res = await fetch(apiUrl("/avisos"), {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          titulo: titulo.trim(),
          descricao: descricao.trim(),
          localExibicao: novoLocalExibicao,
          frequenciaExibicao,
          ativoDe: ativoDe ? new Date(ativoDe).toISOString().slice(0, 19) : null,
          ativoAte: ativoAte ? new Date(ativoAte).toISOString().slice(0, 19) : null,
          permiteDispensar,
        }),
      })

      if (!res.ok) {
        throw new Error("Erro ao criar aviso.")
      }

      toast.success("Aviso criado com sucesso.")
      fecharModal()
      setRefreshKey((value) => value + 1)
    } catch (error: any) {
      toast.error(error?.message || "Não foi possível criar o aviso.")
    } finally {
      setSalvando(false)
    }
  }

  return (
    <section>
      <div className="mb-6 flex flex-col gap-1">
        <h1 className="text-2xl font-bold text-gray-800">Avisos</h1>
        <p className="text-sm text-gray-500">
          Configure avisos para o site, rodapé de anúncios e popup de login sem criar fluxos paralelos.
        </p>
      </div>

      <div className="mb-6 flex flex-col gap-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-2">
            {STATUS_OPCOES.map((opcao) => (
              <Button
                key={opcao.value}
                variant={status === opcao.value ? "default" : "outline"}
                onClick={() => setStatus(opcao.value)}
                className={
                  status === opcao.value
                    ? "bg-[#f0198f] text-white hover:bg-[#f0198f]/90"
                    : "border-gray-300 text-gray-700 hover:bg-gray-100"
                }
              >
                {opcao.label}
              </Button>
            ))}
          </div>

          <Button onClick={abrirModal} className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] sm:w-auto">
            <PlusIcon className="mr-2 h-5 w-5" />
            Novo aviso
          </Button>
        </div>

        <div className="flex flex-col gap-4 lg:flex-row">
          <div className="relative max-w-md w-full">
            <MagnifyingGlassIcon className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
            <Input
              type="text"
              placeholder="Buscar por título, texto ou criador..."
              value={busca}
              onChange={(event) => setBusca(event.target.value)}
              className="pl-10"
            />
          </div>

          <select
            value={filtroLocalExibicao}
            onChange={(event) => setFiltroLocalExibicao(event.target.value)}
            className="h-10 rounded-md border border-gray-300 px-3 text-sm text-gray-700"
          >
            {LOCAIS_OPCOES.map((opcao) => (
              <option key={opcao.value} value={opcao.value}>
                {opcao.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <GerenciarAvisosTable
        key={refreshKey}
        busca={busca}
        status={status}
        localExibicao={filtroLocalExibicao}
      />

      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle className="text-lg font-semibold">Criar novo aviso</DialogTitle>
            <DialogDescription>
              Defina o texto, o local de exibição e o comportamento do aviso.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="titulo">Título</Label>
              <Input id="titulo" value={titulo} onChange={(event) => setTitulo(event.target.value)} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="descricao">Descrição</Label>
              <Textarea
                id="descricao"
                rows={5}
                value={descricao}
                onChange={(event) => setDescricao(event.target.value)}
              />
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>Local de exibição</Label>
                <select
                  value={novoLocalExibicao}
                  onChange={(event) => setNovoLocalExibicao(event.target.value)}
                  className="h-10 w-full rounded-md border border-gray-300 px-3 text-sm text-gray-700"
                >
                  {LOCAIS_OPCOES.filter((opcao) => opcao.value !== "TODOS").map((opcao) => (
                    <option key={opcao.value} value={opcao.value}>
                      {opcao.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label>Frequência</Label>
                <select
                  value={frequenciaExibicao}
                  onChange={(event) => setFrequenciaExibicao(event.target.value)}
                  className="h-10 w-full rounded-md border border-gray-300 px-3 text-sm text-gray-700"
                >
                  {FREQUENCIA_OPCOES.map((opcao) => (
                    <option key={opcao.value} value={opcao.value}>
                      {opcao.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="ativoDe">Ativo de</Label>
                <Input id="ativoDe" type="datetime-local" value={ativoDe} onChange={(event) => setAtivoDe(event.target.value)} />
              </div>

              <div className="space-y-2">
                <Label htmlFor="ativoAte">Ativo até</Label>
                <Input id="ativoAte" type="datetime-local" value={ativoAte} onChange={(event) => setAtivoAte(event.target.value)} />
              </div>
            </div>

            <label className="flex items-center gap-3 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={permiteDispensar}
                onChange={(event) => setPermiteDispensar(event.target.checked)}
                className="accent-pink-500"
              />
              <span>Permitir que o aviso seja dispensado pelo usuário</span>
            </label>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={fecharModal} disabled={salvando}>
              Cancelar
            </Button>
            <Button onClick={criarAviso} disabled={salvando} className="bg-[#FC1EAD] hover:bg-[#e01a9a]">
              {salvando ? "Criando..." : "Criar aviso"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
