import { appendFileSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:http'

const [stateFile, requestLog] = process.argv.slice(2)
if (!stateFile || !requestLog) {
  throw new Error('uso: node fixture-release-atomica.mjs STATE_FILE REQUEST_LOG')
}

let target = 'old'
let oldRunning = true
let candidateRunning = true

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
    else if (command === 'start-old') oldRunning = true
    else if (command === 'remove-candidate') candidateRunning = false
    else if (command === 'start-candidate') candidateRunning = true
    else {
      response.writeHead(400).end()
      return
    }
    record({ type: 'control', command, target, oldRunning, candidateRunning })
    response.writeHead(204).end()
    return
  }

  const available =
    (target === 'old' && oldRunning) ||
    (target === 'candidate' && candidateRunning)
  const status = available ? 200 : 502
  record({ type: 'request', path, target, status })
  response.writeHead(status, {
    'content-type': 'text/plain; charset=utf-8',
    'x-release': available ? target : 'none',
  })
  response.end(available ? `${target}\n` : 'upstream unavailable\n')
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
