import { corrigirTextoCorrompido } from "@/lib/text/encoding"

function limparEspacos(value: string) {
  return value.replace(/\s+/g, " ").trim()
}

function limitarDescricao(value: string, max = 160) {
  const texto = limparEspacos(value)
  if (texto.length <= max) return texto

  const corte = texto.slice(0, max - 1)
  const ultimoEspaco = corte.lastIndexOf(" ")
  return `${(ultimoEspaco > 100 ? corte.slice(0, ultimoEspaco) : corte).trim()}.`
}

function montarDescricaoPagina(textos: string[]) {
  return limitarDescricao(textos.filter(Boolean).join(" "))
}

export function gerarTituloSeoEstado(params: {
  estadoNome: string
  estadoUf: string
  page?: number
}) {
  const estadoNome = corrigirTextoCorrompido(params.estadoNome)
  const sufixo = params.page && params.page > 0 ? ` | Página ${params.page + 1}` : ""
  return `Acompanhantes em ${estadoNome} (${params.estadoUf}) - Cidades e contato direto | Tops do Job${sufixo}`
}

export function gerarDescricaoSeoEstado(params: {
  estadoNome: string
  totalAnuncios: number
  totalCidades: number
  page?: number
}) {
  const estadoNome = corrigirTextoCorrompido(params.estadoNome)
  const base = montarDescricaoPagina([
    `Explore ${params.totalAnuncios} anúncios ativos em ${estadoNome}.`,
    params.totalCidades > 0
      ? `Navegue por ${params.totalCidades} cidades com fotos nos perfis, filtros locais e contato direto em uma plataforma segura.`
      : `Veja perfis com fotos publicadas nos anúncios, navegação por localização e contato direto em uma plataforma segura e atualizada.`,
  ])

  return params.page && params.page > 0 ? limitarDescricao(`${base} Página ${params.page + 1}.`) : base
}

export function gerarTituloSeoCidade(params: {
  cidadeNome: string
  estadoUf: string
  page?: number
}) {
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const sufixo = params.page && params.page > 0 ? ` | Página ${params.page + 1}` : ""
  return `Acompanhantes em ${cidadeNome}, ${params.estadoUf} - Fotos nos perfis e contato direto | Tops do Job${sufixo}`
}

export function gerarDescricaoSeoCidade(params: {
  cidadeNome: string
  estadoNome: string
  totalAnuncios: number
  totalBairros: number
  totalRecentes: number
  page?: number
}) {
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const estadoNome = corrigirTextoCorrompido(params.estadoNome)
  const base = montarDescricaoPagina([
    `Encontre acompanhantes em ${cidadeNome}, ${estadoNome}, com ${params.totalAnuncios} anúncios ativos.`,
    params.totalBairros > 0
      ? `A página organiza a navegação por ${params.totalBairros} bairros e filtros locais.`
      : `A página reúne perfis com fotos publicadas nos anúncios e contato direto em um ambiente atualizado.`,
    params.totalRecentes > 0
      ? `${params.totalRecentes} anúncios recentes ajudam a destacar movimentação atual da cidade.`
      : `Use os cards e links internos para continuar a navegação com contexto local.`,
  ])

  return params.page && params.page > 0 ? limitarDescricao(`${base} Página ${params.page + 1}.`) : base
}

export function gerarTituloSeoBairro(params: {
  bairroNome: string
  cidadeNome: string
  estadoUf: string
  page?: number
}) {
  const bairroNome = corrigirTextoCorrompido(params.bairroNome)
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const sufixo = params.page && params.page > 0 ? ` | Página ${params.page + 1}` : ""
  return `Acompanhantes em ${bairroNome}, ${cidadeNome} - Fotos nos perfis e contato direto | Tops do Job${sufixo}`
}

export function gerarDescricaoSeoBairro(params: {
  bairroNome: string
  cidadeNome: string
  estadoNome: string
  totalAnuncios: number
  page?: number
}) {
  const bairroNome = corrigirTextoCorrompido(params.bairroNome)
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const estadoNome = corrigirTextoCorrompido(params.estadoNome)
  const base = montarDescricaoPagina([
    `Explore anúncios em ${bairroNome}, ${cidadeNome}, ${estadoNome}, com fotos publicadas nos anúncios, perfis atualizados e contato direto.`,
    `A navegação por bairro ajuda a comparar anúncios ativos e continuar a busca com contexto local.`,
  ])

  return params.page && params.page > 0 ? limitarDescricao(`${base} Página ${params.page + 1}.`) : base
}

export function gerarTituloSeoAnuncio(params: {
  titulo: string
  cidadeNome?: string | null
  bairroNome?: string | null
}) {
  const titulo = corrigirTextoCorrompido(params.titulo) || "Anúncio"
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const bairroNome = corrigirTextoCorrompido(params.bairroNome)

  if (bairroNome && cidadeNome) {
    return `${titulo} em ${bairroNome}, ${cidadeNome} | Tops do Job`
  }

  if (cidadeNome) {
    return `${titulo} em ${cidadeNome} | Tops do Job`
  }

  return `${titulo} | Tops do Job`
}

export function gerarDescricaoSeoAnuncio(params: {
  titulo: string
  descricao?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
}) {
  const titulo = corrigirTextoCorrompido(params.titulo) || "anúncio"
  const descricao = corrigirTextoCorrompido(params.descricao)
  const cidadeNome = corrigirTextoCorrompido(params.cidadeNome)
  const bairroNome = corrigirTextoCorrompido(params.bairroNome)

  if (descricao) {
    return limitarDescricao(descricao)
  }

  const local = bairroNome && cidadeNome ? `${bairroNome}, ${cidadeNome}` : cidadeNome || "sua região"

  return montarDescricaoPagina([
    `Confira o anúncio ${titulo} em ${local}, com fotos publicadas no perfil, informações atualizadas e contato direto.`,
    `Navegue com segurança e use os recursos da plataforma para revisar o perfil antes de seguir.`,
  ])
}
