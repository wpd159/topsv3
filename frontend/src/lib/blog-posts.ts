export type BlogPost = {
  id: number
  slug: string
  categoria: string
  titulo: string
  imagem: string
  resumo: string
  autor: string
  data: string
  conteudo: string
}

export const blogPosts: BlogPost[] = [
  {
    id: 1,
    slug: "o-que-e-fazer-uma-americana",
    categoria: "Fetiches e Curiosidades",
    titulo: "O que é 'fazer uma americana'?",
    imagem:
      "https://images.unsplash.com/photo-1604948501466-30af9e6f5d3e?auto=format&fit=crop&w=900&q=60",
    resumo:
      "Se você já ouviu a expressão e ficou curioso pra saber o que significa, saiba que não está sozinho.",
    autor: "Martina",
    data: "13 de outubro, 2025",
    conteudo: `
A expressão "fazer uma americana" aparece em diferentes contextos e pode variar bastante conforme a região e o ambiente em que é usada.

No universo adulto, muita gente encontra esse termo em anúncios, conversas informais ou conteúdos online, e a curiosidade acaba sendo natural. O ponto mais importante é entender que várias expressões populares surgem como gírias e nem sempre têm um significado universal.

Por isso, quando alguém se deparar com esse tipo de termo, o ideal é observar o contexto e, quando necessário, buscar informação em fontes confiáveis. Falar sobre sexualidade com clareza e sem tabu ajuda a reduzir desinformação e também evita interpretações erradas.

No fim das contas, curiosidade não é problema. Problema é ficar no escuro quando dá para aprender com responsabilidade.
    `.trim(),
  },
  {
    id: 2,
    slug: "casarao-brasil-diversidade-e-inclusao",
    categoria: "Notícias",
    titulo: "Casarão Brasil: diversidade e inclusão que inspiram",
    imagem:
      "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=900&q=60",
    resumo:
      "A diversidade é o que torna nossa comunidade única. Conheça o trabalho de apoio e inclusão do Casarão Brasil.",
    autor: "Martina",
    data: "03 de outubro, 2025",
    conteudo: `
A inclusão social continua sendo um dos temas mais urgentes da atualidade. Projetos e espaços que acolhem a diversidade têm papel essencial na construção de uma sociedade mais justa.

O Casarão Brasil se destaca por incentivar respeito, acolhimento e visibilidade para diferentes vivências. Mais do que um espaço de apoio, ele representa a força de iniciativas que entendem que inclusão não pode ser só discurso bonito de campanha.

Quando estruturas assim existem e ganham visibilidade, elas inspiram outras ações, fortalecem redes de apoio e mostram que transformação social não acontece no grito — acontece no trabalho constante.
    `.trim(),
  },
  {
    id: 3,
    slug: "outubro-rosa-prevencao-que-salva-vidas",
    categoria: "Responsabilidade Social",
    titulo: "Outubro Rosa: prevenção que salva vidas",
    imagem:
      "https://images.unsplash.com/photo-1603052875297-4d9965c0c85f?auto=format&fit=crop&w=900&q=60",
    resumo:
      "Outubro é o mês de conscientização sobre o câncer de mama — saiba como apoiar essa causa.",
    autor: "Martina",
    data: "01 de outubro, 2025",
    conteudo: `
O Outubro Rosa é um movimento de conscientização que reforça a importância da prevenção e do diagnóstico precoce do câncer de mama.

Falar sobre saúde com responsabilidade é uma forma real de cuidado coletivo. Informar, incentivar exames preventivos e ampliar o acesso ao atendimento faz diferença prática na vida de muita gente.

Campanhas como essa lembram uma verdade simples: prevenção não é exagero, é estratégia. E estratégia boa salva tempo, energia e, muitas vezes, vidas.
    `.trim(),
  },
]