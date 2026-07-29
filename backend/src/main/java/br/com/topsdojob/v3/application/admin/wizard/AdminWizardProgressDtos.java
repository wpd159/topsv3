package br.com.topsdojob.v3.application.admin.wizard;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminWizardProgressDtos {

  private AdminWizardProgressDtos() {
  }

  public record Dashboard(
      Periodo periodo,
      Indicadores indicadores,
      List<EtapaFunil> funil,
      List<EtapaAtual> etapasAtuais,
      AdminPaginaDto<Item> progresso) {
  }

  public record Periodo(
      LocalDate inicio,
      LocalDate fim,
      String fusoHorario,
      OffsetDateTime calculadoEm) {
  }

  public record Indicadores(
      long sessoesObservadas,
      long usuariosObservados,
      long kycNaoIniciado,
      long kycPendente,
      long kycAprovado,
      long emPreenchimento,
      long aguardandoModeracao,
      long anunciosRascunho,
      long anunciosRejeitados,
      long anunciosPublicados,
      Long tempoMedioConclusaoMinutos) {
  }

  public record EtapaFunil(
      String codigo,
      String rotulo,
      long quantidade,
      long perda,
      BigDecimal conversaoPercentual) {
  }

  public record EtapaAtual(
      String codigo,
      String rotulo,
      long quantidade) {
  }

  public record Item(
      UUID id,
      UUID usuarioId,
      String usuario,
      String emailMascarado,
      String modo,
      String status,
      String ultimoStep,
      String kycStatus,
      UUID anuncioId,
      String anuncioSlug,
      String anuncioTitulo,
      String anuncioStatus,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm) {
  }
}
