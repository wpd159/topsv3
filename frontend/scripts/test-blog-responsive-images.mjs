import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const frontendRoot = fileURLToPath(new URL('../', import.meta.url))
const sourceRoot = path.join(frontendRoot, 'src')
const React = require('react')
const { renderToStaticMarkup } = require('react-dom/server')
const ts = require('typescript')
const Image = require('next/image').default
const { ImageConfigContext } = require('next/dist/shared/lib/image-config-context.shared-runtime')
const { imageConfigDefault } = require('next/dist/shared/lib/image-config')
const { ImageOptimizerCache } = require('next/dist/server/image-optimizer')

// The real public components and Next Image render here. Only API transport,
// navigation and article body are synthetic; no backend or remote image is read.
globalThis.fetch = () => { throw new Error('Unexpected network request in blog image test') }

const publicR2Host = 'pub-567428d3703244d483815a05a1e0e0d9.r2.dev'
const coverUrl = `https://${publicR2Host}/public-media/blog/synthetic/cover.jpg`
const ogUrl = `https://${publicR2Host}/public-media/blog/synthetic/og.jpg`
const logoUrl = 'https://topsdojob.com/logo-finallllll.webp'
const privateR2Url = 'https://00000000000000000000000000000000.r2.cloudflarestorage.com/private.jpg'

function compileFile(file, stubs = {}) {
  const output = ts.transpileModule(readFileSync(file, 'utf8'), {
    fileName: file,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
  }).outputText
  const compiledModule = { exports: {} }
  new Function('require', 'module', 'exports', output)(
    name => Object.hasOwn(stubs, name) ? stubs[name] : require(name),
    compiledModule,
    compiledModule.exports,
  )
  return compiledModule.exports
}

function compile(relativePath, stubs = {}) {
  return compileFile(path.join(sourceRoot, relativePath), stubs)
}

// Loading next.config.ts with Next's config loader would require a temporary
// compiled file and its unrelated SEO import. Evaluate the actual config with
// only that unrelated import stubbed, then apply Next's documented defaults.
const nextConfig = compileFile(path.join(frontendRoot, 'next.config.ts'), {
  './src/lib/seo/search-indexing-policy': {
    NEXT_NOINDEX_ROUTE_SOURCES: [],
    resolveSearchIndexingPolicy: () => ({ publicIndexingEnabled: true }),
  },
}).default
const config = { images: { ...imageConfigDefault, ...nextConfig.images } }

const mockLink = ({ href, children, ...props }) => React.createElement('a', { href, ...props }, children)
const siteAssets = compile('lib/public-site-assets.ts')
const blogCategories = compile('lib/blog-categories.ts')
assert.equal(siteAssets.getPublicLogoUrl(), logoUrl)

const category = {
  id: 'category-synthetic', nome: 'Categoria sintética', slug: 'categoria-sintetica',
  ativa: true, ordem: 1, postCountPublicados: 1, versao: 1,
}
const post = {
  id: 'post-synthetic', titulo: 'Post editorial sintético', slug: 'post-sintetico',
  resumo: 'Resumo sintético.', categoria: category.nome, categoriaId: category.id,
  categoriaSlug: category.slug, imagemUrl: coverUrl, ogImageUrl: ogUrl,
  autorNome: 'Equipe sintética', status: 'PUBLICADO', seoTitle: 'Título sintético',
  seoDescription: 'Descrição sintética', publishedAt: '2026-09-01T12:00:00Z',
  updatedAt: '2026-09-01T12:00:00Z', conteudo: '<p>Corpo sintético.</p>',
  versao: 1, sitemapPriority: 0.7, changeFrequency: 'weekly',
}
let categoryPosts = [post]
const { BlogContent } = compile('components/blog/blog-content.tsx', {
  'next/link': mockLink,
  '@/lib/blog-categories': blogCategories,
  '@/lib/public-site-assets': siteAssets,
})
const { default: BlogCategoryPage } = compile('app/(public-routes)/blog/categoria/[slug]/page.tsx', {
  'next/link': mockLink,
  'next/navigation': { notFound: () => { throw new Error('Unexpected notFound') } },
  '@/lib/api-contract': { ApiContractError: class ApiContractError extends Error {} },
  '@/lib/blog-api': {
    fetchPublicBlogCategorias: async () => [category],
    fetchPublicBlogPostsByCategoria: async () => categoryPosts,
  },
  '@/lib/public-site-assets': siteAssets,
  '@/lib/seo/public-url': {
    buildPublicPath: (...parts) => `/${parts.join('/')}`,
    buildPublicUrl: route => `https://topsdojob.com${route}`,
  },
  '@/lib/seo/search-indexing-policy': { buildPublicRobotsMetadata: () => ({ index: true }) },
})
const { default: BlogPostPageClient } = compile('app/(public-routes)/blog/[slug]/blog-post-page-client.tsx', {
  'next/link': mockLink,
  '@/lib/blog/safe-blog-body': { SafeBlogPostBody: () => null },
  '@/lib/public-site-assets': siteAssets,
})

function render(element) {
  return renderToStaticMarkup(React.createElement(
    ImageConfigContext.Provider,
    { value: config.images },
    element,
  ))
}

function images(html) {
  return [...html.matchAll(/<img\b[^>]*>/g)].map(match => match[0])
}

function attribute(image, name) {
  return image.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`))?.[1]?.replaceAll('&amp;', '&') ?? null
}

function candidates(image) {
  const srcSet = attribute(image, 'srcSet')
  assert.ok(srcSet, `Expected responsive srcSet: ${image}`)
  return srcSet.split(', ').map(entry => {
    const [url, descriptor] = entry.split(' ')
    const parsed = new URL(url, 'https://topsdojob.com')
    assert.equal(parsed.pathname, '/_next/image')
    return { source: parsed.searchParams.get('url'), width: Number(parsed.searchParams.get('w')), descriptor }
  })
}

function assertOptimized(image, expectedSource, expectedAlt) {
  assert.equal(attribute(image, 'alt'), expectedAlt)
  assert.equal(attribute(image, 'loading'), 'lazy')
  assert.ok(attribute(image, 'class')?.includes('object-cover'))
  const options = candidates(image)
  assert.ok(options.length >= 2)
  assert.ok(options.every(option => option.source === expectedSource))
  return options
}

function assertFallback(image) {
  assert.equal(attribute(image, 'src'), logoUrl)
  assert.equal(attribute(image, 'srcSet'), null)
  assert.equal(attribute(image, 'alt'), post.titulo)
}

const indexImages = images(render(React.createElement(BlogContent, { posts: [post], categories: [category] })))
assert.equal(indexImages.length, 2, 'Index must render one cover and one recent-post thumbnail')
const indexOptions = assertOptimized(indexImages[0], coverUrl, post.titulo)
assert.match(attribute(indexImages[0], 'sizes'), /33vw/)
assert.ok(indexOptions.some(option => option.width === 640), 'Cover needs a mobile-sized candidate')
assert.ok(indexOptions.some(option => option.width === 1080), 'Cover needs a desktop-sized candidate')
const thumbOptions = assertOptimized(indexImages[1], coverUrl, post.titulo)
assert.equal(attribute(indexImages[1], 'width'), '48')
assert.equal(attribute(indexImages[1], 'height'), '48')
assert.deepEqual(thumbOptions.map(option => [option.width, option.descriptor]), [[48, '1x'], [96, '2x']])
assert.equal(new URL(attribute(indexImages[1], 'src'), 'https://topsdojob.com').searchParams.get('w'), '96')

const fallbackIndexImages = images(render(React.createElement(BlogContent, {
  posts: [{ ...post, imagemUrl: null }], categories: [category],
})))
assert.equal(fallbackIndexImages.length, 2)
fallbackIndexImages.forEach(assertFallback)

async function renderCategory(currentPost) {
  categoryPosts = [currentPost]
  const page = await BlogCategoryPage({ params: Promise.resolve({ slug: category.slug }) })
  return images(render(page))
}
const categoryImages = await renderCategory(post)
assert.equal(categoryImages.length, 1)
assertOptimized(categoryImages[0], coverUrl, post.titulo)
assert.match(attribute(categoryImages[0], 'sizes'), /360px/)
assert.match(attribute(categoryImages[0], 'sizes'), /40vw/)
const fallbackCategoryImages = await renderCategory({ ...post, imagemUrl: null })
assert.equal(fallbackCategoryImages.length, 1)
assertFallback(fallbackCategoryImages[0])

const articleImages = images(render(React.createElement(BlogPostPageClient, { post })))
assert.equal(articleImages.length, 1)
assertOptimized(articleImages[0], coverUrl, post.titulo)
assert.match(attribute(articleImages[0], 'sizes'), /976px/)
const ogImages = images(render(React.createElement(BlogPostPageClient, { post: { ...post, imagemUrl: null } })))
assert.equal(ogImages.length, 1)
assertOptimized(ogImages[0], ogUrl, post.titulo)
const fallbackArticleImages = images(render(React.createElement(BlogPostPageClient, {
  post: { ...post, imagemUrl: null, ogImageUrl: null },
})))
assert.equal(fallbackArticleImages.length, 1)
assertFallback(fallbackArticleImages[0])

function optimizerError(url) {
  return ImageOptimizerCache.validateParams(
    { headers: {} }, { url, w: '640', q: '75' }, config, false,
  ).errorMessage
}
assert.equal(optimizerError(coverUrl), undefined)
assert.equal(optimizerError(privateR2Url), '"url" parameter is not allowed')

console.log('BLOG_RESPONSIVE_IMAGES_RESULT=OK index=2 category=1 article=1 fallback=logo+og')
