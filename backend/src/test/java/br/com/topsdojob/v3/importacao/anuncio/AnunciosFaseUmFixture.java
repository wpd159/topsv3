package br.com.topsdojob.v3.importacao.anuncio;

import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.AnuncioLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.BloqueioJuridicoLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.FilhoRevisaoOrfao;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalidadeMapeada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalizacaoLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.ModeracaoLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoLocalAtendimentoLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoMidiaLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.RevisaoServicoLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.Snapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AnunciosFaseUmFixture {
  static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-01-15T12:00:00-03:00");
  static final UUID ESTADO_ID = uuid("estado");
  static final UUID CIDADE_ID = uuid("cidade");
  static final UUID BAIRRO_ID = uuid("bairro");
  static final UUID PROPRIETARIO_ATIVO_ID = uuid("proprietario-ativo");
  static final UUID PROPRIETARIO_INATIVO_ID = uuid("proprietario-inativo");
  static final UUID ATOR_MAPEADO_ID = uuid("ator-mapeado");
  static final UUID ATOR_SISTEMA_ID = uuid("ator-sistema");

  private AnunciosFaseUmFixture() {
  }

  static Snapshot snapshotCompleto() {
    List<AnuncioLegado> anuncios = List.of(
        anuncio("pub", "ATIVO", true, "ACOMPANHANTE_FEMININA",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, revisoesPublicadas(), true, false),
        anuncio("sem-midia", "ATIVO", false, "ACOMPANHANTE_MASCULINO",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false),
        anuncio("pausado", "PAUSADO", false, "TRANSEX_TRAVESTIS",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false),
        anuncio("rejeitado", "REJEITADO", false, "MASSAGENS",
            "owner-active", "local-ok", moderacao("REJEITADA", "actor-unknown"),
            null, revisaoAtorDesconhecido(), false, false),
        anuncio("removido", "REMOVIDO", false, "ACOMPANHANTE_FEMININA",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), false, true),
        anuncio("bloqueado", "BLOQUEADO", false, "ACOMPANHANTE_MASCULINO",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            bloqueio(), List.of(), false, false),
        anuncio("revisao", "EM_REVISAO", false, "TRANSEX_TRAVESTIS",
            "owner-active", "local-ok", moderacao("PENDENTE", null),
            null, List.of(), false, false),
        anuncio("virtual-nulo", "ATIVO", null, "ACOMPANHANTE_FEMININA",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false),
        anuncio("owner-inativo", "ATIVO", false, "ACOMPANHANTE_FEMININA",
            "owner-inactive", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false),
        anuncio("local-ambiguo", "ATIVO", false, "ACOMPANHANTE_FEMININA",
            "owner-active", "local-ausente", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false),
        anuncio("categoria-ambigua", "ATIVO", false, "ENCONTROS_CASUAIS",
            "owner-active", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false),
        anuncio("owner-ausente", "ATIVO", false, "ACOMPANHANTE_FEMININA",
            "owner-ausente", "local-ok", moderacao("APROVADA", "actor-mapped"),
            null, List.of(), true, false));

    Map<String, UUID> proprietarios = new LinkedHashMap<>();
    proprietarios.put("owner-active", PROPRIETARIO_ATIVO_ID);
    proprietarios.put("owner-inactive", PROPRIETARIO_INATIVO_ID);
    Map<String, UUID> atores = Map.of("actor-mapped", ATOR_MAPEADO_ID);
    Map<String, LocalidadeMapeada> localidades = Map.of(
        "local-ok", new LocalidadeMapeada(ESTADO_ID, CIDADE_ID, BAIRRO_ID, true));
    return new Snapshot(
        "MIGRACAO_QA_SNAPSHOT_ANUNCIOS_001",
        AGORA,
        ATOR_SISTEMA_ID,
        proprietarios,
        atores,
        localidades,
        anuncios,
        manifesto(anuncios),
        List.of(new FilhoRevisaoOrfao(
            "anuncio_revision_services", "orfao-filho-001", "revisao-inexistente")));
  }

  static AnuncioLegado anuncioBase(String status, Boolean virtual) {
    return anuncio("unitario", status, virtual, "ACOMPANHANTE_FEMININA",
        "owner-active", "local-ok", moderacao(null, null), null,
        List.of(), true, false);
  }

  static ManifestoMidiaAnuncios.Item midiaValida(String anuncioId) {
    return new ManifestoMidiaAnuncios.Item(
        "midia-" + anuncioId,
        anuncioId,
        "ref-" + anuncioId,
        true,
        true,
        true,
        false,
        "APROVADA",
        "ANUNCIO",
        "CAPA",
        "FOTO",
        "qa-migracao",
        "fixtures/" + anuncioId + "/capa.png",
        "image/png",
        1024,
        0,
        "RESTRITA_18");
  }
  static AnuncioLegado anuncioBaseComModeracao(String status, String moderacao) {
    return anuncio("unitario-moderacao", status, false, "ACOMPANHANTE_FEMININA",
        "owner-active", "local-ok", moderacao(moderacao, "actor-mapped"), null,
        List.of(), true, false);
  }


  private static ManifestoMidiaAnuncios manifesto(List<AnuncioLegado> anuncios) {
    List<ManifestoMidiaAnuncios.Item> itens = new ArrayList<>();
    for (AnuncioLegado anuncio : anuncios) {
      if (SetIds.COM_MIDIA.contains(anuncio.idOrigem())) {
        itens.add(midiaValida(anuncio.idOrigem()));
      }
    }
    itens.add(new ManifestoMidiaAnuncios.Item(
        "midia-sem-original",
        "sem-midia",
        "ref-sem-original",
        false,
        false,
        false,
        false,
        "APROVADA",
        "ANUNCIO",
        "CAPA",
        "FOTO",
        null,
        null,
        null,
        0,
        0,
        "LIVRE"));
    itens.add(new ManifestoMidiaAnuncios.Item(
        "video-pendente",
        "pausado",
        "ref-video-pendente",
        true,
        false,
        false,
        false,
        "PENDENTE",
        "ANUNCIO",
        "GALERIA",
        "VIDEO",
        "qa-migracao",
        "fixtures/pausado/video.mp4",
        "video/mp4",
        2048,
        1,
        "RESTRITA_18"));
    return new ManifestoMidiaAnuncios(itens);
  }

  private static AnuncioLegado anuncio(
      String id,
      String status,
      Boolean virtual,
      String categoria,
      String proprietario,
      String local,
      ModeracaoLegada moderacao,
      BloqueioJuridicoLegado bloqueio,
      List<RevisaoLegada> revisoes,
      boolean publicado,
      boolean removido) {
    return new AnuncioLegado(
        id,
        proprietario,
        "migracao-qa-" + id,
        "MIGRACAO_QA_ANUNCIO_" + id.toUpperCase().replace('-', '_'),
        "CONTEUDO_SINTETICO_SEM_DADOS_PESSOAIS",
        status,
        categoria,
        virtual,
        new BigDecimal("100.00"),
        null,
        new LocalizacaoLegada(local),
        moderacao,
        "ADULT_RESTRICTED",
        bloqueio,
        List.of("ORAL"),
        List.of("A_COMBINAR"),
        AGORA.minusDays(30),
        publicado ? AGORA.minusDays(20) : null,
        removido ? AGORA.minusDays(1) : null,
        revisoes);
  }

  private static ModeracaoLegada moderacao(String status, String ator) {
    return new ModeracaoLegada(
        status, "MOTIVO_SINTETICO", "OBSERVACAO_SINTETICA", ator, AGORA.minusDays(20));
  }

  private static BloqueioJuridicoLegado bloqueio() {
    return new BloqueioJuridicoLegado(
        true,
        "ORDEM_OU_RISCO_JURIDICO",
        "Bloqueio sintetico de teste",
        "Observacao sintetica",
        "actor-unknown",
        AGORA.minusDays(2));
  }

  private static List<RevisaoLegada> revisoesPublicadas() {
    return List.of(new RevisaoLegada(
        "rev-publicada-001",
        1,
        "EDICAO",
        "APROVADA",
        "ADVERTISER",
        "MOTIVO_SINTETICO",
        "ADULT_RESTRICTED",
        "actor-mapped",
        AGORA.minusDays(25),
        AGORA.minusDays(24),
        List.of(new RevisaoMidiaLegada(
            "rev-midia-001", "ref-pub", "REORDENAR", "APROVADA", 0, null)),
        List.of(
            new RevisaoServicoLegado("rev-servico-001", "ANAL"),
            new RevisaoServicoLegado("rev-servico-002", "ORAL"),
            new RevisaoServicoLegado("rev-servico-duplicado", "ANAL")),
        List.of(new RevisaoLocalAtendimentoLegado("rev-local-001", "MEU_LOCAL"))));
  }

  private static List<RevisaoLegada> revisaoAtorDesconhecido() {
    return List.of(new RevisaoLegada(
        "rev-ator-desconhecido",
        2,
        "EDICAO",
        "REJEITADA",
        "STAFF",
        "MOTIVO_SINTETICO",
        "ADULT_RESTRICTED",
        "actor-unknown",
        AGORA.minusDays(15),
        AGORA.minusDays(14),
        List.of(),
        List.of(),
        List.of()));
  }

  private static UUID uuid(String value) {
    return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static final class SetIds {
    private static final java.util.Set<String> COM_MIDIA = java.util.Set.of(
        "pub", "virtual-nulo", "owner-inativo", "local-ambiguo",
        "categoria-ambigua", "owner-ausente");
  }
}
