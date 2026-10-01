import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import ts from 'typescript'

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../src')
const source = (name) => readFileSync(path.join(sourceRoot, name), 'utf8')

function runtimeModule(name, imports = {}) {
  const compiled = ts.transpileModule(source(name), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: name,
    reportDiagnostics: true,
  })
  assert.deepEqual((compiled.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [])
  const module = { exports: {} }
  new Function('require', 'module', 'exports', compiled.outputText)((specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Fronteira não declarada: ${specifier}`)
    return imports[specifier]
  }, module, module.exports)
  return module.exports
}

function hooks() {
  const slots = []
  const effects = []
  let index = 0
  let dirty = false
  let tree
  let component
  const changed = (previous, next) => !previous || !next || next.some((value, i) => !Object.is(value, previous[i]))
  const react = {
    useState(initial) {
      const slot = index++
      slots[slot] ??= { value: typeof initial === 'function' ? initial() : initial }
      return [slots[slot].value, (next) => {
        const value = typeof next === 'function' ? next(slots[slot].value) : next
        dirty ||= !Object.is(value, slots[slot].value)
        slots[slot].value = value
      }]
    },
    useRef(initial) { const slot = index++; return slots[slot] ??= { current: initial } },
    useCallback(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) {
        slots[slot] = { value: callback, dependencies }
      }
      return slots[slot].value
    },
    useEffect(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) {
        const old = slots[slot]
        slots[slot] = { dependencies, cleanup: old?.cleanup }
        effects.push(() => { old?.cleanup?.(); slots[slot].cleanup = callback() })
      }
    },
  }
  function render() {
    index = 0
    dirty = false
    tree = component({ open: true, onOpenChange() {}, usuarioId: 'synthetic-user', nome: 'Conta sintética' })
    effects.splice(0).forEach((effect) => effect())
  }
  return {
    react,
    mount(next) { component = next; render() },
    async settle() {
      for (let iteration = 0; iteration < 12; iteration++) {
        await new Promise((resolve) => setImmediate(resolve))
        if (dirty) render()
      }
      assert.equal(dirty, false)
    },
    get tree() { return tree },
  }
}

function find(node, predicate) {
  if (Array.isArray(node)) return node.map((child) => find(child, predicate)).find(Boolean)
  if (!node || typeof node !== 'object') return null
  if (predicate(node)) return node
  return find(node.props?.children, predicate)
}

function text(node) {
  if (Array.isArray(node)) return node.map(text).join('')
  if (node && typeof node === 'object') return text(node.props?.children)
  return typeof node === 'string' ? node : ''
}

const element = (type, props) => ({ type, props: props ?? {} })
const jsxRuntime = { jsx: element, jsxs: element, Fragment: 'Fragment' }
const icons = new Proxy({}, { get: (_, key) => String(key) })
const contract = runtimeModule('lib/api-contract.ts')
const runner = hooks()
const calls = { saldo: 0, movimentos: 0, ajustar: [], confirmar: 0 }
const api = {
  saldo: async () => { calls.saldo++; return { saldoProjetado: 0 } },
  movimentos: async () => { calls.movimentos++; return { itens: [] } },
  ajustar: async (...args) => { calls.ajustar.push(args); return { saldoDepois: 500 } },
}
globalThis.window = { confirm: () => { calls.confirmar++; return true } }
const { AdminUsuarioCreditDialog } = runtimeModule('features/admin-usuarios/admin-usuario-credit-dialog.tsx', {
  react: runner.react,
  'react/jsx-runtime': jsxRuntime,
  'lucide-react': icons,
  '@/components/feedback/contract-state': { ContractState: 'ContractState' },
  '@/components/ui/badge': { Badge: 'Badge' },
  '@/components/ui/button': { Button: 'Button' },
  '@/components/ui/dialog': {
    Dialog: 'Dialog', DialogContent: 'DialogContent', DialogDescription: 'DialogDescription',
    DialogFooter: 'DialogFooter', DialogHeader: 'DialogHeader', DialogTitle: 'DialogTitle',
  },
  '@/components/ui/input': { Input: 'Input' },
  '@/components/ui/label': { Label: 'Label' },
  '@/lib/api-contract': contract,
  '@/lib/admin-creditos-operacionais-api': { AdminCreditosApi: api },
})

function input(id) { return find(runner.tree, (node) => node.type === 'Input' && node.props.id === id) }
function confirmButton() {
  return find(runner.tree, (node) => node.type === 'Button' && text(node.props.children).includes('Confirmar'))
}
function error() { return find(runner.tree, (node) => node.type === 'ContractState')?.props.error }
async function change(id, value) {
  input(id).props.onChange({ target: { value } })
  await runner.settle()
}
async function click() {
  confirmButton().props.onClick()
  await runner.settle()
}

runner.mount(AdminUsuarioCreditDialog)
await runner.settle()
assert.equal(calls.saldo, 1)
assert.equal(calls.movimentos, 1)
assert.equal(input('admin-user-credit-reason').props.required, true)
assert.equal(input('admin-user-credit-reason').props.minLength, 5)
assert.equal(input('admin-user-credit-reason').props.maxLength, 500)

await click()
assert.equal(error()?.kind, 'INVALID_REQUEST')
assert.equal(error()?.status, null)
assert.match(error()?.message, /quantidade positiva/)
assert.equal(calls.confirmar, 0)
assert.equal(calls.ajustar.length, 0)

await change('admin-user-credit-quantity', '0')
await click()
assert.equal(error()?.kind, 'INVALID_REQUEST')
assert.equal(calls.confirmar, 0)
assert.equal(calls.ajustar.length, 0)

await change('admin-user-credit-quantity', '500')
for (const invalid of ['', '    ', 'abcd', '  abcd  ', 'x'.repeat(501)]) {
  await change('admin-user-credit-reason', invalid)
  await click()
  assert.equal(error()?.kind, 'INVALID_REQUEST', `Motivo inválido: ${invalid.length} caracteres`)
  assert.equal(error()?.status, null)
  assert.match(error()?.message, /5 a 500 caracteres/)
  assert.equal(calls.confirmar, 0)
  assert.equal(calls.ajustar.length, 0)
}

await change('admin-user-credit-reason', '  abcde  ')
await click()
assert.equal(calls.confirmar, 1)
assert.equal(calls.ajustar.length, 1)
assert.deepEqual(calls.ajustar[0].slice(0, 4), ['synthetic-user', 'CREDITO', 500, 'abcde'])
assert.equal(typeof calls.ajustar[0][4], 'string')
assert.ok(calls.ajustar[0][4].startsWith('ajuste-usuario-'))
assert.equal(error(), undefined)
assert.equal(input('admin-user-credit-quantity').props.value, '')
assert.equal(input('admin-user-credit-reason').props.value, '')

await change('admin-user-credit-quantity', '1')
await change('admin-user-credit-reason', 'x'.repeat(500))
await click()
assert.equal(calls.confirmar, 2)
assert.equal(calls.ajustar.length, 2)
assert.equal(calls.ajustar[1][3].length, 500)
assert.notEqual(calls.ajustar[1][4], calls.ajustar[0][4], 'Um ajuste confirmado deve receber nova chave.')
assert.equal(error(), undefined)

console.log('Créditos admin: validação local, mensagem e ausência de POST inválido validadas.')
