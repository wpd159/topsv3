import { corrigirTextoCorrompido } from "@/lib/text/encoding"
import { labelAcompanhantesBairroCidade } from "@/lib/seo/local-labels"

export interface SeoFaqItem {
  pergunta: string
  resposta: string
}

interface SeoProgramaticoParams {
  tipo: "cidade" | "bairro"
  cidadeNome: string
  estadoNome: string
  estadoUf: string
  bairroNome?: string
  bairros?: string[]
  totalAnuncios?: number
  totalBairros?: number
  totalRecentes?: number
  totalDestaques?: number
}

interface SeoProgramaticoResult {
  title: string
  description: string
  h1: string
  resumoTopo: string
  intro: string[]
  bairros: string[]
  comoUsar: string[]
  perfilRegiao: string[]
  singular: string[]
  paragrafos: string[]
  faq: SeoFaqItem[]
}

const FRASES_BASE = [
  "acompanhantes em {local}",
  "acompanhante em {local}",
  "anúncios de acompanhantes em {local}",
  "perfis de acompanhantes em {local}",
  "opções de acompanhantes em {local}",
]

const FRASES_APOIO = [
  "fotos publicadas nos anúncios e navegação por localização",
  "anúncios ativos com navegação local",
  "contato direto e descoberta por bairro",
  "links internos por cidade e região",
  "anúncios com leitura simples e contexto local",
]

function hashString(value: string) {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0
  }
  return hash
}

function selecionarVariacao(seed: number, values: string[], offset = 0) {
  return values[(seed + offset) % values.length]
}

function substituirLocal(template: string, local: string) {
  return template.replace(/\{local\}/g, local)
}

function limitarDescricao(value: string, max = 158) {
  const texto = value.replace(/\s+/g, " ").trim()
  if (texto.length <= max) return texto

  const corte = texto.slice(0, max - 1)
  const ultimoEspaco = corte.lastIndexOf(" ")
  return `${(ultimoEspaco > 100 ? corte.slice(0, ultimoEspaco) : corte).trim()}.`
}

function formatarLista(values: string[]) {
  if (values.length === 0) return ""
  if (values.length === 1) return values[0]
  if (values.length === 2) return `${values[0]} e ${values[1]}`
  return `${values.slice(0, -1).join(", ")} e ${values[values.length - 1]}`
}

function capitalizarPrimeiraLetra(value: string) {
  if (!value) return value
  return `${value.charAt(0).toLocaleUpperCase("pt-BR")}${value.slice(1)}`
}

export function gerarSeoProgramaticoLocal(params: SeoProgramaticoParams): SeoProgramaticoResult {
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const estadoNome = corrigirTextoCorrompido(params.estadoNome)
  const estadoUf = corrigirTextoCorrompido(params.estadoUf)
  const bairroNome = corrigirTextoCorrompido(params.bairroNome)
  const bairros = (params.bairros || []).map((bairro) => corrigirTextoCorrompido(bairro)).filter(Boolean)
  const localPrincipal = params.tipo === "bairro" && bairroNome ? `${bairroNome}, ${cidadeNome}` : cidadeNome
  const seed = hashString([params.tipo, cidadeNome, estadoUf, bairroNome].filter(Boolean).join(":"))

  const fraseSecundaria = substituirLocal(selecionarVariacao(seed, FRASES_BASE, 2), cidadeNome)
  const fraseTerciaria = substituirLocal(selecionarVariacao(seed, FRASES_BASE, 4), localPrincipal)
  const apoioPrincipal = selecionarVariacao(seed, FRASES_APOIO, 1)
  const bairroCidadeLabel =
    params.tipo === "bairro" && bairroNome ? labelAcompanhantesBairroCidade(bairroNome, cidadeNome) : ""
  const bairroBuscaLabel =
    params.tipo === "bairro" && bairroNome
      ? bairroCidadeLabel.replace("Acompanhantes", "acompanhantes")
      : ""
  const bairroSingularLabel = bairroBuscaLabel.replace("acompanhantes", "uma acompanhante")

  const title =
    params.tipo === "bairro" && bairroNome
      ? `${bairroCidadeLabel} - fotos nos perfis e contato direto | Tops do Job`
      : `Acompanhantes em ${cidadeNome} - fotos nos perfis e contato direto | Tops do Job`

  const description =
    params.tipo === "bairro" && bairroNome
      ? `Encontre ${bairroBuscaLabel} com fotos nos perfis, contato direto e navegação por bairro.`
      : `Encontre acompanhantes em ${cidadeNome} com fotos nos perfis, contato direto e navegação por bairro.`

  const h1 =
    params.tipo === "bairro" && bairroNome
      ? bairroCidadeLabel
      : `Acompanhantes em ${cidadeNome} - ${estadoUf}`

  const resumoTopo = ["Fotos nos perfis", "Contato direto", capitalizarPrimeiraLetra(apoioPrincipal)].join(" • ")

  const totalAnunciosTexto =
    params.totalAnuncios && params.totalAnuncios > 0
      ? `A listagem reúne ${params.totalAnuncios} anúncios ativos vinculados a ${cidadeNome}.`
      : `A listagem reúne anúncios ativos vinculados a ${cidadeNome}.`
  const bairrosTexto = bairros.length > 0 ? formatarLista(bairros.slice(0, 5)) : ""

  const intro =
    params.tipo === "bairro" && bairroNome
      ? [
          `Nesta página, você encontra ${bairroBuscaLabel} com navegação local, comparação de anúncios de acompanhantes e leitura direta dos perfis publicados.`,
          `O bairro funciona como um recorte mais específico dentro de ${cidadeNome}, ajudando a avaliar localização, descrição, fotos nos perfis e caminhos de contato sem perder o contexto da cidade.`,
        ]
      : [
          `Nesta página, você encontra acompanhantes em ${cidadeNome} com navegação por bairros, leitura simples dos anúncios e links internos para regiões relacionadas.`,
          `${totalAnunciosTexto} Os cards destacam informações principais do perfil, localização, galeria dos anúncios e caminhos de contato, ajudando a comparar opções sem sair do contexto local.`,
        ]

  const bairrosEditorial =
    bairros.length > 0
      ? [
          `A navegação por bairro facilita a busca por acompanhantes em ${cidadeNome} em regiões como ${bairrosTexto}. Use esses links para continuar a pesquisa por áreas específicas, comparar a oferta local e voltar à listagem da cidade quando quiser ampliar as opções.`,
        ]
      : [
          `Mesmo quando a cobertura por bairros ainda é menor, a página continua útil para encontrar ${fraseTerciaria} e seguir por links internos para outras regiões com presença ativa.`,
        ]

  const comoUsar =
    params.tipo === "bairro" && bairroNome
      ? [
          `Ao comparar anúncios de ${bairroBuscaLabel}, observe a localização informada, a descrição do perfil, o valor publicado quando disponível, as fotos nos perfis e o botão de contato direto. Essa leitura ajuda a escolher com mais clareza sem depender de buscas genéricas.`,
        ]
      : [
          `Para encontrar uma acompanhante em ${cidadeNome}, compare região, preço informado quando disponível, descrição do perfil, fotos publicadas nos anúncios e botão de contato. A combinação desses sinais ajuda a refinar a busca sem sair da página local.`,
        ]

  const perfilRegiao =
    params.tipo === "bairro" && bairroNome
      ? [
          `Para quem procura ${fraseSecundaria}, ${bairroNome} funciona como ponto de partida para entender a oferta local e seguir para bairros próximos quando fizer sentido.`,
          `A página prioriza anúncios ativos, navegação por localização e contato direto, mantendo o foco em acompanhantes em ${cidadeNome} e em recortes reais de bairro.`,
        ]
      : [
          `${cidadeNome} reúne buscas por ${fraseSecundaria}, anúncios de acompanhantes com fotos publicadas nos perfis e caminhos internos para bairros e páginas próximas.`,
          `Além da listagem principal, a navegação por perfil e região ajuda a comparar opções de acompanhantes em ${cidadeNome} com mais contexto, sem transformar a página em um texto repetitivo ou distante da busca local.`,
        ]

  const singular =
    params.tipo === "bairro" && bairroNome
      ? [
          `Para quem procura ${bairroSingularLabel}, vale conferir os anúncios ativos, comparar a descrição de cada perfil e usar os links da cidade para continuar a busca em regiões próximas de ${cidadeNome}.`,
        ]
      : [
          `Quem busca uma acompanhante em ${cidadeNome} pode usar os bairros, perfis e links locais para comparar opções sem sair do contexto da cidade. A navegação prioriza anúncios ativos, regiões próximas e contato direto.`,
        ]

  const faq: SeoFaqItem[] =
    params.tipo === "bairro" && bairroNome
      ? [
          {
            pergunta: `Como encontrar anúncios de ${bairroBuscaLabel}?`,
            resposta:
              "Use a listagem principal para comparar perfis ativos, localização, descrição, fotos nos perfis e caminhos de contato. Depois, refine a navegação pelos links do bairro e da cidade.",
          },
          {
            pergunta: "Quais bairros próximos ajudam a continuar a busca?",
            resposta:
              bairros.length > 0
                ? `A página conecta regiões como ${bairrosTexto}, ajudando a ampliar a busca sem sair do contexto de ${cidadeNome}.`
                : `Use os links de ${cidadeNome} para continuar a navegação por regiões relacionadas e encontrar outros recortes locais com anúncios ativos.`,
          },
          {
            pergunta: "Esta página reúne anúncios ativos da região?",
            resposta:
              "Sim. A página organiza anúncios ativos vinculados à localidade exibida, com navegação por bairro, leitura dos perfis e contato direto.",
          },
          {
            pergunta: "Como entrar em contato com uma anunciante?",
            resposta:
              "Abra o anúncio desejado e use o botão de contato disponível no perfil. O contato é feito diretamente pelos caminhos exibidos no próprio anúncio.",
          },
          {
            pergunta: `Posso ver opções em toda ${cidadeNome}?`,
            resposta: `Sim. Use o link da cidade para voltar à página de acompanhantes em ${cidadeNome} e comparar opções em outros bairros.`,
          },
        ]
      : [
          {
            pergunta: `Como encontrar acompanhante em ${cidadeNome}?`,
            resposta:
              "Use a listagem principal para comparar anúncios ativos, bairros, descrições, fotos nos perfis e caminhos de contato. A estrutura foi organizada para facilitar a busca local sem etapas desnecessárias.",
          },
          {
            pergunta: `Como buscar acompanhantes por bairro em ${cidadeNome}?`,
            resposta:
              bairros.length > 0
                ? `Use os links de bairros como ${bairrosTexto} para navegar por regiões específicas e comparar anúncios dentro da cidade.`
                : "Quando ainda não há muitos bairros listados, use a página da cidade e os links internos relacionados para ampliar a busca.",
          },
          {
            pergunta: `A página mostra anúncios ativos de ${cidadeNome}?`,
            resposta:
              "Sim. A página organiza anúncios ativos vinculados à localidade exibida, com navegação por bairros, perfis e contato direto.",
          },
          {
            pergunta: "Como entrar em contato com uma anunciante?",
            resposta:
              "Abra o anúncio desejado e use o botão de contato disponível no perfil. O contato acontece pelos canais exibidos no próprio anúncio.",
          },
          {
            pergunta: "Posso ver opções em cidades próximas?",
            resposta:
              "Sim. Quando houver cidades relacionadas disponíveis, a página exibe links internos para continuar a navegação em outras localidades do mesmo estado.",
          },
        ]

  return {
    title,
    description,
    h1,
    resumoTopo,
    intro,
    bairros: bairrosEditorial,
    comoUsar,
    perfilRegiao,
    singular,
    paragrafos: [...intro, ...bairrosEditorial, ...comoUsar, ...perfilRegiao],
    faq,
  }
}

export function transformarParagrafosEmHtml(paragrafos: string[]) {
  return paragrafos.map((paragrafo) => `<p>${paragrafo}</p>`).join("\n")
}
