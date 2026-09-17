'use client'

import {
  CheckCircleIcon,
  HandRaisedIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

type ConsentState = {
  necessary: boolean
  functional: boolean
  analytics: boolean
  marketing: boolean
  ts?: number
}

const CONSENT_COOKIE = 'cookie_consent'
const CONSENT_EVENT = 'tops:cookie-consent-updated'
const DAYS_180 = 60 * 60 * 24 * 180

const defaultConsent: ConsentState = {
  necessary: true,
  functional: false,
  analytics: false,
  marketing: false,
}

function setCookie(name: string, value: string, maxAgeSeconds = DAYS_180) {
  document.cookie = `${name}=${encodeURIComponent(value)}; Path=/; Max-Age=${maxAgeSeconds}; SameSite=Lax`
}

function getCookie(name: string) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function pushConsentEvent(consent: ConsentState) {
  ;(window as Window & { dataLayer?: unknown[] }).dataLayer?.push({
    event: 'consent_update',
    consent: {
      necessary: consent.necessary,
      functional: consent.functional,
      analytics: consent.analytics,
      marketing: consent.marketing,
    },
  })
}

export function CookiePreferences() {
  const router = useRouter()
  const [consent, setConsent] = useState<ConsentState>(defaultConsent)
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    try {
      const raw = getCookie(CONSENT_COOKIE)
      if (raw) {
        const parsed = JSON.parse(raw) as ConsentState
        setConsent({
          necessary: true,
          functional: Boolean(parsed.functional),
          analytics: parsed.analytics === true,
          marketing: Boolean(parsed.marketing),
          ts: parsed.ts,
        })
      }
    } catch {
      // A malformed preference cookie is ignored in favor of the safe defaults.
    } finally {
      setLoaded(true)
    }
  }, [])

  const persistConsent = (next: ConsentState) => {
    setSaving(true)
    setCookie(CONSENT_COOKIE, JSON.stringify(next))
    pushConsentEvent(next)
    window.dispatchEvent(new CustomEvent(CONSENT_EVENT, { detail: next }))
    setConsent(next)
    setSaving(false)
  }

  return (
    <div>
      <Card>
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
              <p className="text-sm text-gray-600">
                Sempre ativos para seguranca, login e integridade do site.
              </p>
            </div>
            <Badge variant="secondary" className="bg-gray-100">
              Sempre ativo
            </Badge>
          </div>
          <Separator />

          <RowSwitch
            label="Funcionais"
            description="Lembrar idioma e preferencias de interface."
            checked={consent.functional}
            onChange={(value) => setConsent((current) => ({ ...current, functional: value }))}
          />
          <RowSwitch
            label="Analytics"
            description="Metricas agregadas para evoluir o produto."
            checked={consent.analytics}
            onChange={(value) => setConsent((current) => ({ ...current, analytics: value }))}
          />
          <RowSwitch
            label="Marketing"
            description="Mensuracao e relevancia de campanhas."
            checked={consent.marketing}
            onChange={(value) => setConsent((current) => ({ ...current, marketing: value }))}
          />

          <div className="flex flex-wrap gap-3 pt-2">
            <Button
              variant="secondary"
              onClick={() =>
                persistConsent({
                  necessary: true,
                  functional: false,
                  analytics: false,
                  marketing: false,
                  ts: Date.now(),
                })
              }
              disabled={!loaded || saving}
              className="gap-2"
            >
              <XCircleIcon className="h-5 w-5" />
              Rejeitar nao essenciais
            </Button>
            <Button
              onClick={() =>
                persistConsent({
                  necessary: true,
                  functional: true,
                  analytics: true,
                  marketing: true,
                  ts: Date.now(),
                })
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

          {consent.ts ? (
            <p className="text-xs text-gray-500">
              Ultimo registro de consentimento: {new Date(consent.ts).toLocaleString('pt-BR')}
            </p>
          ) : null}
        </CardContent>
      </Card>

      <div className="mt-10 flex flex-wrap items-center justify-between gap-3">
        <Button variant="ghost" onClick={() => router.back()}>
          Voltar
        </Button>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            onClick={() =>
              persistConsent({
                necessary: true,
                functional: false,
                analytics: false,
                marketing: false,
                ts: Date.now(),
              })
            }
          >
            <XCircleIcon className="mr-2 h-5 w-5" />
            Rejeitar nao essenciais
          </Button>
          <Button
            onClick={() =>
              persistConsent({
                necessary: true,
                functional: true,
                analytics: true,
                marketing: true,
                ts: Date.now(),
              })
            }
            className="bg-[#FC1EAD] hover:bg-[#e01a9a]"
          >
            <CheckCircleIcon className="mr-2 h-5 w-5" />
            Aceitar tudo
          </Button>
        </div>
      </div>
    </div>
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
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
        checked ? 'bg-[#FC1EAD]' : 'bg-gray-300'
      }`}
    >
      <span
        className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
          checked ? 'translate-x-5' : 'translate-x-1'
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
