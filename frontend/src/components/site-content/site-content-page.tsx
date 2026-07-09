"use client"

import { useEffect, useState } from "react"
import { fetchPublicSiteContent, getFallbackSiteContent, type SiteContentKey } from "@/lib/site-content"

export function SiteContentPage({
  contentKey,
  fallbackTitle,
  fallbackBody,
  centered = true,
}: {
  contentKey: SiteContentKey
  fallbackTitle: string
  fallbackBody: string
  centered?: boolean
}) {
  const [title, setTitle] = useState(fallbackTitle)
  const [body, setBody] = useState(fallbackBody)

  useEffect(() => {
    let active = true
    fetchPublicSiteContent(contentKey)
      .then((entry) => {
        if (!active) return
        setTitle(entry.titulo || fallbackTitle || getFallbackSiteContent(contentKey).titulo)
        setBody(entry.corpo || fallbackBody || getFallbackSiteContent(contentKey).corpo)
      })
      .catch(() => {
        if (!active) return
        setTitle(fallbackTitle)
        setBody(fallbackBody)
      })
    return () => {
      active = false
    }
  }, [contentKey, fallbackBody, fallbackTitle])

  return (
    <section className="max-w-5xl mx-auto px-6 py-10 text-center space-y-6">
      <h1 className="text-2xl md:text-4xl font-extrabold text-gray-900 leading-tight">
        {title}
      </h1>

      <div
        className={`text-gray-600 leading-relaxed space-y-4 text-justify ${
          centered ? "md:text-center" : ""
        }`}
      >
        {body.split(/\n{2,}/).map((paragraph, index) => (
          <p key={`${contentKey}-${index}`}>{paragraph}</p>
        ))}
      </div>
    </section>
  )
}
