package br.com.topsdojob.v3.importacao.comercial;

import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.Decisao;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.LinhaBeneficio;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.LinhaPacote;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.PoliticaDuracao;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao.PoliticaValor;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.BeneficioLegado;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.ConfiguracaoStoryLegada;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.OpcaoBeneficioLegada;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.PacoteCreditoLegado;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.Snapshot;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class ConfiguracaoComercialFaseTresFixture {
  public static final UUID ATOR_SISTEMA_ID =
      UUID.fromString("f8000000-0000-4000-8000-000000000301");
  public static final OffsetDateTime AGORA =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  private ConfiguracaoComercialFaseTresFixture() {
  }

  public static MatrizMapeamentoComercialImportacao matriz() {
    return MatrizMapeamentoComercialImportacao.comLinhasAdicionais(
        List.of(new LinhaBeneficio(
            "DESTAQUE_CONTA",
            "DESTAQUE_CONTA_HISTORICO",
            EscopoBeneficioPremium.USUARIO,
            Decisao.PRESERVAR_HISTORICO,
            PoliticaDuracao.NAO_APLICAVEL,
            PoliticaValor.HISTORICO_SEM_EXECUCAO,
            false,
            "Destaque de conta historico",
            "Registro legado preservado sem produto executavel.",
            false,
            90)),
        List.of(
            new LinhaPacote("PACOTE_VALIDADE", "PACOTE_900", Decisao.IMPORTAR, 90),
            new LinhaPacote("PACOTE_NEGATIVO", "PACOTE_901", Decisao.IMPORTAR, 91)));
  }

  public static Snapshot snapshotCompleto() {
    return new Snapshot(
        "snapshot-comercial-sintetico-v1",
        AGORA,
        ATOR_SISTEMA_ID,
        beneficios(),
        opcoes(),
        pacotes(),
        stories());
  }

  public static Snapshot snapshotStoryIncompativel() {
    return new Snapshot(
        "snapshot-comercial-story-incompativel",
        AGORA,
        ATOR_SISTEMA_ID,
        List.of(),
        List.of(),
        List.of(),
        List.of(new ConfiguracaoStoryLegada(
            "story-incompativel",
            "Stories legado",
            "Configuracao incompativel",
            6,
            true,
            0,
            48,
            List.of("ANUNCIO"))));
  }

  private static List<BeneficioLegado> beneficios() {
    return List.of(
        new BeneficioLegado(
            "benefit-01-top",
            "ANUNCIO_TOPO",
            "Nome legado nao autoritativo",
            "<script>nao executar</script>Descricao antiga",
            "ANUNCIO",
            true,
            11),
        new BeneficioLegado(
            "benefit-02-whatsapp",
            "WHATSAPP_CARD",
            "WhatsApp antigo",
            "Registro inativo",
            "ANUNCIO",
            false,
            52),
        new BeneficioLegado(
            "benefit-03-unknown",
            "PRODUTO_NAO_CANONICO",
            "Desconhecido",
            "Nao deve ser ativado",
            "ANUNCIO",
            true,
            80),
        new BeneficioLegado(
            "benefit-99-top-duplicate",
            "ANUNCIO_TOPO",
            "Duplicado",
            "Nao deve criar segundo produto",
            "ANUNCIO",
            true,
            12),
        new BeneficioLegado(
            "benefit-04-account-history",
            "DESTAQUE_CONTA",
            "Destaque de conta",
            "Somente historico",
            "USUARIO",
            true,
            90),
        new BeneficioLegado(
            "benefit-05-video-zero",
            "VIDEO_1",
            "Video",
            "Opcao ativa com custo zero",
            "ANUNCIO",
            true,
            70),
        new BeneficioLegado(
            "benefit-06-photos-duration",
            "FOTOS_EXTRA_5",
            "Fotos",
            "Duracao incompativel",
            "ANUNCIO",
            true,
            30),
        new BeneficioLegado(
            "benefit-07-carousel-negative",
            "CARROSSEL_FOTOS",
            "Carrossel",
            "Credito negativo",
            "ANUNCIO",
            true,
            60),
        new BeneficioLegado(
            "benefit-08-hide-age",
            "OCULTAR_IDADE",
            "Ocultar",
            "Opcao valida de 14 dias",
            "ANUNCIO",
            true,
            44));
  }

  private static List<OpcaoBeneficioLegada> opcoes() {
    return List.of(
        new OpcaoBeneficioLegada(
            "option-01-top-1",
            "benefit-01-top",
            1,
            5,
            null,
            true,
            1),
        new OpcaoBeneficioLegada(
            "option-02-top-duplicate",
            "benefit-99-top-duplicate",
            1,
            5,
            null,
            true,
            1),
        new OpcaoBeneficioLegada(
            "option-03-whatsapp",
            "benefit-02-whatsapp",
            7,
            9,
            null,
            true,
            2),
        new OpcaoBeneficioLegada(
            "option-04-video-zero",
            "benefit-05-video-zero",
            7,
            0,
            null,
            true,
            2),
        new OpcaoBeneficioLegada(
            "option-05-photos-duration",
            "benefit-06-photos-duration",
            2,
            4,
            null,
            true,
            1),
        new OpcaoBeneficioLegada(
            "option-06-carousel-negative",
            "benefit-07-carousel-negative",
            7,
            -1,
            null,
            true,
            2),
        new OpcaoBeneficioLegada(
            "option-07-hide-age",
            "benefit-08-hide-age",
            14,
            18,
            new BigDecimal("12.50"),
            true,
            3),
        new OpcaoBeneficioLegada(
            "option-08-orphan",
            "benefit-missing",
            7,
            10,
            null,
            true,
            2),
        new OpcaoBeneficioLegada(
            "option-09-negative-price",
            "benefit-01-top",
            7,
            10,
            new BigDecimal("-1.00"),
            true,
            2),
        new OpcaoBeneficioLegada(
            "option-10-negative-order",
            "benefit-01-top",
            30,
            35,
            null,
            true,
            -1));
  }

  private static List<PacoteCreditoLegado> pacotes() {
    return List.of(
        new PacoteCreditoLegado(
            "package-01-prata",
            "PACOTE_PRATA",
            "Pacote Prata",
            "<b>Pacote inicial</b><script>nao executar</script>",
            50,
            5,
            new BigDecimal("9.99"),
            "BRL",
            true,
            12,
            null),
        new PacoteCreditoLegado(
            "package-02-ouro",
            "PACOTE_OURO",
            "Pacote Ouro",
            "Pacote historico inativo",
            150,
            0,
            BigDecimal.ZERO,
            "BRL",
            false,
            20,
            null),
        new PacoteCreditoLegado(
            "package-03-diamond-zero",
            "PACOTE_DIAMANTE",
            "Pacote Diamante",
            "Ativo com valor zero",
            400,
            0,
            BigDecimal.ZERO,
            "BRL",
            true,
            30,
            null),
        new PacoteCreditoLegado(
            "package-99-prata-duplicate",
            "PACOTE_PRATA",
            "Pacote Prata",
            "Duplicado",
            50,
            0,
            new BigDecimal("9.99"),
            "BRL",
            true,
            13,
            null),
        new PacoteCreditoLegado(
            "package-04-validity",
            "PACOTE_VALIDADE",
            "Pacote com validade",
            "Validade sem suporte canonico",
            900,
            0,
            new BigDecimal("49.90"),
            "BRL",
            true,
            90,
            30),
        new PacoteCreditoLegado(
            "package-05-unknown",
            "PACOTE_DESCONHECIDO",
            "Pacote desconhecido",
            "Sem mapeamento",
            10,
            0,
            new BigDecimal("1.00"),
            "BRL",
            true,
            95,
            null),
        new PacoteCreditoLegado(
            "package-06-negative-bonus",
            "PACOTE_NEGATIVO",
            "Pacote invalido",
            "Bonus negativo",
            901,
            -1,
            new BigDecimal("59.90"),
            "BRL",
            true,
            91,
            null));
  }

  private static List<ConfiguracaoStoryLegada> stories() {
    return List.of(
        new ConfiguracaoStoryLegada(
            "story-01-canonical",
            "Stories",
            "Configuracao comercial separada",
            6,
            true,
            0,
            24,
            List.of("ANUNCIO", "MIDIA_UPLOAD")),
        new ConfiguracaoStoryLegada(
            "story-99-duplicate",
            "Stories duplicado",
            "Nao deve criar outra configuracao",
            7,
            true,
            1,
            24,
            List.of("ANUNCIO", "MIDIA_UPLOAD")));
  }
}
