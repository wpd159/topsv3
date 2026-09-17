'use client'

import { useEffect, useRef } from 'react'
import { usePathname, useSearchParams } from 'next/navigation'
import {
  analyticsPermitido, atualizarPrivacidadeGA4, contextoPaginaGA4,
  GA_CONSENT_EVENT, GA_MEASUREMENT_ID, registrarEventoGA4,
} from '@/lib/analytics/ga4'

const GA_SCRIPT_ID = 'google-analytics-loader'
const deniedConsent = {
  analytics_storage: 'denied', ad_storage: 'denied',
  ad_user_data: 'denied', ad_personalization: 'denied',
}

export function ConsentAwareAnalytics() {
  const pathname = usePathname()
  const search = useSearchParams().toString()
  const ultimaNavegacao = useRef<string | null>(null)

  useEffect(() => {
    const syncConsent = () => {
      try {
        atualizarPrivacidadeGA4()
        if (!analyticsPermitido()) {
          ultimaNavegacao.current = null
          window.gtag?.('consent', 'update', deniedConsent)
          return
        }
        if (typeof window.gtag !== 'function') {
          window.dataLayer = window.dataLayer ?? []
          window.gtag = function gtag() {
            window.dataLayer?.push(arguments)
          }
          window.gtag('consent', 'default', deniedConsent)
          window.gtag('js', new Date())
        }
        window.gtag('consent', 'update', { ...deniedConsent, analytics_storage: 'granted' })
        window.gtag('config', GA_MEASUREMENT_ID, {
          send_page_view: false,
          ...contextoPaginaGA4(),
          allow_google_signals: false,
          allow_ad_personalization_signals: false,
        })
        if (!document.getElementById(GA_SCRIPT_ID)) {
          const script = document.createElement('script')
          script.id = GA_SCRIPT_ID
          script.async = true
          script.referrerPolicy = 'no-referrer'
          script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`
          document.head.appendChild(script)
        }
        // Chave completa somente em memória; o payload só admite page numérico.
        const navegacao = window.location.pathname + window.location.search
        if (ultimaNavegacao.current !== navegacao && registrarEventoGA4('page_view')) {
          ultimaNavegacao.current = navegacao
        }
      } catch {
        // Analytics indisponível não afeta navegação ou consentimento.
      }
    }
    syncConsent()
    window.addEventListener(GA_CONSENT_EVENT, syncConsent)
    window.addEventListener('focus', syncConsent)
    return () => {
      window.removeEventListener(GA_CONSENT_EVENT, syncConsent)
      window.removeEventListener('focus', syncConsent)
    }
  }, [pathname, search])

  return null
}
