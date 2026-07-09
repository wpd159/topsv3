"use client"

import Link from "next/link"
import type { ReactNode } from "react"
import { useMemo } from "react"
import { getPublicSiteOrigin } from "@/lib/public-site-assets"

const MD_LINK = /^\[([^\]]*)\]\(([^)]+)\)/

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
}

function stripBlockedRegions(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
}

function stripEventHandlers(html: string): string {
  return html.replace(/\s*on[a-z][a-z0-9-]*\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "")
}

function stripDangerousVoidTags(html: string): string {
  return html.replace(
    /<\/?(?:meta|link|object|embed|base|svg|form|input|button|img|video|audio|source|track)[^>]*>/gi,
    ""
  )
}

function isAllowedHref(href: string): boolean {
  const h = href.trim()
  if (!h) return false
  const lower = h.toLowerCase()
  if (lower.startsWith("javascript:")) return false
  if (lower.startsWith("data:")) return false
  if (lower.startsWith("vbscript:")) return false
  if (lower.startsWith("//")) return false
  if (/^https?:\/\//i.test(h)) {
    try {
      new URL(h)
      return true
    } catch {
      return false
    }
  }
  if (h.startsWith("/") && !h.startsWith("//")) {
    return !/[<>"`]/.test(h)
  }
  return false
}

function hostnameKey(host: string): string {
  return host.replace(/^www\./i, "").toLowerCase()
}

function isExternalHref(href: string): boolean {
  const origin = getPublicSiteOrigin()
  if (href.startsWith("/")) return false
  try {
    const u = new URL(href)
    const base = new URL(origin)
    return hostnameKey(u.hostname) !== hostnameKey(base.hostname) || u.protocol !== base.protocol
  } catch {
    return true
  }
}

function toInternalAppPath(href: string): string {
  if (href.startsWith("/")) return href
  const u = new URL(href)
  const path = `${u.pathname}${u.search}${u.hash}`
  return path || "/"
}

function normalizeAllowlistedOpenClose(html: string): string {
  return html
    .replace(/<(strong|em|b|i|h2|h3|ul|ol|li|p)(\s[^>]*)?>/gi, (_, tag: string) => `<${tag.toLowerCase()}>`)
    .replace(/<\/(strong|em|b|i|h2|h3|ul|ol|li|p)>/gi, (_, tag: string) => `</${tag.toLowerCase()}>`)
    .replace(/<br\s*\/?>/gi, "<br />")
}

const SPLIT_RICH = /(<\/?(?:strong|em|b|i|h2|h3|ul|ol|li|p)>|<br \/>)/gi

function isRichSplitToken(part: string): boolean {
  return part === "<br />" || /^<\/?(strong|em|b|i|h2|h3|ul|ol|li|p)>$/.test(part)
}

function emitNormalizedTag(tagText: string): string {
  if (tagText === "<br />") return "<br />"
  const m = tagText.match(/^<\/?([a-z0-9]+)>/i)
  if (!m) return escapeHtml(tagText)
  const name = m[1].toLowerCase()
  if (tagText.startsWith("</")) return `</${name}>`
  return `<${name}>`
}

function sanitizeRichTextChunk(chunk: string): string {
  let s = stripBlockedRegions(chunk)
  s = stripEventHandlers(s)
  s = stripDangerousVoidTags(s)
  s = normalizeAllowlistedOpenClose(s)

  const parts = s.split(SPLIT_RICH)
  let out = ""
  for (const part of parts) {
    if (!part) continue
    if (isRichSplitToken(part)) {
      out += emitNormalizedTag(part)
    } else {
      out += escapeHtml(part).replace(/\n/g, "<br />")
    }
  }
  return out
}

function parseLeadingHtmlAnchor(
  s: string,
  start: number
): { href: string; text: string; end: number } | null {
  const slice = s.slice(start)
  if (!/^<a\b/i.test(slice)) return null
  const gt = s.indexOf(">", start)
  if (gt === -1) return null
  const openTag = s.slice(start, gt + 1)
  const hrefMatch = openTag.match(/\bhref\s*=\s*(["'])([^"']*)\1/i)
  if (!hrefMatch) return null
  const href = hrefMatch[2].trim()
  if (!isAllowedHref(href)) return null
  const close = s.toLowerCase().indexOf("</a>", gt + 1)
  if (close === -1) return null
  const inner = s.slice(gt + 1, close)
  const textOnly = inner.replace(/<[^>]+>/g, "")
  return { href, text: textOnly, end: close + "</a>".length }
}

function findNextMarkdownLink(s: string, from: number): { start: number; text: string; href: string; end: number } | null {
  let i = from
  while (i < s.length) {
    const j = s.indexOf("[", i)
    if (j === -1) return null
    const m = s.slice(j).match(MD_LINK)
    if (!m) {
      i = j + 1
      continue
    }
    const href = m[2].trim()
    if (isAllowedHref(href)) {
      return { start: j, text: m[1], href, end: j + m[0].length }
    }
    i = j + 1
  }
  return null
}

function findNextHtmlAnchor(s: string, from: number): { start: number; text: string; href: string; end: number } | null {
  const lower = s.toLowerCase()
  let pos = from
  while (pos < s.length) {
    const i = lower.indexOf("<a", pos)
    if (i === -1) return null
    const parsed = parseLeadingHtmlAnchor(s, i)
    if (parsed) {
      return { start: i, text: parsed.text, href: parsed.href, end: parsed.end }
    }
    pos = i + 2
  }
  return null
}

function BlogBodyLink({ href, label }: { href: string; label: string }) {
  const external = isExternalHref(href)

  if (!external) {
    const path = toInternalAppPath(href)
    return (
      <Link href={path} className="text-[#FC1EAD] underline hover:opacity-80">
        {label}
      </Link>
    )
  }

  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="text-[#FC1EAD] underline hover:opacity-80"
    >
      {label}
    </a>
  )
}

function renderParagraphNodes(paragraph: string, baseKey: string): ReactNode[] {
  const nodes: ReactNode[] = []
  let pos = 0
  let n = 0

  while (pos < paragraph.length) {
    const md = findNextMarkdownLink(paragraph, pos)
    const ha = findNextHtmlAnchor(paragraph, pos)

    let next: { start: number; text: string; href: string; end: number } | null = null
    if (md && ha) {
      next = md.start <= ha.start ? md : ha
    } else if (md) {
      next = md
    } else if (ha) {
      next = ha
    }

    if (!next) {
      const tail = paragraph.slice(pos)
      if (tail) {
        const html = sanitizeRichTextChunk(tail)
        nodes.push(
          <span
            key={`${baseKey}-t-${n++}`}
            dangerouslySetInnerHTML={{ __html: html }}
          />
        )
      }
      break
    }

    if (next.start > pos) {
      const chunk = paragraph.slice(pos, next.start)
      const html = sanitizeRichTextChunk(chunk)
      nodes.push(
        <span key={`${baseKey}-t-${n++}`} dangerouslySetInnerHTML={{ __html: html }} />
      )
    }

    nodes.push(
      <BlogBodyLink key={`${baseKey}-a-${n++}`} href={next.href} label={next.text} />
    )
    pos = next.end
  }

  return nodes
}

type Props = {
  conteudo: string
  className?: string
  paragraphClassName?: string
}

export function SafeBlogPostBody({
  conteudo,
  className = "prose prose-gray mt-8 max-w-none",
  paragraphClassName = "mb-5 text-base leading-8 text-gray-700",
}: Props) {
  const blocks = useMemo(() => {
    const raw = conteudo ?? ""
    return raw.split(/\n{2,}/)
  }, [conteudo])

  return (
    <article className={className}>
      {blocks.map((block, index) => (
        <div key={index} className={paragraphClassName}>
          {renderParagraphNodes(block, `p${index}`)}
        </div>
      ))}
    </article>
  )
}
