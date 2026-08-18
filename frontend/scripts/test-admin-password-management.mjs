import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repositoryRoot = path.resolve(frontendRoot, '..')
const frontend = (file) => readFileSync(path.join(frontendRoot, file), 'utf8')
const repository = (file) => readFileSync(path.join(repositoryRoot, file), 'utf8')

const login = frontend('src/app/(admin-auth)/admin/login/page.tsx')
const footer = frontend('src/app/(painel-admin)/admin/components/sidebar/sidebar-user-footer.tsx')
const dialog = frontend('src/features/admin-auth/admin-change-password-dialog.tsx')
const adapter = frontend('src/lib/admin-auth-api.ts')
const recovery = frontend('src/components/modals/recuperar-senha-modal.tsx')
const openapi = repository('contracts/openapi/topsdojob-v3-local.yaml')

assert.match(login, /Esqueci minha senha/)
assert.match(login, /setForgotPasswordOpen\(true\)/)
assert.match(login, /<RecuperarSenhaModal/)
assert.doesNotMatch(login, /requestPublicPasswordReset|resetPublicCredential/)

assert.match(footer, /Alterar senha/)
assert.match(footer, /<AdminChangePasswordDialog/)
assert.match(footer, /onOpenChange=\{setPasswordDialogOpen\}/)

assert.equal((dialog.match(/<PasswordInput/g) || []).length, 3)
assert.match(dialog, /Senha atual/)
assert.match(dialog, /Nova senha/)
assert.match(dialog, /Confirmar nova senha/)
assert.match(dialog, /autoComplete="current-password"/)
assert.match(dialog, /<PasswordRequirements/)
assert.match(dialog, /passwordMeetsPolicy\(nextCredential\)/)
assert.match(dialog, /await changeAdminPassword\(currentCredential, nextCredential, confirmation\)/)
assert.match(dialog, /Senha alterada com sucesso\./)
assert.match(dialog, /window\.location\.assign\('\/admin\/login'\)/)
assert.doesNotMatch(dialog, /usuarioId/)

const payloadType = adapter.match(/type AdminCredentialField = [^\n]+/)?.[0]
assert.ok(payloadType, 'Contrato de alteração de senha ADMIN ausente.')
for (const field of ['senhaAtual', 'novaSenha', 'confirmarSenha']) {
  assert.ok(payloadType.includes(field), `Campo ${field} ausente do payload.`)
}
assert.doesNotMatch(payloadType, /usuarioId|email|papel|role/)
assert.match(adapter, /request<AdminAccountAction>\('\/password'/)
assert.match(adapter, /Object\.fromEntries/)
assert.match(adapter, /const antiForgeryValue = await ensureAntiForgeryValue\(\)/)
assert.match(adapter, /headers\.set\(antiForgeryHeaderName\(\), antiForgeryValue\)/)
assert.match(adapter, /ADMIN_PASSWORD_FUNCTIONAL_ERRORS/)
assert.match(adapter, /Senha atual incorreta\./)

assert.match(recovery, /requestPublicPasswordReset/)
assert.match(recovery, /validatePublicResetCode/)
assert.match(recovery, /resetPublicCredential/)
assert.match(recovery, /Se houver uma conta elegível, enviaremos as instruções\./)
assert.doesNotMatch(recovery, /ADMIN não encontrado|MODERADOR não encontrado/)

const adminCredentialPath = ['/api/admin/auth/', 'pass', 'word:'].join('')
assert.ok(openapi.includes(adminCredentialPath))
assert.match(openapi, /operationId: postAdminAuthPassword/)
assert.match(openapi, /#\/components\/schemas\/MinhaContaAlterarSenhaRequest/)

console.log('ADMIN_PASSWORD_MANAGEMENT_RESULT=OK')
