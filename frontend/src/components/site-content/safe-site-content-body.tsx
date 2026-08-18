import Link from 'next/link'
import type { ReactNode } from 'react'

const MARKDOWN_LINK = /\[([^\]]+)\]\(([^)]+)\)/g
const CANONICAL_ORIGIN = 'https://topsdojob.com'
const INSTITUTIONAL_LINKS = [
  { label: 'Política de Verificação Etária', href: '/politicas/verificacao-etaria' },
  { label: 'Termos de Uso', href: '/termos-de-uso' },
] as const
const INSTITUTIONAL_LABELS = INSTITUTIONAL_LINKS
  .map(({ label }) => label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  .join('|')
const INSTITUTIONAL_LINK = new RegExp(
  `\\[(${INSTITUTIONAL_LABELS})\\]\\(([^)]+)\\)|(${INSTITUTIONAL_LABELS})`,
  'g',
)

function safeHref(value: string): string | null {
  const href = value.trim()
  if (href.startsWith('/') && !href.startsWith('//') && !/[<>"`]/.test(href)) {
    return href
  }
  if (!/^https?:\/\//i.test(href)) return null
  try {
    const parsed = new URL(href)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? href : null
  } catch {
    return null
  }
}

function inlineNodes(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = []
  let cursor = 0
  let index = 0

  for (const match of text.matchAll(MARKDOWN_LINK)) {
    const start = match.index ?? 0
    if (start > cursor) {
      nodes.push(text.slice(cursor, start))
    }
    const href = safeHref(match[2])
    if (!href) {
      nodes.push(match[0])
    } else if (href.startsWith('/')) {
      nodes.push(
        <Link
          key={`${keyPrefix}-link-${index++}`}
          href={href}
          className="text-[#d91891] underline underline-offset-2"
        >
          {match[1]}
        </Link>,
      )
    } else {
      nodes.push(
        <a
          key={`${keyPrefix}-link-${index++}`}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="text-[#d91891] underline underline-offset-2"
        >
          {match[1]}
        </a>,
      )
    }
    cursor = start + match[0].length
  }

  if (cursor < text.length) {
    nodes.push(text.slice(cursor))
  }
  return nodes
}

function institutionalHref(label: string, candidate?: string): string | null {
  const institutional = INSTITUTIONAL_LINKS.find((item) => item.label === label)
  if (!institutional) return null
  if (!candidate) return institutional.href

  try {
    const parsed = new URL(candidate.trim(), CANONICAL_ORIGIN)
    if (
      parsed.origin !== CANONICAL_ORIGIN ||
      parsed.pathname !== institutional.href ||
      parsed.username ||
      parsed.password ||
      parsed.search ||
      parsed.hash
    ) {
      return null
    }
    return institutional.href
  } catch {
    return null
  }
}

function institutionalInlineNodes(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = []
  let cursor = 0
  let index = 0

  for (const match of text.matchAll(INSTITUTIONAL_LINK)) {
    const start = match.index ?? 0
    if (start > cursor) nodes.push(text.slice(cursor, start))

    const label = match[1] ?? match[3]
    const href = institutionalHref(label, match[2])
    if (!href) {
      nodes.push(match[0])
    } else {
      nodes.push(
        <Link
          key={`${keyPrefix}-institutional-${index++}`}
          href={href}
          className="text-[#d91891] underline underline-offset-2"
        >
          {label}
        </Link>,
      )
    }
    cursor = start + match[0].length
  }

  if (cursor < text.length) nodes.push(text.slice(cursor))
  return nodes
}

export function SafeInstitutionalText({ content }: { content: string }) {
  return <>{institutionalInlineNodes(content, 'institutional-text')}</>
}

type Block =
  | { type: 'heading'; level: 2 | 3 | 4; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'unordered-list'; items: string[] }
  | { type: 'ordered-list'; items: string[] }

function parseBlocks(content: string): Block[] {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const blocks: Block[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index].trim()
    if (!line) {
      index += 1
      continue
    }

    const heading = line.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      blocks.push({
        type: 'heading',
        level: (heading[1].length + 1) as 2 | 3 | 4,
        text: heading[2],
      })
      index += 1
      continue
    }

    if (/^[-*]\s+/.test(line)) {
      const items: string[] = []
      while (index < lines.length && /^[-*]\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^[-*]\s+/, ''))
        index += 1
      }
      blocks.push({ type: 'unordered-list', items })
      continue
    }

    if (/^\d+\.\s+/.test(line)) {
      const items: string[] = []
      while (index < lines.length && /^\d+\.\s+/.test(lines[index].trim())) {
        items.push(lines[index].trim().replace(/^\d+\.\s+/, ''))
        index += 1
      }
      blocks.push({ type: 'ordered-list', items })
      continue
    }

    const paragraph: string[] = [line]
    index += 1
    while (
      index < lines.length &&
      lines[index].trim() &&
      !/^(#{1,3})\s+/.test(lines[index].trim()) &&
      !/^[-*]\s+/.test(lines[index].trim()) &&
      !/^\d+\.\s+/.test(lines[index].trim())
    ) {
      paragraph.push(lines[index].trim())
      index += 1
    }
    blocks.push({ type: 'paragraph', text: paragraph.join(' ') })
  }
  return blocks
}

export function SafeSiteContentBody({
  content,
  centered = false,
  unavailable = false,
  institutionalLinks = false,
}: {
  content: string
  centered?: boolean
  unavailable?: boolean
  institutionalLinks?: boolean
}) {
  const blocks = parseBlocks(content)
  const renderInline = institutionalLinks ? institutionalInlineNodes : inlineNodes

  return (
    <article
      data-site-content-state={unavailable ? 'unavailable' : 'published'}
      className={`space-y-4 leading-relaxed text-gray-700 ${
        centered ? 'text-center' : 'text-left'
      }`}
    >
      {blocks.map((block, index) => {
        const key = `site-content-${index}`
        if (block.type === 'heading') {
          const Heading = `h${block.level}` as 'h2' | 'h3' | 'h4'
          return (
            <Heading key={key} className="pt-3 text-xl font-semibold text-gray-900">
              {renderInline(block.text, key)}
            </Heading>
          )
        }
        if (block.type === 'unordered-list' || block.type === 'ordered-list') {
          const List = block.type === 'ordered-list' ? 'ol' : 'ul'
          return (
            <List
              key={key}
              className={`space-y-2 pl-6 text-left ${
                block.type === 'ordered-list' ? 'list-decimal' : 'list-disc'
              }`}
            >
              {block.items.map((item, itemIndex) => (
                <li key={`${key}-${itemIndex}`}>{renderInline(item, `${key}-${itemIndex}`)}</li>
              ))}
            </List>
          )
        }
        return <p key={key}>{renderInline(block.text, key)}</p>
      })}
    </article>
  )
}
