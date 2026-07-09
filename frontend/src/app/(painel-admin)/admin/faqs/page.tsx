"use client"

import { useState, useEffect } from "react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  PencilSquareIcon,
  PlusCircleIcon,
  TrashIcon,
  MagnifyingGlassIcon,
  QuestionMarkCircleIcon,
} from "@heroicons/react/24/solid"
import {
  Table,
  TableHeader,
  TableHead,
  TableRow,
  TableBody,
  TableCell,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { Separator } from "@/components/ui/separator"
import { toast } from "sonner"

interface FAQ {
  id: number
  pergunta: string
  resposta: string
  categoria: "conta" | "pagamentos" | "seguranca" | "anuncios" | "geral"
}

export default function AdminFaqPage() {
  const [faqs, setFaqs] = useState<FAQ[]>([])
  const [busca, setBusca] = useState("")
  const [loading, setLoading] = useState(true)
  const [openDialog, setOpenDialog] = useState(false)
  const [editing, setEditing] = useState<FAQ | null>(null)

  const [novaPergunta, setNovaPergunta] = useState("")
  const [novaResposta, setNovaResposta] = useState("")
  const [novaCategoria, setNovaCategoria] = useState<FAQ["categoria"]>("geral")

  useEffect(() => {
    const fetchFaqs = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/faq`, {
          credentials: "include",
        })
        if (!res.ok) throw new Error("Erro ao carregar FAQs")
        const data = await res.json()
        setFaqs(data)
      } catch (err) {
      } finally {
        setLoading(false)
      }
    }
    fetchFaqs()
  }, [])

  const handleSalvar = async () => {
    try {
      const metodo = editing ? "PUT" : "POST"
      const url = editing
        ? `${process.env.NEXT_PUBLIC_API_URL}/faq/${editing.id}`
        : `${process.env.NEXT_PUBLIC_API_URL}/faq`

      const res = await fetch(url, {
        method: metodo,
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          pergunta: novaPergunta,
          resposta: novaResposta,
          categoria: novaCategoria.toUpperCase(),
        }),
      })

      if (!res.ok) throw new Error("Erro ao salvar FAQ")

      toast.success(editing ? "FAQ atualizado com sucesso!" : "FAQ criado com sucesso!")

      setOpenDialog(false)
      setEditing(null)
      setNovaPergunta("")
      setNovaResposta("")
      setNovaCategoria("geral")

      const data = await (await fetch(`${process.env.NEXT_PUBLIC_API_URL}/faq`, { credentials: "include" })).json()
      setFaqs(data)
    } catch (err) {
      toast.error("Erro ao salvar FAQ.")
    }
  }

  const handleEditar = (faq: FAQ) => {
    setEditing(faq)
    setNovaPergunta(faq.pergunta)
    setNovaResposta(faq.resposta)
    setNovaCategoria(faq.categoria)
    setOpenDialog(true)
  }

  const handleExcluir = async (id: number) => {
    if (!confirm("Tem certeza que deseja excluir esta FAQ?")) return
    try {
      await fetch(`${process.env.NEXT_PUBLIC_API_URL}/faq/${id}`, {
        method: "DELETE",
        credentials: "include",
      })
      toast.success("FAQ excluída com sucesso.")
      setFaqs((prev) => prev.filter((f) => f.id !== id))
    } catch {
      toast.error("Erro ao excluir FAQ.")
    }
  }

  const faqsFiltradas = faqs.filter(
    (f) =>
      f.pergunta.toLowerCase().includes(busca.toLowerCase()) ||
      f.resposta.toLowerCase().includes(busca.toLowerCase()) ||
      f.categoria.toLowerCase().includes(busca.toLowerCase())
  )

  return (
    <section>
      {/* 🧭 Header */}
      <div className="flex flex-col gap-1 mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Gerenciar FAQs</h1>
        <p className="text-sm text-gray-500">
          Crie, edite ou remova perguntas frequentes exibidas para os usuários.
        </p>
      </div>

      {/* 🔍 Filtros e busca */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <div className="relative max-w-md w-full">
          <MagnifyingGlassIcon className="w-5 h-5 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <Input
            placeholder="Buscar por palavra, categoria..."
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            className="pl-10"
          />
        </div>

        <Button
          onClick={() => {
            setEditing(null)
            setNovaPergunta("")
            setNovaResposta("")
            setNovaCategoria("geral")
            setOpenDialog(true)
          }}
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] flex items-center gap-2"
        >
          <PlusCircleIcon className="w-5 h-5" />
          Nova FAQ
        </Button>
      </div>

      {/* 📋 Lista */}
      <Card className="border border-gray-100 shadow-sm">
        <div className="px-5 py-4 border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent flex items-center gap-2">
          <QuestionMarkCircleIcon className="w-5 h-5 text-[#FC1EAD]" />
          <h3 className="text-base font-semibold text-gray-800">Lista de Perguntas</h3>
        </div>

        {loading ? (
          <p className="text-gray-500 text-center py-10">Carregando FAQs...</p>
        ) : faqsFiltradas.length === 0 ? (
          <p className="text-gray-400 text-center py-10">Nenhuma FAQ encontrada.</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow className="bg-gray-50 border-b text-[12px] text-gray-600 uppercase">
                  <TableHead className="w-1/3 py-3 px-6">Pergunta</TableHead>
                  <TableHead className="py-3 px-6">Categoria</TableHead>
                  <TableHead className="py-3 px-6">Resposta</TableHead>
                  <TableHead className="py-3 px-6 text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>

              <TableBody>
                {faqsFiltradas.map((faq, i) => (
                  <TableRow
                    key={faq.id}
                    className={`${
                      i % 2 === 0 ? "bg-white" : "bg-gray-50/40"
                    } hover:bg-[#FC1EAD]/5 transition-all`}
                  >
                    <TableCell className="px-6 py-3 font-medium text-gray-800">
                      {faq.pergunta}
                    </TableCell>
                    <TableCell className="px-6">
                      <Badge
                        variant="outline"
                        className={`text-xs capitalize border ${
                          faq.categoria === "pagamentos"
                            ? "border-blue-300 bg-blue-50 text-blue-700"
                            : faq.categoria === "anuncios"
                            ? "border-purple-300 bg-purple-50 text-purple-700"
                            : faq.categoria === "seguranca"
                            ? "border-yellow-300 bg-yellow-50 text-yellow-700"
                            : faq.categoria === "conta"
                            ? "border-pink-300 bg-pink-50 text-pink-700"
                            : "border-gray-300 bg-gray-50 text-gray-700"
                        }`}
                      >
                        {faq.categoria}
                      </Badge>
                    </TableCell>
                    <TableCell className="px-6 text-gray-600 truncate max-w-md">
                      {faq.resposta}
                    </TableCell>
                    <TableCell className="px-6 text-right space-x-2">
                      <Button
                        size="icon"
                        variant="ghost"
                        className="hover:bg-gray-100 border border-gray-200"
                        onClick={() => handleEditar(faq)}
                        title="Editar FAQ"
                      >
                        <PencilSquareIcon className="w-4 h-4 text-gray-700" />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="hover:bg-red-50 border border-gray-200"
                        onClick={() => handleExcluir(faq.id)}
                        title="Excluir FAQ"
                      >
                        <TrashIcon className="w-4 h-4 text-red-600" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      {/* ✏️ Modal */}
      <Dialog open={openDialog} onOpenChange={setOpenDialog}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="text-lg font-semibold text-gray-800">
              {editing ? "Editar FAQ" : "Nova FAQ"}
            </DialogTitle>
            <p className="text-sm text-gray-500">
              {editing
                ? "Atualize a pergunta e a resposta conforme necessário."
                : "Crie uma nova pergunta frequente que aparecerá no site público."}
            </p>
          </DialogHeader>

          <div className="space-y-4 mt-4">
            <div>
              <label className="text-sm text-gray-600">Pergunta</label>
              <Input
                value={novaPergunta}
                onChange={(e) => setNovaPergunta(e.target.value)}
                placeholder="Digite a pergunta..."
                className="mt-1"
              />
            </div>

            <div>
              <label className="text-sm text-gray-600">Resposta</label>
              <Input
                value={novaResposta}
                onChange={(e) => setNovaResposta(e.target.value)}
                placeholder="Digite a resposta..."
                className="mt-1"
              />
            </div>

            <div>
              <label className="text-sm text-gray-600">Categoria</label>
              <select
                value={novaCategoria}
                onChange={(e) =>
                  setNovaCategoria(e.target.value as FAQ["categoria"])
                }
                className="mt-1 w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus-visible:ring-[#FC1EAD]"
              >
                <option value="geral">Geral</option>
                <option value="conta">Conta</option>
                <option value="pagamentos">Pagamentos</option>
                <option value="seguranca">Segurança</option>
                <option value="anuncios">Anúncios</option>
              </select>
            </div>
          </div>

          <Separator className="my-4" />

          <DialogFooter className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => setOpenDialog(false)}
              className="border-gray-300 text-gray-600 hover:bg-gray-100"
            >
              Cancelar
            </Button>
            <Button
              onClick={handleSalvar}
              className="bg-[var(--brand-pink-contrast)] hover:bg-[#8f005f] text-white"
            >
              {editing ? "Salvar alterações" : "Criar FAQ"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
