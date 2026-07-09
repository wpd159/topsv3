"use client"

import { useMemo, useState, useEffect, JSX } from "react"
import { useRouter } from "next/navigation"
import {
  MagnifyingGlassIcon,
  QuestionMarkCircleIcon,
  ChatBubbleLeftRightIcon,
  BoltIcon,
  ShieldCheckIcon,
  CreditCardIcon,
  ListBulletIcon,
  CheckCircleIcon,
  XCircleIcon,
  ChevronDownIcon,
} from "@heroicons/react/24/outline"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { useAuth } from "@/context/AuthContext"
import { LoginModal } from "@/components/modals/login-modal"

// ====== Tipagem ======
type FAQ = {
  id: string
  pergunta: string
  resposta: string
  categoria: "conta" | "pagamentos" | "seguranca" | "anuncios" | "geral"
}

// ====== Página ======
export default function FAQPage() {
  const router = useRouter()
  const { usuario } = useAuth()

  const [faqs, setFaqs] = useState<FAQ[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState("")
  const [activeCat, setActiveCat] = useState<FAQ["categoria"] | "todas">("todas")
  const [loginOpen, setLoginOpen] = useState(false)

  // ====== Busca na API ======
  useEffect(() => {
    const fetchFaqs = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/faq`, {
          credentials: "include",
        })
        if (!res.ok) throw new Error("Erro ao buscar FAQs")
        const data = await res.json()
        setFaqs(data)
      } catch (err) {
      } finally {
        setLoading(false)
      }
    }
    fetchFaqs()
  }, [])

  // ====== Busca + filtro ======
  const results = useMemo(() => {
    const q = query.trim().toLowerCase()
    return faqs.filter((f) => {
      const byCat = activeCat === "todas" ? true : f.categoria === activeCat
      if (!q) return byCat
      const hay = (f.pergunta + " " + f.resposta).toLowerCase()
      return byCat && hay.includes(q)
    })
  }, [query, activeCat, faqs])

  // ====== Hash deep-link (ex.: /faq#5) ======
  useEffect(() => {
    if (typeof window === "undefined") return
    const hash = window.location.hash?.replace("#", "")
    if (!hash) return
    window.dispatchEvent(new CustomEvent("faq-open", { detail: { id: hash } }))
  }, [])

  const onFalarComSuporte = () => {
    if (usuario) {
      window.dispatchEvent(new Event("open-suporte-ticket"))
    } else {
      setLoginOpen(true)
    }
  }

  const categories: { key: FAQ["categoria"] | "todas"; label: string; icon: JSX.Element }[] = [
    { key: "todas", label: "Todas", icon: <ListBulletIcon className="h-4 w-4" /> },
    { key: "conta", label: "Conta", icon: <QuestionMarkCircleIcon className="h-4 w-4" /> },
    { key: "pagamentos", label: "Pagamentos", icon: <CreditCardIcon className="h-4 w-4" /> },
    { key: "seguranca", label: "Segurança", icon: <ShieldCheckIcon className="h-4 w-4" /> },
    { key: "anuncios", label: "Anúncios", icon: <BoltIcon className="h-4 w-4" /> },
    { key: "geral", label: "Geral", icon: <ChatBubbleLeftRightIcon className="h-4 w-4" /> },
  ]

  return (
    <section className="mx-auto max-w-6xl py-10">
      {/* Hero */}
      <div className="mb-8">
        <div className="flex items-center gap-3">
          <QuestionMarkCircleIcon className="h-7 w-7 text-[#FC1EAD]" />
          <h1 className="text-3xl font-bold tracking-tight">FAQ — Perguntas Frequentes</h1>
        </div>
        <p className="text-gray-600 mt-2">
          Respostas rápidas. Se algo não encaixar, o suporte está a um clique.
        </p>
      </div>

      {/* Busca + CTA */}
      <Card className="mb-6">
        <CardHeader className="pb-2">
          <CardTitle className="text-base flex items-center gap-2">
            <MagnifyingGlassIcon className="h-5 w-5" /> Encontre sua resposta
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <MagnifyingGlassIcon className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Busque por palavra-chave (ex.: pagamento, 2FA, anúncio...)"
              className="pl-9 py-6"
            />
          </div>
          <Button
            onClick={onFalarComSuporte}
            className="bg-[#FC1EAD] hover:bg-[#e01a9a] gap-2 py-6"
          >
            <ChatBubbleLeftRightIcon className="h-5 w-5" />
            Falar com suporte
          </Button>
        </CardContent>
      </Card>

      {/* Categorias */}
      <div className="flex flex-wrap gap-2 mb-6">
        {categories.map((c) => {
          const active = activeCat === c.key
          const count = faqs.filter((f) => f.categoria === c.key).length
          return (
            <button
              key={c.key as string}
              onClick={() => setActiveCat(c.key)}
              className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition ${
                active
                  ? "border-[#FC1EAD] text-[#FC1EAD] bg-[#FC1EAD]/10"
                  : "border-gray-300 text-gray-700 hover:bg-gray-50"
              }`}
            >
              {c.icon}
              {c.label}
              {c.key !== "todas" && count > 0 && (
                <Badge variant="secondary" className="ml-1">
                  {count}
                </Badge>
              )}
            </button>
          )
        })}
      </div>

      {/* Resultados */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base flex items-center gap-2">
            <ListBulletIcon className="h-5 w-5" />
            {loading
              ? "Carregando..."
              : `${results.length} resultado${results.length !== 1 ? "s" : ""}`}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className="text-gray-500 text-center py-10">Carregando FAQs...</p>
          ) : results.length === 0 ? (
            <div className="text-center py-12">
              <XCircleIcon className="mx-auto h-7 w-7 text-gray-400 mb-2" />
              <p className="text-gray-600">
                Nada encontrado para sua busca. Tente outra palavra, ou fale com o suporte.
              </p>
              <div className="mt-4">
                <Button onClick={onFalarComSuporte} className="bg-[#FC1EAD] hover:bg-[#e01a9a]">
                  Abrir ticket
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              {results.map((item) => (
                <FAQItem key={item.id} item={item} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Ações finais */}
      <div className="flex items-center justify-between mt-10">
        <Button variant="ghost" onClick={() => router.back()}>
          Voltar
        </Button>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setQuery("")}>
            Limpar busca
          </Button>
          <Button onClick={onFalarComSuporte} className="bg-[#FC1EAD] hover:bg-[#e01a9a] gap-2">
            <ChatBubbleLeftRightIcon className="h-5 w-5" />
            Ainda com dúvidas? Fale com a gente
          </Button>
        </div>
      </div>

      {/* Login se não autenticado */}
      <LoginModal open={loginOpen} onOpenChange={setLoginOpen} />
    </section>
  )
}

// ====== Item de FAQ ======
function FAQItem({ item }: { item: FAQ }) {
  const [open, setOpen] = useState(false)

  useEffect(() => {
    const onOpenById = (e: Event) => {
      const detail = (e as CustomEvent).detail as { id?: string }
      if (detail?.id === String(item.id)) setOpen(true)
    }
    window.addEventListener("faq-open", onOpenById as EventListener)
    return () => window.removeEventListener("faq-open", onOpenById as EventListener)
  }, [item.id])

  return (
    <div id={String(item.id)} className="border rounded-lg">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={`${item.id}-panel`}
        className="w-full flex items-center justify-between px-4 py-3 text-left"
      >
        <div className="flex items-center gap-2">
          <QuestionMarkCircleIcon className="h-5 w-5 text-gray-700" />
          <span className="font-medium">{item.pergunta}</span>
        </div>
        <ChevronDownIcon className={`h-5 w-5 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>
      <div
        id={`${item.id}-panel`}
        role="region"
        aria-hidden={!open}
        className={`px-4 pb-4 transition-[max-height,opacity] duration-200 ease-out ${
          open ? "opacity-100" : "opacity-0 max-h-0 overflow-hidden"
        } ${open ? "max-h-[400px]" : ""}`}
      >
        {open && (
          <>
            <Separator className="my-2" />
            <p className="text-sm text-gray-700">{item.resposta}</p>
            <div className="mt-3 flex items-center gap-2 text-xs text-gray-500">
              <CheckCircleIcon className="h-4 w-4 text-emerald-600" />
              <span>Essa resposta ajudou?</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
