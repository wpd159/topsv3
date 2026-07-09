'use client'

export type BlogPost = {
  id: number
  titulo: string
  slug: string
  resumo: string
  conteudo: string
  capaUrl?: string
  autor: string
  tags: string[]
  categoria: string
  status: 'RASCUNHO' | 'PUBLICADO'
  criadoEm: string
  atualizadoEm: string
  visualizacoes: number
}

const LS_KEY = 'tdj_blog_posts_v1'

const SEED: BlogPost[] = [
  {
    id: 1,
    titulo: 'Como atrair clientes com anúncios de qualidade',
    slug: 'como-atrair-clientes-com-anuncios-de-qualidade',
    resumo: 'Guia prático de copy, fotos e reputação para aumentar conversões.',
    conteudo:
      '## Introdução\n\nQualidade vende. Neste guia você aprende como descrever seu serviço, escolher fotos, e criar confiança...\n\n### Dicas rápidas\n- Escreva claro\n- Use 2 fotos nítidas\n- Mantenha preços consistentes',
    capaUrl: '/blog/capa-default-1.jpg',
    autor: 'Equipe Tops do Job',
    tags: ['conversao', 'copy', 'branding'],
    categoria: 'Marketing',
    status: 'PUBLICADO',
    criadoEm: new Date().toISOString(),
    atualizadoEm: new Date().toISOString(),
    visualizacoes: 128,
  },
  {
    id: 2,
    titulo: 'Política de fotos: o que passa e o que não passa na moderação',
    slug: 'politica-de-fotos-moderacao',
    resumo: 'Checklist para aprovação rápida sem retrabalho.',
    conteudo:
      'Fotos aprovadas seguem regras simples: iluminação, enquadramento e respeito às diretrizes da plataforma...',
    capaUrl: '/blog/capa-default-2.jpg',
    autor: 'Moderation Team',
    tags: ['moderacao', 'fotos'],
    categoria: 'Diretrizes',
    status: 'RASCUNHO',
    criadoEm: new Date().toISOString(),
    atualizadoEm: new Date().toISOString(),
    visualizacoes: 32,
  },
]

function read(): BlogPost[] {
  try {
    const raw = localStorage.getItem(LS_KEY)
    if (!raw) {
      localStorage.setItem(LS_KEY, JSON.stringify(SEED))
      return SEED
    }
    return JSON.parse(raw) as BlogPost[]
  } catch {
    return SEED
  }
}

function write(posts: BlogPost[]) {
  localStorage.setItem(LS_KEY, JSON.stringify(posts))
}

export const blogStorage = {
  list: (): BlogPost[] => read().sort((a, b) => (a.id < b.id ? 1 : -1)),
  get: (id: number): BlogPost | undefined => read().find(p => p.id === id),
  create: (post: Omit<BlogPost, 'id' | 'criadoEm' | 'atualizadoEm' | 'visualizacoes'>): BlogPost => {
    const all = read()
    const id = Math.max(0, ...all.map(p => p.id)) + 1
    const now = new Date().toISOString()
    const novo: BlogPost = { ...post, id, criadoEm: now, atualizadoEm: now, visualizacoes: 0 }
    write([novo, ...all])
    return novo
  },
  update: (id: number, patch: Partial<BlogPost>): BlogPost | undefined => {
    const all = read()
    const idx = all.findIndex(p => p.id === id)
    if (idx < 0) return undefined
    const atualizado = { ...all[idx], ...patch, atualizadoEm: new Date().toISOString() }
    all[idx] = atualizado
    write(all)
    return atualizado
  },
  remove: (id: number) => {
    const all = read().filter(p => p.id !== id)
    write(all)
  },
}

export function slugify(text: string) {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
}
