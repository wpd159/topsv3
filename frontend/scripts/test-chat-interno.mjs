import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8')

const client = read('src/app/(private-routes)/chat/chat-client.tsx')
const sidebar = read('src/components/chat/sidebar-chat.tsx')
const windowSource = read('src/components/chat/chat.tsx')
const api = read('src/lib/chat-api.ts')
const auth = read('src/context/AuthContext.tsx')
const publicProfile = read('src/app/(public-routes)/anuncios/usuario/[username]/anuncios-usuario-client.tsx')

assert.match(api, /\/chat\/conversas/)
assert.match(api, /Idempotency-Key/)
assert.match(api, /credentials: 'include'/)
assert.match(api, /csrfHeaderName/)
assert.doesNotMatch(api, /NEXT_PUBLIC_API_URL/)

assert.match(client, /MESSAGE_POLL_MS = 4_000/)
assert.match(client, /CONVERSATION_POLL_MS = 10_000/)
assert.match(client, /pendingMessage\.current/)
assert.match(client, /pending\?\.body === message/)
assert.match(client, /marcarChatConversaComoLida/)
assert.match(client, /topsv3:chat-nao-lidas/)
assert.match(client, /searchParams\.get\('conversa'\)/)
assert.match(client, /router\.push\(`\/chat\?conversa=/)
assert.match(client, /router\.replace\(`\/chat\?conversa=/)
assert.match(client, /openConversation\(selected, 'none'\)/)
assert.doesNotMatch(client, /CONTRATO_BACKEND_AUSENTE|pendingContractError/)

assert.match(sidebar, /Nova conversa/)
assert.match(sidebar, /Digite o username\.\.\./)
assert.match(sidebar, /Buscar conversa\.\.\./)
assert.match(sidebar, /item\.naoLidas/)
assert.match(sidebar, /Nenhuma conversa iniciada/)

assert.match(windowSource, /Digite uma mensagem\.\.\./)
assert.match(windowSource, /Ver anuncios/)
assert.match(windowSource, /whitespace-pre-wrap break-words/)
assert.match(windowSource, /maxLength=\{2000\}/)
assert.match(windowSource, /text-base/)
assert.doesNotMatch(windowSource, /dangerouslySetInnerHTML/)

assert.match(auth, /fetchChatNaoLidas/)
assert.match(auth, /setInterval\(\(\) => void atualizarNovasMensagens\(\), 10_000\)/)
assert.match(publicProfile, /\/chat\?usuario=/)
assert.doesNotMatch(publicProfile, /runPendingAction\('Abrir chat'\)/)

console.log('chat interno frontend: OK')
