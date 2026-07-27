import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

const api = source('lib/blog-api.ts')
const adminApi = source('lib/admin-blog-api.ts')
const adminList = source('app/(painel-admin)/admin/blog/page.tsx')
const form = source('app/(painel-admin)/admin/blog/components/blog-post-form.tsx')
const publicList = source('app/(public-routes)/blog/page.tsx')
const publicDetail = source('app/(public-routes)/blog/[slug]/page.tsx')
const detailView = source('app/(public-routes)/blog/[slug]/blog-post-page-client.tsx')
const safeBody = source('lib/blog/safe-blog-body.tsx')
const sitemap = source('app/sitemap.ts')
const combined = [
  api,
  adminApi,
  adminList,
  form,
  publicList,
  publicDetail,
  detailView,
  safeBody,
  sitemap,
].join('\n')

assert.match(api, /\/blog-posts\/public/)
assert.match(api, /PUBLIC_BLOG_CACHE_TAG/)
assert.match(adminApi, /\/blog-posts\/upload-image/)
assert.match(adminApi, /credentials: 'include'/)
assert.match(adminApi, /XSRF/)
assert.match(adminList, /Buscar por título, autor ou slug/)
assert.match(adminList, /Retirar/)
assert.match(adminList, /Arquivar/)
assert.match(form, /Salvar rascunho/)
assert.match(form, /Post publicado com sucesso/)
assert.match(form, /SafeBlogPostBody/)
assert.match(publicList, /await Promise\.all/)
assert.match(publicList, /export const dynamic = "force-dynamic"/)
assert.match(publicList, /<h1/)
assert.match(publicDetail, /"@type": "BlogPosting"/)
assert.match(publicDetail, /datePublished/)
assert.match(publicDetail, /alternates: \{ canonical \}/)
assert.match(detailView, /<h1/)
assert.match(detailView, /<time/)
assert.match(safeBody, /<article className=\{className\} dangerouslySetInnerHTML=/)
assert.doesNotMatch(safeBody, /<span[\s\S]*dangerouslySetInnerHTML=/)
assert.match(sitemap, /blog-posts\/public\/sitemap`, true/)
assert.doesNotMatch(combined, /BackendContractPendingError|PENDING_BACKEND_CONTRACTS\.blog/)
assert.doesNotMatch(combined, /localStorage|sessionStorage/)
assert.doesNotMatch(combined, /href\s*=\s*["']javascript:/i)

console.log('BLOG_V3_RESULT=OK')
