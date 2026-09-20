'use client'

import {
  CheckCircleIcon,
  HandRaisedIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CookieOptions } from '@/components/site/cookie-options'
import {
  CONSENT_EVENT,
  defaultConsent,
  persistCookieConsent,
  readCookieConsent,
  type ConsentState,
} from '@/lib/cookie-consent'

export function CookiePreferences() {
  const router = useRouter()
  const [consent, setConsent] = useState<ConsentState>(defaultConsent)
  const [loaded, setLoaded] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const sync = () => {
      setConsent(readCookieConsent() ?? defaultConsent)
      setLoaded(true)
    }
    sync()
    window.addEventListener(CONSENT_EVENT, sync)
    return () => window.removeEventListener(CONSENT_EVENT, sync)
  }, [])

  const persistConsent = (next: ConsentState) => {
    setSaving(true)
    setError(null)
    try {
      setConsent(persistCookieConsent(next))
    } catch {
      setError('Nao foi possivel salvar as preferencias. Tente novamente.')
    } finally {
      setSaving(false)
    }
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
          <CookieOptions consent={consent} onChange={setConsent} disabled={!loaded || saving} />

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
          {error ? <p role="alert" className="text-sm text-red-600">{error}</p> : null}
        </CardContent>
      </Card>

      <div className="mt-10 flex flex-wrap items-center justify-between gap-3">
        <Button variant="ghost" onClick={() => router.back()}>
          Voltar
        </Button>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            disabled={!loaded || saving}
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
            disabled={!loaded || saving}
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
