'use client'

import { useEffect, useMemo, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { SITE_CONTENT_KEYS, getFallbackSiteContent, type SiteContentEntry, type SiteContentKey } from "@/lib/site-content"

const LABELS: Record<SiteContentKey, string> = {
  "quem-somos": "Quem somos",
  "footer-resumo-institucional": "Resumo institucional do footer",
  "termos-de-uso": "Termos de uso",
  "politica-privacidade": "Privacidade",
  "politica-cookies": "Cookies",
  "consentimento-promocional": "Consentimento promocional",
  verificacao: "Verificação",
  "popup-login": "Avisos de login",
  "texto-whatsapp": "Texto WhatsApp",
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export default function AdminSiteContentPage() {
  const [entries, setEntries] = useState<Record<string, SiteContentEntry>>({})
  const [savingKey, setSavingKey] = useState<string | null>(null)
  const [openKey, setOpenKey] = useState<SiteContentKey | null>(null)

  const normalizedKeys = useMemo(() => SITE_CONTENT_KEYS, [])

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch(apiUrl("/admin/site-content"), {
          credentials: "include",
          cache: "no-store",
        })

        if (!res.ok) {
          throw new Error("Falha ao carregar conteúdos do site.")
        }

        const data = await res.json()
        const next: Record<string, SiteContentEntry> = {}

        for (const key of normalizedKeys) {
          next[key] = getFallbackSiteContent(key)
        }

        for (const item of Array.isArray(data) ? data : []) {
          if (item?.contentKey) {
            next[item.contentKey] = {
              ...next[item.contentKey],
              ...item,
            }
          }
        }

        setEntries(next)
      } catch (error: any) {
        toast.error(error?.message || "Não foi possível carregar os conteúdos.")
      }
    }

    load()
  }, [normalizedKeys])

  const updateEntry = (key: SiteContentKey, field: "titulo" | "corpo", value: string) => {
    setEntries((prev) => ({
      ...prev,
      [key]: {
        ...(prev[key] || getFallbackSiteContent(key)),
        contentKey: key,
        [field]: value,
      },
    }))
  }

  const saveEntry = async (key: SiteContentKey) => {
    const entry = entries[key] || getFallbackSiteContent(key)
    setSavingKey(key)

    try {
      const res = await fetch(apiUrl(`/admin/site-content/${key}`), {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          titulo: entry.titulo,
          corpo: entry.corpo,
        }),
      })

      if (!res.ok) {
        throw new Error("Falha ao salvar conteúdo.")
      }

      const saved = await res.json()
      setEntries((prev) => ({ ...prev, [key]: saved }))
      toast.success(`${LABELS[key]} atualizado com sucesso.`)
    } catch (error: any) {
      toast.error(error?.message || "Não foi possível salvar o conteúdo.")
    } finally {
      setSavingKey(null)
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold text-gray-800">Conteúdo do Site</h1>
        <p className="text-sm text-gray-500">
          Edite os textos institucionais, avisos de login, mensagem de segurança do WhatsApp e textos de aceite.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-5">
        {normalizedKeys.map((key) => {
          const entry = entries[key] || getFallbackSiteContent(key)
          const isOpen = openKey === key

          return (
            <Card key={key} className="border border-gray-100 p-0 shadow-sm">
              <button
                type="button"
                className="flex w-full items-center justify-between gap-4 p-5 text-left"
                aria-expanded={isOpen}
                aria-controls={`${key}-editor`}
                onClick={() => setOpenKey((current) => (current === key ? null : key))}
              >
                <div>
                  <h2 className="text-lg font-semibold text-gray-900">{LABELS[key]}</h2>
                  <p className="text-xs text-gray-500">Chave interna: {key}</p>
                  <p className="text-xs text-gray-500">
                    Versão atual: {entry.contentVersion ?? 1}
                    {entry.contentHash ? ` • Hash: ${entry.contentHash.slice(0, 12)}...` : ""}
                  </p>
                </div>

                <span className="rounded-full border border-gray-200 px-3 py-1 text-xs font-medium text-gray-600">
                  {isOpen ? "Recolher" : "Expandir"}
                </span>
              </button>

              {isOpen && (
                <div id={`${key}-editor`} className="space-y-4 border-t border-gray-100 p-5">
                <div className="space-y-2">
                  <Label htmlFor={`${key}-title`}>Título</Label>
                  <Input
                    id={`${key}-title`}
                    value={entry.titulo}
                    onChange={(event) => updateEntry(key, "titulo", event.target.value)}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor={`${key}-body`}>Conteúdo</Label>
                  <Textarea
                    id={`${key}-body`}
                    rows={key === "termos-de-uso" ? 12 : 8}
                    value={entry.corpo}
                    onChange={(event) => updateEntry(key, "corpo", event.target.value)}
                  />
                </div>

                <div className="flex justify-end">
                  <Button
                    onClick={() => saveEntry(key)}
                    className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
                    disabled={savingKey === key}
                  >
                    {savingKey === key ? "Salvando..." : "Salvar"}
                  </Button>
                </div>
              </div>
              )}
            </Card>
          )
        })}
      </div>
    </section>
  )
}
