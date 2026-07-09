'use client'

import { useEffect, useMemo, useState } from 'react'
import Script from 'next/script'
import { Analytics } from '@vercel/analytics/react'

type ConsentState = {
  analytics?: boolean
}

const CONSENT_COOKIE = 'cookie_consent'
const CONSENT_EVENT = 'tops:cookie-consent-updated'
const GA_MEASUREMENT_ID = 'G-E0CNBH6WPM'

declare global {
  interface Window {
    gtag?: (...args: unknown[]) => void
  }
}

function getCookie(name: string) {
  if (typeof document === 'undefined') return null
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function analyticsEnabledFromCookie() {
  try {
    const raw = getCookie(CONSENT_COOKIE)
    if (!raw) return true
    const parsed = JSON.parse(raw) as ConsentState
    if (typeof parsed.analytics === 'boolean') {
      return parsed.analytics
    }
    return true
  } catch {
    return true
  }
}

export function ConsentAwareAnalytics() {
  const [analyticsEnabled, setAnalyticsEnabled] = useState(false)

  useEffect(() => {
    const syncConsent = () => {
      setAnalyticsEnabled(analyticsEnabledFromCookie())
    }

    syncConsent()
    window.addEventListener(CONSENT_EVENT, syncConsent)
    window.addEventListener('focus', syncConsent)

    return () => {
      window.removeEventListener(CONSENT_EVENT, syncConsent)
      window.removeEventListener('focus', syncConsent)
    }
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.gtag !== 'function') return

    window.gtag('consent', 'update', {
      analytics_storage: analyticsEnabled ? 'granted' : 'denied',
      ad_storage: 'denied',
      ad_user_data: 'denied',
      ad_personalization: 'denied',
    })
  }, [analyticsEnabled])

  const gaBootstrap = useMemo(
    () => `
      window.dataLayer = window.dataLayer || [];
      function gtag(){window.dataLayer.push(arguments);}
      window.gtag = gtag;
      gtag('js', new Date());
      gtag('consent', 'default', {
        analytics_storage: 'granted',
        ad_storage: 'denied',
        ad_user_data: 'denied',
        ad_personalization: 'denied'
      });
      gtag('config', '${GA_MEASUREMENT_ID}', {
        page_path: window.location.pathname,
      });
    `,
    []
  )

  if (!analyticsEnabled) {
    return null
  }

  return (
    <>
      <Script
        src={`https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`}
        strategy="afterInteractive"
      />
      <Script id="google-analytics" strategy="afterInteractive">
        {gaBootstrap}
      </Script>
      <Analytics />
    </>
  )
}
