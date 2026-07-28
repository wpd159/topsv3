import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

const phoneSource = source('lib/phone-mask.js')
const phone = Function(`
  ${phoneSource.replaceAll('export ', '')}
  return { phoneDigitsBR, maskPhoneBR, phoneToE164BR, phoneDigitOffset, phoneCaretFromDigitOffset }
`)()
const maskedInput = source('components/forms/masked-phone-input.tsx')
const userDetail = source('features/admin-usuarios/admin-usuario-detail.tsx')
const adDocuments = source('features/admin-anuncios/admin-anuncio-documentos.tsx')
const documentGrid = source('features/admin-documentos/admin-kyc-document-grid.tsx')
const documentApi = source('features/admin-documentos/api.ts')
const userEdit = source('features/admin-usuarios/admin-usuario-edit-form.tsx')
const adEdit = source('features/admin-anuncios/admin-anuncio-edit-form.tsx')
const adDetail = source('features/admin-anuncios/admin-anuncio-moderacao.tsx')

assert.equal(phone.maskPhoneBR('6'), '(6')
assert.equal(phone.maskPhoneBR('62'), '(62')
assert.equal(phone.maskPhoneBR('623333'), '(62) 3333')
assert.equal(phone.maskPhoneBR('6233334444'), '(62) 3333-4444')
assert.equal(phone.maskPhoneBR('62999998888'), '(62) 99999-8888')
assert.equal(phone.maskPhoneBR('(62) 99999-8888'), '(62) 99999-8888')
assert.equal(phone.maskPhoneBR('+55 62 3333-4444'), '(62) 3333-4444')
assert.equal(phone.phoneToE164BR('(62) 3333-4444'), '+556233334444')
assert.equal(phone.phoneToE164BR('62 99999 8888'), '+5562999998888')
assert.equal(phone.phoneToE164BR('(62) 9999'), null)
assert.equal(phone.phoneDigitOffset('(62) 99999-8888', 9), 6)
assert.equal(phone.phoneCaretFromDigitOffset('(62) 99999-8888', 6), 9)

assert.ok(maskedInput.includes("event.key !== 'Backspace'"), 'Backspace deve tratar separadores sem deslocar o cursor.')
assert.ok(maskedInput.includes('setSelectionRange') && maskedInput.includes('requestAnimationFrame'), 'A posicao do cursor deve ser restaurada apos aplicar a mascara.')
assert.ok(maskedInput.includes('maskPhoneBR(event.target.value)'), 'Digitacao e colagem devem passar pela mesma mascara.')

assert.ok(userEdit.includes('MaskedPhoneInput') && userEdit.includes('phoneToE164BR'), 'Telefone do usuario deve manter mascara e persistir normalizado.')
assert.ok(userEdit.includes('maskCpf') && userEdit.includes('cpfDigits'), 'CPF do usuario deve manter mascara e persistir normalizado.')
assert.ok(userEdit.includes('BirthDateField') && userEdit.includes('birthDateToIso'), 'Nascimento deve manter mascara e persistir normalizado.')
assert.ok(adEdit.includes('MaskedPhoneInput') && adEdit.includes('phoneToE164BR'), 'WhatsApp do anuncio deve manter mascara e persistir normalizado.')
assert.ok(adEdit.includes('hadWhatsapp && !normalizedWhatsapp'), 'Campo existente vazio ou parcial nao pode virar null.')
assert.ok(userDetail.includes('maskPhoneBR(detail.telefone)'), 'Telefone salvo deve continuar mascarado no detalhe do usuario.')
assert.ok(adDetail.includes('maskPhoneBR(ad.whatsapp)'), 'WhatsApp salvo deve continuar mascarado no detalhe do anuncio.')

assert.ok(userDetail.includes('AdminKycDocumentGrid'), 'Usuarios deve usar a grade compartilhada.')
assert.ok(userDetail.includes('AdminKycUploadDialog'), 'A gestao do usuario deve concentrar inclusao e substituicao.')
assert.ok(adDocuments.includes('AdminKycDocumentGrid'), 'Anuncios deve usar a mesma grade compartilhada.')
assert.ok(!adDocuments.includes('AdminKycUploadDialog'), 'Anuncios deve permanecer somente leitura para documentos.')
assert.ok(adDocuments.includes('listAdminAdDocuments(anuncioId)'), 'Anuncios deve obter documentos pelo vinculo canonico com o proprietario.')
assert.ok(documentGrid.includes('adminDocumentThumbnailUrl(document.id)'), 'JPG, PNG e PDF devem usar o endpoint de miniatura protegida.')
assert.ok(documentGrid.includes('getAdminDocumentTemporaryUrl(documentId)'), 'Arquivo integral deve ser solicitado somente ao abrir a visualizacao.')
assert.ok(documentGrid.includes('object-contain'), 'Miniaturas nao podem ser cortadas.')
assert.ok(documentGrid.includes('sm:grid-cols-2') && documentGrid.includes('xl:grid-cols-3'), 'A grade deve ser responsiva.')
assert.ok(documentApi.includes('/miniatura') && documentApi.includes('/url-temporaria'), 'Miniatura e visualizacao devem compartilhar o contrato administrativo privado.')
for (const forbidden of ['objectKey', 'bucket', 'chaveObjeto', 'signedUrl']) {
  assert.ok(!documentGrid.includes(forbidden), `A grade nao pode expor ${forbidden}.`)
}

console.log('KYC compartilhado e mascara administrativa: contratos, cursor, persistencia e privacidade validados.')
