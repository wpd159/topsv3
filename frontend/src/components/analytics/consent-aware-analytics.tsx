'use client'

import { useEffect, useState } from 'react'

type ConsentState = {
  analytics?: boolean
}

const CONSENT_COOKIE = 'cookie_consent'
const CONSENT_EVENT = 'tops:cookie-consent-updated'
const GA_MEASUREMENT_ID = 'G-E0CNBH6WPM'
const GA4_CONFIGURED = process.env.NEXT_PUBLIC_ANALYTICS_ENABLED !== 'false'
const GA_SCRIPT_ID = 'google-analytics-loader'

declare global {
  interface Window {
    dataLayer?: IArguments[]
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
    if (!GA4_CONFIGURED) return

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
    if (!GA4_CONFIGURED || typeof window === 'undefined') return

    if (!analyticsEnabled) {
      window.gtag?.('consent', 'update', {
        analytics_storage: 'denied',
        ad_storage: 'denied',
        ad_user_data: 'denied',
        ad_personalization: 'denied',
      })
      return
    }

    if (typeof window.gtag !== 'function') {
      window.dataLayer = window.dataLayer ?? []
      window.gtag = function gtag() {
        window.dataLayer?.push(arguments)
      }
      window.gtag('js', new Date())
      window.gtag('consent', 'default', {
        analytics_storage: 'granted',
        ad_storage: 'denied',
        ad_user_data: 'denied',
        ad_personalization: 'denied',
      })
      window.gtag('config', GA_MEASUREMENT_ID, {
        page_path: window.location.pathname,
      })
    } else {
      window.gtag('consent', 'update', {
        analytics_storage: 'granted',
        ad_storage: 'denied',
        ad_user_data: 'denied',
        ad_personalization: 'denied',
      })
    }

    if (!document.getElementById(GA_SCRIPT_ID)) {
      const script = document.createElement('script')
      script.id = GA_SCRIPT_ID
      script.async = true
      script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`
      document.head.appendChild(script)
    }
  }, [analyticsEnabled])

  return null
}
