# Correções editoriais — preparado, não publicado

Base: `f46017dcb3f8c392b3a5eeb59f9efe12e61d2c66`. Origem: proposta editorial revisada em 17/09/2026, blocos A–G, com a correção posterior do proprietário: **o único e-mail utilizado é contato@topsdojob.com.br**. Site principal: `https://topsdojob.com`, sem alterar seu domínio.

## O que esta entrega altera

| Origem | Preparação |
|---|---|
| Código fixo: `/contato` | Orientação de login/tickets e um canal para atendimento, privacidade, questões jurídicas, denúncias e segurança. |
| Código fixo: `/meus-tickets` e menus desktop/mobile | Nome consistente “Meus tickets”, sem alterar destinos, contadores ou operações. |
| Conteúdo administrável | [Corpos finais integrais por chave](conteudo-institucional-revisado.json), para revisão e aplicação posterior pelo editor existente. **Não são importados automaticamente pela aplicação.** |

Chaves preparadas: `quem-somos`, `termos-de-uso`, `politica-privacidade`, `politica-cookies`, `consentimento-promocional`, `verificacao`, `texto-whatsapp`, `privacidade-conteudo-restrito`, `aviso-legal-conteudo-restrito`.

Termos gerais, cookies, consentimento promocional e aviso legal recebem **somente substituições de canal**, inclusive destinos `mailto:`. Nas demais chaves, foram aplicados os trechos aprovados C–G; o restante, os títulos e as datas são preservados. `footer-resumo-institucional`, `popup-login` e `termos-conteudo-restrito` não precisaram de alteração. Nenhuma migration antiga, seed ou fallback foi modificado para simular atualização do conteúdo publicado.

`texto-whatsapp` também alimenta o aviso antes do contato externo; continua texto simples. A instrução final usa “informe o endereço do anúncio, se disponível”.

## Origem e preservação

Snapshot público anterior: 17/09/2026, aproximadamente 23h00 de Brasília, `GET /api/public/conteudos-site`. SHA256 do arquivo `conteudos-publicos.json`: `18EE3AB2162C1D482939F03B0FA773D2B615C93A4CD0C8EA39CDD4033AB2E68F`.

A captura integral e a proposta original permanecem preservadas, fora do repositório, em `C:\Users\WpD\AppData\Local\Temp\tops-conteudo-institucional-20260917`. A proposta foi atualizada e P3 registra a decisão do proprietário. Não houve teste de caixa postal nem alteração de DNS, SMTP, Resend ou encaminhamento.

Cada entrada do pacote traz `expectedContentVersion`, `expectedContentHash` e `expectedUpdatedAt` da captura, além de `titulo` e `corpo` finais. Esses campos são **referências para conferência humana**, não precondições aceitas pelo endpoint. O snapshot anterior não substitui uma leitura fresca na futura aplicação.

## Procedimento futuro pelo mecanismo existente

**Não executar publicação nesta etapa.** O merge/deploy do código, isoladamente, não altera os corpos administráveis. A aplicação editorial futura exige autorização própria.

1. Coordenar uma janela sem outros editores. Usar sessão autorizada com papel `ADMIN` e permissão `ADMIN_CONFIGURAR`, no [editor existente](https://topsdojob.com/admin/termos-footer). Não editar diretamente o banco.
2. Ler `GET /api/admin/conteudos-site` pelo fluxo autenticado existente (`no-store`). Guardar privadamente o título/corpo completos e versão/hash/data **frescos** de cada chave antes de qualquer publicação. Não guardar cookies ou credenciais no pacote.
3. Comparar versão/hash/data e conteúdo com a origem revisada. Se houver diferença, não sobrescrever: preservar a edição concorrente e revisar somente aquele delta. Não trocar os campos `expected*` para fazer uma divergência desaparecer.
4. Para cada chave, conferir novamente imediatamente antes de publicar. Copiar o `titulo` preservado e o `corpo` integral do pacote nos campos correspondentes. A UI publica por `PUT /api/admin/conteudos-site/{contentKey}`, DTO existente **somente** `{ titulo, corpo }`, usando sua sessão/CSRF. Não enviar o pacote inteiro nem campos `expected*` como se fossem uma proteção implementada.
5. Publicar uma chave de cada vez. O botão salva primeiro e depois solicita revalidação da tag `public-site-content`. Reabrir o editor e comparar o conteúdo salvo. O hash da resposta é SHA-256 UTF-8 de `titulo + "\n" + corpo`, após a normalização existente (título trim; corpo CRLF→LF e trim).
6. Conferir o texto público pela API e pela página afetada, inclusive o aviso que reutiliza `texto-whatsapp`. Verificar canal único, ausência de endereços antigos e preservação dos parágrafos normativos. A renderização sintética desta entrega não comprova que o conteúdo foi publicado.
7. Em erro de rede, PUT ou revalidação, **não clicar novamente automaticamente**. O texto pode ter sido salvo antes de o cache falhar. Reler o estado administrativo e público e apurar a diferença antes de qualquer repetição.
8. Se for necessária recuperação editorial autorizada, conferir ausência de nova edição e republicar a captura anterior pela mesma tela. Não há histórico editorial restaurável automaticamente demonstrado; conservar a captura anterior até a conferência final.

### Limite de concorrência — explícito

O editor não envia versão/hash de origem nem `If-Match`. O `@Version` JPA protege transações sobrepostas, não um formulário antigo salvo depois de outra edição. A conferência GET→PUT não é atômica. **Sem exclusividade coordenada entre editores, a aplicação deve permanecer bloqueada**; esta entrega não cria CAS, importador, editor paralelo ou novo sistema de publicação.

### Limite de links — preservado

O renderizador atual aceita links HTTP(S) e internos; Markdown `mailto:` já existente é apresentado literalmente, não como link clicável. Seus endereços foram corrigidos no pacote, mas não se declara mudança de funcionalidade. O teste conserva essa restrição; não houve ampliação de protocolos nem alteração do renderizador. `/sobre` tem tratamento próprio dos links institucionais, também preservado.

## Provas e pendências mantidas

Evidências reutilizadas: `ComplianceVisitorVerificationService`, `CpfValidator`, `ComplianceVisitorDocumentService`, `AdminComplianceVisitorService`, `DenunciaPublicaService`, `AdminDenunciaService`, `KycPublicoService` e `FotoElegivelAnuncioPolicy` da base. Nenhum desses componentes foi alterado ou novamente exercitado com dados reais.

- **P1:** conferência local de CPF/data não comprova titularidade nem suficiência jurídica da verificação. A melhoria textual não resolve nem atesta conformidade do mecanismo.
- **P2:** descarte efetivo dos documentos não foi comprovado. Preservados os enunciados normativos; não se inventa descarte automático, prazo ou fundamento retroativo de conservação.
- **P3:** canal único definido pelo proprietário: `contato@topsdojob.com.br`. Sem criação de outras caixas ou teste de entrega.
- **P4 (complemento focal):** a prévia do wizard agora descreve documentação enviada/a concluir, pois `prontoParaEnviarAnuncio` inclui `PENDENTE`, `EM_ANALISE` e `APROVADO`, sem equivaler a e-mail confirmado ou aprovação documental. O texto associado mantém explícita a moderação. A orientação real de `DOCUMENT_PENDING` passa a “Envie o documento solicitado para análise.”. Apenas apresentação foi ajustada; condições, permissões e referências sintéticas legítimas de testes permanecem. Os nove corpos administráveis deste pacote não mudaram.
- **GA4 ponta a ponta:** continua adiado; não foi retomado.

Fontes primárias já conferidas na proposta, mantidas como fundamentação editorial: [LGPD](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm), [Lei 15.211/2025](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/l15211.htm), [Decreto 12.880/2026](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12880.htm), [CERT.br — Evite Fraudes](https://cartilha.cert.br/fasciculos/golpes-evite-fraudes/fasciculo-golpes-evite-fraudes.pdf), [CERT.br — Caiu? Veja o que Fazer](https://cartilha.cert.br/fasciculos/golpes-o-que-fazer/fasciculo-golpes-o-que-fazer.pdf) e [canais de emergência](https://www.gov.br/mcom/pt-br/noticias/noticias_alt/2026/setembro/emergencia-voce-sabe-para-quem-ligar-quando-precisa-de-ajuda).

## Validação proporcional

Os testes existentes `test-site-content-ssr.mjs` e `test-tickets-suporte-v3.mjs` cobrem o delta: renderização React do contato em dois contextos sintéticos, nove corpos preparados no renderizador real, endereços/links seguros, canal único e nomes/destinos de suporte. Sem transporte externo, contas ou chamados reais. São executados também no CI normal; todos os gates anteriores permanecem.

Não se declara validação visual em navegador, atendimento efetivo das caixas ou atualização produtiva. Não há merge/deploy, alteração de regras de autenticação, documento, armazenamento, exclusão, moderação ou SEO nesta preparação.
