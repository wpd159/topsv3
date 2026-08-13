package br.com.topsdojob.v3.importacao.conteudoseo;

import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.AvisoLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.BlogCategoriaLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.BlogPostLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.ConteudoInstitucionalLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.FaqLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LinkInterno;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.RedirectLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.ReferenciaAnuncioSeo;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.Snapshot;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.TipoLocalidade;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ConteudoSeoFaseDoisFixture {
  static final OffsetDateTime AGORA =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);
  static final UUID ATOR_SISTEMA_ID = uuid("ator-sistema-conteudo-seo");
  static final UUID ATOR_MAPEADO_ID = uuid("ator-mapeado-conteudo-seo");
  static final UUID IMAGEM_PUBLICA_ID = uuid("blog-imagem-publica");
  static final UUID IMAGEM_PRIVADA_ID = uuid("blog-imagem-privada");
  static final UUID ANUNCIO_INDEXAVEL_ID = uuid("anuncio-seo-indexavel");
  static final UUID ANUNCIO_FRACO_ID = uuid("anuncio-seo-fraco");

  private static final String CORPO_PUBLICADO = """
      <h2 onclick="executar()">Orientacao sintetica</h2>
      <script>conteudoNaoExecutavel()</script>
      <p>Este material editorial sintetico explica uma navegacao segura usando apenas dados de teste.</p>
      <p>Consulte tambem <a href="/acompanhantes/qq/cidade-alfa">Cidade Alfa</a>.</p>
      """;

  private ConteudoSeoFaseDoisFixture() {
  }

  static Snapshot snapshotCompleto() {
    return new Snapshot(
        "conteudo-seo-fase2-sintetico-v1",
        AGORA,
        ATOR_SISTEMA_ID,
        Map.of("ator-editorial", ATOR_MAPEADO_ID),
        faqs(),
        avisos(),
        categorias(),
        posts(),
        institucionais(),
        localidades(),
        anunciosSeo(),
        redirects());
  }

  private static List<FaqLegada> faqs() {
    return List.of(
        new FaqLegada(
            "faq-publicada",
            "Como consultar uma localidade sintetica?",
            "Use os links publicos apresentados na navegacao da fixture e preserve a rota canonica.",
            "GERAL",
            "PUBLICADO",
            true,
            1,
            AGORA.minusDays(30),
            AGORA.minusDays(2),
            AGORA.minusDays(20)),
        new FaqLegada(
            "faq-inativa",
            "Esta pergunta inativa deve ficar publica?",
            "Nao. A fixture comprova que conteudo inativo nao integra o contrato publico.",
            "GERAL",
            "INATIVO",
            false,
            2,
            AGORA.minusDays(25),
            AGORA.minusDays(3),
            null));
  }

  private static List<AvisoLegado> avisos() {
    return List.of(new AvisoLegado(
        "aviso-inativo",
        "Aviso sintetico arquivado",
        "Este aviso preserva o historico sem aparecer no contrato publico.",
        null,
        null,
        "INATIVO",
        true,
        null,
        null,
        "ator-nao-mapeado",
        AGORA.minusDays(40),
        AGORA.minusDays(5),
        null));
  }

  private static List<BlogCategoriaLegada> categorias() {
    return List.of(new BlogCategoriaLegada(
        "categoria-guias",
        "Guias sinteticos",
        "guias-sinteticos",
        1,
        true,
        AGORA.minusDays(100),
        AGORA.minusDays(5)));
  }

  private static List<BlogPostLegado> posts() {
    return List.of(
        new BlogPostLegado(
            "post-a-publicado",
            "categoria-guias",
            "Guia sintetico de navegacao",
            "guia-migracao",
            "Orientacoes sinteticas para navegar por localidades publicas de forma segura.",
            CORPO_PUBLICADO,
            "Equipe editorial sintetica",
            "PUBLICADO",
            "Template legado fraco que nao deve ser copiado",
            "Descricao legada fraca que nao deve ser promovida literalmente.",
            IMAGEM_PUBLICA_ID,
            IMAGEM_PRIVADA_ID,
            "ator-editorial",
            AGORA.minusDays(90),
            AGORA.minusDays(2),
            AGORA.minusDays(80),
            null),
        new BlogPostLegado(
            "post-b-rascunho",
            "categoria-guias",
            "Rascunho editorial sintetico",
            "rascunho-editorial",
            "Resumo sintetico de um rascunho que deve permanecer fora do sitemap publico.",
            "<p>Conteudo de rascunho suficientemente longo para validacao, sem publicacao automatica.</p>",
            "Equipe editorial sintetica",
            "RASCUNHO",
            null,
            null,
            null,
            null,
            null,
            AGORA.minusDays(20),
            AGORA.minusDays(1),
            null,
            null),
        new BlogPostLegado(
            "post-c-colisao",
            "categoria-guias",
            "Segundo guia sintetico",
            "guia-migracao",
            "Outro resumo sintetico com finalidade propria e conteudo diferente do primeiro guia.",
            "<p>Outro material editorial sintetico, independente e util, que testa colisao deterministica de slug.</p>",
            "Equipe editorial sintetica",
            "PUBLICADO",
            null,
            null,
            null,
            null,
            "ator-editorial",
            AGORA.minusDays(50),
            AGORA.minusDays(3),
            AGORA.minusDays(45),
            null),
        new BlogPostLegado(
            "post-d-duplicado",
            "categoria-guias",
            "Copia sintetica em quarentena",
            "copia-sintetica",
            "Este item repete o corpo do primeiro post e deve ir para revisao, sem publicacao.",
            CORPO_PUBLICADO,
            "Equipe editorial sintetica",
            "PUBLICADO",
            null,
            null,
            null,
            null,
            null,
            AGORA.minusDays(30),
            AGORA.minusDays(4),
            AGORA.minusDays(25),
            null));
  }

  private static List<ConteudoInstitucionalLegado> institucionais() {
    return List.of(
        new ConteudoInstitucionalLegado(
            "institucional-sobre",
            "quem-somos",
            "Sobre a plataforma sintetica",
            """
            Conteudo institucional sintetico e util para validar a migracao.
            <script>naoExecutar()</script>
            Ele preserva a rota catalogada sem copiar texto real.
            """,
            "PUBLICADO",
            "ator-editorial",
            AGORA.minusDays(200),
            AGORA.minusDays(10),
            AGORA.minusDays(180)),
        new ConteudoInstitucionalLegado(
            "institucional-fora-catalogo",
            "pagina-inventada",
            "Pagina nao catalogada",
            "Conteudo sintetico suficiente, mas fora do catalogo institucional canonico da V3.",
            "PUBLICADO",
            null,
            AGORA.minusDays(10),
            AGORA.minusDays(5),
            AGORA.minusDays(9)));
  }

  private static List<LocalidadeSeoLegada> localidades() {
    return List.of(
        localidade(
            "estado-qq",
            TipoLocalidade.ESTADO,
            "/acompanhantes/qq",
            "Estado Sintetico",
            null,
            10,
            true,
            false,
            List.of(new LinkInterno("/acompanhantes/qq/cidade-alfa", "Cidade Alfa - QQ"))),
        localidade(
            "cidade-alfa",
            TipoLocalidade.CIDADE,
            "/acompanhantes/qq/cidade-alfa",
            "Cidade Alfa",
            "Cidade Alfa",
            6,
            true,
            false,
            List.of(new LinkInterno(
                "/acompanhantes/qq/cidade-alfa/bairro-central",
                "Bairro Central"))),
        localidade(
            "cidade-beta",
            TipoLocalidade.CIDADE,
            "/acompanhantes/qq/cidade-beta",
            "Cidade Beta",
            "Cidade Beta",
            1,
            false,
            false,
            List.of(new LinkInterno("/acompanhantes/qq", "Estado Sintetico"))),
        localidade(
            "bairro-central",
            TipoLocalidade.BAIRRO,
            "/acompanhantes/qq/cidade-alfa/bairro-central",
            "Bairro Central",
            "Cidade Alfa",
            3,
            true,
            false,
            List.of(new LinkInterno("/acompanhantes/qq/cidade-alfa", "Cidade Alfa"))),
        localidade(
            "bairro-vazio",
            TipoLocalidade.BAIRRO,
            "/acompanhantes/qq/cidade-alfa/bairro-vazio",
            "Bairro Vazio",
            "Cidade Alfa",
            0,
            false,
            true,
            List.of(new LinkInterno("/acompanhantes/qq/cidade-alfa", "Cidade Alfa"))));
  }

  private static LocalidadeSeoLegada localidade(
      String id,
      TipoLocalidade tipo,
      String caminho,
      String nome,
      String cidade,
      long anuncios,
      boolean inventarioSuficiente,
      boolean vazia,
      List<LinkInterno> links) {
    return new LocalidadeSeoLegada(
        id,
        tipo,
        caminho,
        nome,
        cidade,
        "QQ",
        true,
        true,
        true,
        inventarioSuficiente,
        false,
        false,
        vazia,
        false,
        anuncios,
        "Introducao sintetica baseada no inventario publico da localidade " + nome + ".",
        links,
        AGORA.minusDays(1));
  }

  private static List<ReferenciaAnuncioSeo> anunciosSeo() {
    return List.of(
        new ReferenciaAnuncioSeo(
            "anuncio-forte",
            ANUNCIO_INDEXAVEL_ID,
            "perfil-sintetico-completo",
            true,
            AGORA.minusHours(3)),
        new ReferenciaAnuncioSeo(
            "anuncio-fraco",
            ANUNCIO_FRACO_ID,
            "perfil-sintetico-fraco",
            false,
            AGORA.minusHours(2)));
  }

  private static List<RedirectLegado> redirects() {
    return List.of(
        new RedirectLegado(
            "redirect-antigo",
            "/blog/guia-antigo",
            "/blog/guia-intermediario",
            true,
            null,
            AGORA),
        new RedirectLegado(
            "redirect-intermediario",
            "/blog/guia-intermediario",
            "/blog/guia-migracao",
            true,
            null,
            AGORA),
        new RedirectLegado(
            "redirect-ciclo-a",
            "/blog/ciclo-a",
            "/blog/ciclo-b",
            true,
            null,
            AGORA),
        new RedirectLegado(
            "redirect-ciclo-b",
            "/blog/ciclo-b",
            "/blog/ciclo-a",
            true,
            null,
            AGORA));
  }

  private static UUID uuid(String valor) {
    return UUID.nameUUIDFromBytes(valor.getBytes(StandardCharsets.UTF_8));
  }
}
