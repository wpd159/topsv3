import { appendFileSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:http'

const [stateFile, requestLog] = process.argv.slice(2)
if (!stateFile || !requestLog) {
  throw new Error('uso: node fixture-release-atomica.mjs STATE_FILE REQUEST_LOG')
}

let target = 'old'
let oldRunning = true
let candidateRunning = true
let oldRemoved = false
let oldNetworkPresent = true
const activeRequests = { old: 0, candidate: 0 }

function record(entry) {
  appendFileSync(requestLog, `${JSON.stringify({ at: Date.now(), ...entry })}\n`)
}

const server = createServer((request, response) => {
  const path = request.url ?? '/'
  if (request.method === 'POST' && path.startsWith('/__control/')) {
    const command = path.slice('/__control/'.length)
    if (command === 'old') target = 'old'
    else if (command === 'candidate') target = 'candidate'
    else if (command === 'unavailable') target = 'unavailable'
    else if (command === 'stop-old') oldRunning = false
    else if (command === 'start-old') {
      oldRunning = true
      oldRemoved = false
      oldNetworkPresent = true
    }
    else if (command === 'remove-old-runtime') {
      if (oldRunning || activeRequests.old > 0) {
        response.writeHead(409).end()
        return
      }
      oldRemoved = true
      oldNetworkPresent = false
    }
    else if (command === 'remove-candidate') candidateRunning = false
    else if (command === 'start-candidate') candidateRunning = true
    else {
      response.writeHead(400).end()
      return
    }
    record({
      type: 'control',
      command,
      target,
      oldRunning,
      candidateRunning,
      oldRemoved,
      oldNetworkPresent,
      activeRequests,
    })
    response.writeHead(204).end()
    return
  }

  if (request.method === 'GET' && path === '/__active/old') {
    response.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' })
    response.end(`${activeRequests.old}\n`)
    return
  }
  if (request.method === 'GET' && path === '/__residual/old') {
    const residuals = Number(oldRunning) + Number(!oldRemoved) + Number(oldNetworkPresent)
    response.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' })
    response.end(`${residuals}\n`)
    return
  }

  const requestTarget = target
  const releaseRunning = () =>
    (requestTarget === 'old' && oldRunning) ||
    (requestTarget === 'candidate' && candidateRunning)
  const available = releaseRunning()
  const status = available ? 200 : 502
  record({ type: 'request', path, target: requestTarget, status })
  if (!available) {
    response.writeHead(status, {
      'content-type': 'text/plain; charset=utf-8',
      'x-release': 'none',
    })
    response.end('upstream unavailable\n')
    return
  }

  activeRequests[requestTarget] += 1
  let completed = false
  const complete = () => {
    if (completed) return
    completed = true
    activeRequests[requestTarget] -= 1
  }
  response.on('close', complete)

  const url = new URL(path, 'http://fixture.invalid')
  if (url.pathname === '/__long') {
    const requestedMs = Number.parseInt(url.searchParams.get('ms') ?? '16000', 10)
    const durationMs = Math.min(Math.max(requestedMs, 1), 30000)
    setTimeout(() => {
      if (!releaseRunning()) {
        response.destroy()
        return
      }
      response.writeHead(200, {
        'content-type': 'text/plain; charset=utf-8',
        'x-release': requestTarget,
      })
      response.end(`${requestTarget}-long\n`)
    }, durationMs)
    return
  }

  if (url.pathname === '/__stream') {
    const requestedChunks = Number.parseInt(url.searchParams.get('chunks') ?? '16', 10)
    const requestedInterval = Number.parseInt(url.searchParams.get('interval') ?? '1000', 10)
    const chunks = Math.min(Math.max(requestedChunks, 1), 40)
    const intervalMs = Math.min(Math.max(requestedInterval, 10), 2000)
    let sent = 0
    response.writeHead(200, {
      'content-type': 'application/octet-stream',
      'x-release': requestTarget,
    })
    const interval = setInterval(() => {
      if (!releaseRunning()) {
        clearInterval(interval)
        response.destroy()
        return
      }
      sent += 1
      response.write(`${requestTarget}-video-chunk-${sent}\n`)
      if (sent >= chunks) {
        clearInterval(interval)
        response.end()
      }
    }, intervalMs)
    response.on('close', () => clearInterval(interval))
    return
  }

  response.writeHead(200, {
    'content-type': 'text/plain; charset=utf-8',
    'x-release': requestTarget,
  })
  response.end(`${requestTarget}\n`)
})

server.listen(0, '127.0.0.1', () => {
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('porta local indisponivel')
  writeFileSync(stateFile, `${address.port}\n`)
})

function close() {
  server.close(() => process.exit(0))
  setTimeout(() => process.exit(1), 2_000).unref()
}

process.on('SIGTERM', close)
process.on('SIGINT', close)
