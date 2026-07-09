"use client"

import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import {
  ChartBarIcon,
  CheckCircleIcon,
  Cog6ToothIcon,
  HandRaisedIcon,
  InformationCircleIcon,
  MegaphoneIcon,
  ShieldCheckIcon,
  XCircleIcon,
} from "@heroicons/react/24/outline"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { fetchPublicSiteContent, getFallbackSiteContent } from "@/lib/site-content"

type ConsentState = {
  necessary: boolean
  functional: boolean
  analytics: boolean
  marketing: boolean
  ts?: number
}

const CONSENT_COOKIE = "cookie_consent"
const CONSENT_EVENT = "tops:cookie-consent-updated"
const DAYS_180 = 60 * 60 * 24 * 180

const defaultConsent: ConsentState = {
  necessary: true,
  functional: false,
  analytics: true,
  marketing: false,
}

const cookieCatalog = [
  { name: "session", provider: "topsdojob.com", type: "Necessario", duration: "Sessao", purpose: "Mantem a sessao do usuario autenticado." },
  { name: "csrf_token", provider: "topsdojob.com", type: "Necessario", duration: "Sessao", purpose: "Protecao contra CSRF em formularios." },
  { name: "tpz_locale", provider: "topsdojob.com", type: "Funcional", duration: "30 dias", purpose: "Recorda idioma e preferencias de interface." },
  { name: "_ga", provider: "google.com", type: "Analytics", duration: "2 anos", purpose: "Diferencia usuarios para metricas de navegacao." },
  { name: "_gid", provider: "google.com", type: "Analytics", duration: "24 horas", purpose: "Diferencia usuarios em sessoes curtas." },
  { name: "_gcl_au", provider: "google.com", type: "Marketing", duration: "90 dias", purpose: "Atribuicao de conversoes em campanhas." },
  { name: "_fbp", provider: "facebook.com", type: "Marketing", duration: "90 dias", purpose: "Apoia segmentacao e mensuracao de campanhas." },
]

function setCookie(name: string, value: string, maxAgeSeconds = DAYS_180) {
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; Max-Age=${maxAgeSeconds}; SameSite=Lax`
}

function getCookie(name: string) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function pushConsentEvent(consent: ConsentState) {
  ;(window as Window & { dataLayer?: unknown[] }).dataLayer?.push({
    event: "consent_update",
    consent: {
      necessary: consent.necessary,
      functional: consent.functional,
      analytics: consent.analytics,
      marketing: consent.marketing,
    },
  })
}

export default function CookiesPage() {
  const router = useRouter()
  const fallback = getFallbackSiteContent("politica-cookies")
  const [contentTitle, setContentTitle] = useState(fallback.titulo)
  const [contentBody, setContentBody] = useState(fallback.corpo)
  const [consent, setConsent] = useState<ConsentState>(defaultConsent)
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const lastUpdated = useMemo(() => "22/03/2026", [])

  useEffect(() => {
    try {
      const raw = getCookie(CONSENT_COOKIE)
      if (raw) {
        const parsed = JSON.parse(raw) as ConsentState
        setConsent({
          necessary: true,
          functional: !!parsed.functional,
          analytics: typeof parsed.analytics === "boolean" ? parsed.analytics : true,
          marketing: !!parsed.marketing,
          ts: parsed.ts,
        })
      }
    } catch {
      // noop
    } finally {
      setLoaded(true)
    }
  }, [])

  useEffect(() => {
    fetchPublicSiteContent("politica-cookies")
      .then((entry) => {
        setContentTitle(entry.titulo || fallback.titulo)
        setContentBody(entry.corpo || fallback.corpo)
      })
      .catch(() => {
        setContentTitle(fallback.titulo)
        setContentBody(fallback.corpo)
      })
  }, [fallback.corpo, fallback.titulo])

  const persistConsent = (next: ConsentState) => {
    setSaving(true)
    setCookie(CONSENT_COOKIE, JSON.stringify(next))
    pushConsentEvent(next)
    window.dispatchEvent(new CustomEvent(CONSENT_EVENT, { detail: next }))
    setConsent(next)
    setSaving(false)
  }

  return (
    <section className="mx-auto max-w-5xl px-6 py-10">
      <div className="mb-8">
        <div className="flex items-center gap-3">
          <ShieldCheckIcon className="h-7 w-7 text-[#FC1EAD]" />
          <h1 className="text-3xl font-bold tracking-tight">{contentTitle}</h1>
        </div>
        <p className="mt-1 text-sm text-gray-500">Ultima atualizacao: {lastUpdated}</p>
        <div className="mt-4 space-y-3 text-gray-700">
          {contentBody.split(/\n{2,}/).map((paragraph, index) => (
            <p key={`cookies-copy-${index}`}>{paragraph}</p>
          ))}
        </div>
      </div>

      <Card className="mb-8">
        <CardHeader className="pb-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <InformationCircleIcon className="h-5 w-5" />
            Resumo rapido
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-gray-700">
          <p>• Necessarios: seguranca, login e funcionamento basico.</p>
          <p>• Funcionais: idioma, filtros e preferencias de interface.</p>
          <p>• Analytics: metricas para evoluir produto e navegacao.</p>
          <p>• Marketing: mensuracao e relevancia de campanhas quando permitido.</p>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {[
          {
            icon: <ShieldCheckIcon className="h-5 w-5 text-emerald-600" />,
            title: "Cookies necessarios",
            description: "Essenciais para autenticacao, integridade da sessao e recursos basicos do site.",
          },
          {
            icon: <Cog6ToothIcon className="h-5 w-5 text-gray-700" />,
            title: "Cookies funcionais",
            description: "Mantem idioma, preferencias e escolhas de experiencia do usuario.",
          },
          {
            icon: <ChartBarIcon className="h-5 w-5 text-blue-600" />,
            title: "Cookies de analytics",
            description: "Apoiam metricas agregadas para melhorar paginas, fluxos e desempenho.",
          },
          {
            icon: <MegaphoneIcon className="h-5 w-5 text-pink-600" />,
            title: "Cookies de marketing",
            description: "Utilizados somente quando permitido para mensuracao e campanhas.",
          },
        ].map((item) => (
          <Card key={item.title}>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-base">
                {item.icon}
                {item.title}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-gray-700">{item.description}</CardContent>
          </Card>
        ))}
      </div>

      <Card className="mt-8">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <HandRaisedIcon className="h-5 w-5" />
            Suas preferencias
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium">Necessarios</p>
              <p className="text-sm text-gray-600">Sempre ativos para seguranca, login e integridade do site.</p>
            </div>
            <Badge variant="secondary" className="bg-gray-100">Sempre ativo</Badge>
          </div>
          <Separator />

          <RowSwitch
            label="Funcionais"
            description="Lembrar idioma e preferencias de interface."
            checked={!!consent.functional}
            onChange={(value) => setConsent((prev) => ({ ...prev, functional: value }))}
          />
          <RowSwitch
            label="Analytics"
            description="Metricas agregadas para evoluir o produto."
            checked={!!consent.analytics}
            onChange={(value) => setConsent((prev) => ({ ...prev, analytics: value }))}
          />
          <RowSwitch
            label="Marketing"
            description="Mensuracao e relevancia de campanhas."
            checked={!!consent.marketing}
            onChange={(value) => setConsent((prev) => ({ ...prev, marketing: value }))}
          />

          <div className="flex flex-wrap gap-3 pt-2">
            <Button
              variant="secondary"
              onClick={() =>
                persistConsent({ necessary: true, functional: false, analytics: false, marketing: false, ts: Date.now() })
              }
              disabled={!loaded || saving}
              className="gap-2"
            >
              <XCircleIcon className="h-5 w-5" />
              Rejeitar nao essenciais
            </Button>
            <Button
              onClick={() =>
                persistConsent({ necessary: true, functional: true, analytics: true, marketing: true, ts: Date.now() })
              }
              disabled={!loaded || saving}
              className="gap-2 bg-[#FC1EAD] hover:bg-[#e01a9a]"
            >
              <CheckCircleIcon className="h-5 w-5" />
              Aceitar tudo
            </Button>
            <Button
              variant="outline"
              onClick={() => persistConsent({ ...consent, necessary: true, ts: Date.now() })}
              disabled={!loaded || saving}
            >
              Salvar preferencias
            </Button>
          </div>

          {consent.ts && (
            <p className="text-xs text-gray-500">
              Ultimo registro de consentimento: {new Date(consent.ts).toLocaleString("pt-BR")}
            </p>
          )}
        </CardContent>
      </Card>

      <Card className="mt-8">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <ChartBarIcon className="h-5 w-5" />
            Lista de cookies
          </CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Cookie</TableHead>
                <TableHead>Tipo</TableHead>
                <TableHead>Finalidade</TableHead>
                <TableHead>Validade</TableHead>
                <TableHead>Provedor</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {cookieCatalog.map((cookie) => (
                <TableRow key={`${cookie.name}-${cookie.provider}`}>
                  <TableCell className="font-medium">{cookie.name}</TableCell>
                  <TableCell><Badge variant="outline">{cookie.type}</Badge></TableCell>
                  <TableCell className="max-w-[420px]">{cookie.purpose}</TableCell>
                  <TableCell>{cookie.duration}</TableCell>
                  <TableCell>{cookie.provider}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <p className="mt-3 text-xs text-gray-500">
            A lista pode variar conforme novas integracoes, campanhas e ajustes de produto.
          </p>
        </CardContent>
      </Card>

      <div className="mt-10 flex items-center justify-between">
        <Button variant="ghost" onClick={() => router.back()}>
          Voltar
        </Button>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            onClick={() =>
              persistConsent({ necessary: true, functional: false, analytics: false, marketing: false, ts: Date.now() })
            }
            className="gap-2"
          >
            <XCircleIcon className="h-5 w-5" />
            Rejeitar nao essenciais
          </Button>
          <Button
            onClick={() =>
              persistConsent({ necessary: true, functional: true, analytics: true, marketing: true, ts: Date.now() })
            }
            className="gap-2 bg-[#FC1EAD] hover:bg-[#e01a9a]"
          >
            <CheckCircleIcon className="h-5 w-5" />
            Aceitar tudo
          </Button>
        </div>
      </div>
    </section>
  )
}

function ToggleSwitch({
  checked,
  onChange,
  label,
}: {
  checked: boolean
  onChange: (value: boolean) => void
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
        checked ? "bg-[#FC1EAD]" : "bg-gray-300"
      }`}
    >
      <span
        className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
          checked ? "translate-x-5" : "translate-x-1"
        }`}
      />
    </button>
  )
}

function RowSwitch({
  label,
  description,
  checked,
  onChange,
}: {
  label: string
  description: string
  checked: boolean
  onChange: (value: boolean) => void
}) {
  return (
    <div className="flex items-start justify-between gap-6">
      <div>
        <p className="font-medium">{label}</p>
        <p className="text-sm text-gray-600">{description}</p>
      </div>
      <ToggleSwitch checked={checked} onChange={onChange} label={label} />
    </div>
  )
}
