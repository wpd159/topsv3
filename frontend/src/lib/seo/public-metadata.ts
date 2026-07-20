import { corrigirTextoCorrompido } from "@/lib/text/encoding"

function limparEspacos(value: string) {
  return value.replace(/\s+/g, " ").replace(/\s+[–-]\s+[–-]\s+/g, " – ").trim()
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
  return `Acompanhantes em ${estadoNome} - ${params.estadoUf} | Tops do Job${sufixo}`
}

export function gerarDescricaoSeoEstado(params: {
  estadoNome: string
  totalAnuncios: number
  totalCidades: number
  page?: number
}) {
  const estadoNome = corrigirTextoCorrompido(params.estadoNome)
  const base = montarDescricaoPagina([
    `Veja ${params.totalAnuncios} anúncios ativos de acompanhantes em ${estadoNome}.`,
    params.totalCidades > 0
      ? `Navegue por ${params.totalCidades} cidades e acesse páginas locais com perfis e bairros relacionados.`
      : `Acesse os perfis publicados e continue a navegação pelas localidades disponíveis.`,
  ])

  return params.page && params.page > 0 ? limitarDescricao(`${base} Página ${params.page + 1}.`) : base
}

export function gerarTituloSeoAnuncio(params: {
  titulo: string
  cidadeNome?: string | null
  bairroNome?: string | null
  categoria?: string | null
}) {
  const titulo = limparEspacos(corrigirTextoCorrompido(params.titulo) || "Anúncio")
  const cidadeNome = limparEspacos(corrigirTextoCorrompido(params.cidadeNome) || "")
  const bairroNome = limparEspacos(corrigirTextoCorrompido(params.bairroNome) || "")
  const categoria = limparEspacos(params.categoria || "")
  const local = bairroNome && cidadeNome ? `${bairroNome}, ${cidadeNome}` : cidadeNome

  if (categoria === "VENDA_DE_CONTEUDO") {
    return limparEspacos(`Sexo virtual com ${titulo} | Tops do Job`)
  }

  const prefixo =
    categoria === "ACOMPANHANTE_MASCULINO"
      ? "Acompanhante masculino"
      : categoria === "TRANSEX_TRAVESTIS"
        ? "Acompanhante trans"
        : categoria === "MASSAGENS"
          ? "Massagista"
          : "Acompanhante"

  return local
    ? limparEspacos(`${prefixo} em ${local} – ${titulo} | Tops do Job`)
    : limparEspacos(`${prefixo} – ${titulo} | Tops do Job`)
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
