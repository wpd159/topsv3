package br.com.topsdojob.v3.importacao.financeiro;

import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.AtivacaoLegada;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.CarteiraLegada;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.GrupoAtivacaoLegado;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.PagamentoLegado;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.Snapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FinanceiroFaseQuatroFixture {
  public static final UUID ATOR_SISTEMA_ID =
      UUID.fromString("f8000000-0000-4000-8000-000000000401");
  public static final UUID USUARIO_ATIVO_ID =
      UUID.fromString("f8000000-0000-4000-8000-000000000402");
  public static final UUID USUARIO_BLOQUEADO_ID =
      UUID.fromString("f8000000-0000-4000-8000-000000000403");
  public static final UUID USUARIO_ZERO_ID =
      UUID.fromString("f8000000-0000-4000-8000-000000000404");
  public static final UUID USUARIO_OUTRO_ID =
      UUID.fromString("f8000000-0000-4000-8000-000000000405");
  public static final UUID ANUNCIO_ATIVO_ID =
      UUID.fromString("f8100000-0000-4000-8000-000000000401");
  public static final UUID ANUNCIO_OUTRO_ID =
      UUID.fromString("f8100000-0000-4000-8000-000000000402");
  public static final UUID PLANO_ID =
      UUID.fromString("f8200000-0000-4000-8000-000000000401");
  public static final OffsetDateTime AGORA =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime INICIO = AGORA.minusDays(1);
  private static final OffsetDateTime FIM = AGORA.plusDays(1);

  private FinanceiroFaseQuatroFixture() {
  }

  public static Snapshot snapshotCompleto() {
    return new Snapshot(
        "snapshot-financeiro-sintetico-v1",
        AGORA,
        ATOR_SISTEMA_ID,
        Map.of(
            "user-active", USUARIO_ATIVO_ID,
            "user-blocked", USUARIO_BLOQUEADO_ID,
            "user-zero", USUARIO_ZERO_ID,
            "user-other", USUARIO_OUTRO_ID),
        Map.of(
            "ad-active", ANUNCIO_ATIVO_ID,
            "ad-other", ANUNCIO_OUTRO_ID),
        Map.of("plan-a", PLANO_ID),
        pagamentos(),
        grupos(),
        carteiras());
  }

  private static List<PagamentoLegado> pagamentos() {
    return List.of(
        pagamento(
            "pay-approved-efi", "user-active", "plan-a", "EFI", "APPROVED",
            "provider-approved-efi", "10.00", 100, true,
            null, AGORA.minusDays(3), AGORA.minusDays(2), "10.00", 100),
        pagamento(
            "pay-approved-mp", "user-active", "plan-a", "MERCADO_PAGO", "PAID",
            "provider-approved-mp", "20.00", 200, true,
            null, AGORA.minusDays(4), AGORA.minusDays(3), "20.00", 200),
        pagamento(
            "pay-pending-expired", "user-active", "plan-a", "EFI", "PENDING",
            "provider-pending", "30.00", 300, false,
            AGORA.minusHours(1), AGORA.minusDays(2), AGORA.minusHours(2), "30.00", 300),
        pagamento(
            "pay-cancelled", "user-active", "plan-a", "EFI", "CANCELLED",
            "provider-cancelled", "40.00", 400, false,
            null, AGORA.minusDays(4), AGORA.minusDays(1), "40.00", 400),
        pagamento(
            "pay-refunded", "user-active", "plan-a", "MERCADO_PAGO", "REFUNDED",
            "provider-refunded", "50.00", 500, true,
            null, AGORA.minusDays(5), AGORA.minusDays(1), "50.00", 500),
        pagamento(
            "pay-created", "user-active", "plan-a", "EFI", "CREATED",
            null, "60.00", 600, false,
            null, null, null, "60.00", 600),
        pagamento(
            "pay-value-mismatch", "user-active", "plan-a", "EFI", "APPROVED",
            "provider-value-mismatch", "70.00", 700, true,
            null, AGORA.minusDays(2), AGORA.minusDays(1), "71.00", 700),
        pagamento(
            "pay-credit-mismatch", "user-active", "plan-a", "EFI", "APPROVED",
            "provider-credit-mismatch", "80.00", 800, true,
            null, AGORA.minusDays(2), AGORA.minusDays(1), "80.00", 801),
        pagamento(
            "pay-orphan-user", "user-orphan", "plan-a", "EFI", "APPROVED",
            "provider-orphan", "90.00", 900, true,
            null, AGORA.minusDays(2), AGORA.minusDays(1), "90.00", 900),
        pagamento(
            "pay-plan-missing", "user-active", "plan-missing", "EFI", "APPROVED",
            "provider-plan-missing", "100.00", 1000, true,
            null, AGORA.minusDays(2), AGORA.minusDays(1), "100.00", 1000));
  }

  private static PagamentoLegado pagamento(
      String id,
      String usuario,
      String plano,
      String provedor,
      String estado,
      String identificador,
      String valor,
      int creditos,
      boolean creditado,
      OffsetDateTime expiracao,
      OffsetDateTime criado,
      OffsetDateTime atualizado,
      String valorEvidencia,
      int creditosEvidencia) {
    return new PagamentoLegado(
        id,
        usuario,
        plano,
        provedor,
        estado,
        identificador,
        new BigDecimal(valor),
        creditos,
        "BRL",
        creditado,
        expiracao,
        criado,
        atualizado,
        new BigDecimal(valorEvidencia),
        creditosEvidencia);
  }

  private static List<GrupoAtivacaoLegado> grupos() {
    return List.of(
        new GrupoAtivacaoLegado(
            "group-paid-multiple",
            "user-active",
            "ad-active",
            "CREDITO",
            "pay-approved-efi",
            "ATIVO",
            INICIO,
            FIM,
            List.of(
                ativacao("activation-paid-top", "ANUNCIO_TOPO", 5, "5.00", INICIO, FIM),
                ativacao("activation-paid-whatsapp", "WHATSAPP_CARD", 3, "3.00", INICIO, FIM))),
        new GrupoAtivacaoLegado(
            "group-imported",
            "user-active",
            "ad-active",
            null,
            null,
            "ACTIVE",
            INICIO,
            FIM,
            List.of(ativacao(
                "activation-imported", "OCULTAR_IDADE", 0, null, INICIO, FIM))),
        new GrupoAtivacaoLegado(
            "group-admin",
            "user-active",
            "ad-active",
            "ADMIN",
            null,
            "PUBLICADO",
            INICIO,
            FIM,
            List.of(ativacao(
                "activation-admin", "VIDEO_1", 0, null, INICIO, FIM))),
        new GrupoAtivacaoLegado(
            "group-expired",
            "user-active",
            "ad-active",
            "IMPORTACAO",
            null,
            "EXPIRADO",
            AGORA.minusDays(3),
            AGORA.minusDays(2),
            List.of(ativacao(
                "activation-expired", "CARROSSEL_FOTOS", 0, null,
                AGORA.minusDays(3), AGORA.minusDays(2)))),
        new GrupoAtivacaoLegado(
            "group-revoked",
            "user-active",
            "ad-active",
            "IMPORTACAO",
            null,
            "REVOGADO",
            INICIO,
            FIM,
            List.of(ativacao(
                "activation-revoked", "FOTOS_EXTRA_5", 0, null, INICIO, FIM))),
        new GrupoAtivacaoLegado(
            "group-inconsistent",
            "user-active",
            "ad-active",
            "IMPORTACAO",
            null,
            "ATIVO",
            INICIO,
            FIM,
            List.of(
                ativacao("activation-inconsistent-a", "ANUNCIO_TOPO", 0, null, INICIO, FIM),
                ativacao(
                    "activation-inconsistent-b", "WHATSAPP_CARD", 0, null,
                    INICIO, FIM.plusHours(1)))),
        new GrupoAtivacaoLegado(
            "group-owner-mismatch",
            "user-active",
            "ad-other",
            "IMPORTACAO",
            null,
            "ATIVO",
            INICIO,
            FIM,
            List.of(ativacao(
                "activation-owner-mismatch", "ANUNCIO_TOPO", 0, null, INICIO, FIM))));
  }

  private static AtivacaoLegada ativacao(
      String id,
      String codigo,
      int creditos,
      String preco,
      OffsetDateTime inicio,
      OffsetDateTime fim) {
    return new AtivacaoLegada(
        id,
        codigo,
        creditos,
        preco == null ? null : new BigDecimal(preco),
        inicio,
        fim);
  }

  private static List<CarteiraLegada> carteiras() {
    return List.of(
        new CarteiraLegada("wallet-active", "user-active", 1000, true, "ATIVO"),
        new CarteiraLegada("wallet-blocked", "user-blocked", 500, false, "BLOQUEADO"),
        new CarteiraLegada("wallet-zero", "user-zero", 0, false, "ATIVO"),
        new CarteiraLegada("wallet-orphan", "user-orphan", 2100, true, "ATIVO"),
        new CarteiraLegada("wallet-negative", "user-other", -5, true, "ATIVO"));
  }
}
