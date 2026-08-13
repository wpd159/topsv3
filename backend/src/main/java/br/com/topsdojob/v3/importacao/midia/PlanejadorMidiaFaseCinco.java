package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PlanejadorMidiaFaseCinco {

  private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
  private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
  private static final long MAX_DOCUMENT_BYTES = 12L * 1024 * 1024;
  private static final Set<EntidadeTipo> ENTIDADES_DESCARTADAS = Set.of(
      EntidadeTipo.STORY,
      EntidadeTipo.SUPORTE,
      EntidadeTipo.DENUNCIA,
      EntidadeTipo.COMPLIANCE,
      EntidadeTipo.VISITANTE_COMPLIANCE,
      EntidadeTipo.WIZARD,
      EntidadeTipo.QA,
      EntidadeTipo.TEMPORARIO);

  private final GeradorChaveDestinoMidiaMigracao keyGenerator;

  public PlanejadorMidiaFaseCinco(GeradorChaveDestinoMidiaMigracao keyGenerator) {
    this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator obrigatorio");
  }

  public ManifestoMidiaFaseCinco planejar(List<Candidato> candidatos) {
    List<Item> itens = (candidatos == null ? List.<Candidato>of() : candidatos).stream()
        .map(this::planejar)
        .toList();
    return new ManifestoMidiaFaseCinco(itens);
  }

  private Item planejar(Candidato candidate) {
    Objects.requireNonNull(candidate, "candidato obrigatorio");
    String discardReason = motivoDescarte(candidate);
    if (discardReason != null) {
      return item(candidate, null, Decisao.DESCARTAR, discardReason, candidate.visibilidade());
    }

    String quarantineReason = motivoQuarentena(candidate);
    if (quarantineReason != null) {
      return item(
          candidate, null, Decisao.QUARENTENA, quarantineReason, candidate.visibilidade());
    }

    Visibilidade effectiveVisibility = visibilidadeDestino(candidate);
    Destino destination = keyGenerator.gerar(
        candidate.entidadeTipo(),
        candidate.entidadeV3Id(),
        candidate.proprietarioV3Id(),
        candidate.referenciaOrigemId(),
        MidiaMigracaoHashes.identificadorSeguro(candidate.origem().representacaoCanonica()),
        candidate.finalidade(),
        effectiveVisibility,
        candidate.sha256(),
        candidate.extensao());
    return item(candidate, destination, Decisao.IMPORTAR, null, candidate.visibilidade());
  }

  private String motivoDescarte(Candidato candidate) {
    if (ENTIDADES_DESCARTADAS.contains(candidate.entidadeTipo())) {
      return "ESCOPO_EXCLUIDO_" + candidate.entidadeTipo().name();
    }
    if (candidate.finalidade() == Finalidade.PREVIEW
        || candidate.finalidade() == Finalidade.THUMBNAIL) {
      return "DERIVADO_REGENERAVEL_DESCARTADO";
    }
    return null;
  }

  private String motivoQuarentena(Candidato candidate) {
    if (candidate.entidadeTipo() == EntidadeTipo.DESCONHECIDO) {
      return "ENTIDADE_DESCONHECIDA";
    }
    if (!candidate.referenciada()) {
      return "SEM_REFERENCIA_CANONICA";
    }
    if (!candidate.originalExiste()) {
      return "ORIGINAL_AUSENTE";
    }
    if (vazio(candidate.entidadeOrigemId()) || vazio(candidate.entidadeV3Id())) {
      return "ENTIDADE_SEM_MAPEAMENTO";
    }
    if (vazio(candidate.proprietarioOrigemId()) || vazio(candidate.proprietarioV3Id())) {
      return "OWNERSHIP_NAO_COMPROVADO";
    }
    if (vazio(candidate.referenciaOrigemId())) {
      return "REFERENCIA_ORIGEM_AUSENTE";
    }
    if (candidate.finalidade() == Finalidade.CAPA && !candidate.capaValida()) {
      return "CAPA_NAO_COMPROVADA";
    }
    if (!MidiaMigracaoHashes.sha256Valido(candidate.sha256())) {
      return "CHECKSUM_AUSENTE_OU_INVALIDO";
    }
    if (!mimePermitido(candidate)) {
      return "MIME_INCOMPATIVEL";
    }
    if (!extensaoCompativel(candidate)) {
      return "EXTENSAO_INCOMPATIVEL";
    }
    if (candidate.tamanhoBytes() < 1 || candidate.tamanhoBytes() > limite(candidate.tipoMidia())) {
      return "TAMANHO_FORA_DA_POLITICA";
    }
    if (!finalidadeCompativel(candidate)) {
      return "FINALIDADE_INCOMPATIVEL";
    }
    return null;
  }

  private boolean extensaoCompativel(Candidato candidate) {
    String extension = candidate.extensao() == null
        ? ""
        : candidate.extensao().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
    String mime = candidate.mimeType() == null
        ? ""
        : candidate.mimeType().toLowerCase(Locale.ROOT);
    return switch (mime) {
      case "image/jpeg" -> Set.of("jpg", "jpeg").contains(extension);
      case "image/png" -> "png".equals(extension);
      case "image/webp" -> "webp".equals(extension);
      case "video/mp4" -> "mp4".equals(extension);
      case "video/quicktime" -> "mov".equals(extension);
      case "application/pdf" -> "pdf".equals(extension);
      default -> false;
    };
  }

  private boolean mimePermitido(Candidato candidate) {
    String mime = candidate.mimeType() == null
        ? ""
        : candidate.mimeType().toLowerCase(Locale.ROOT);
    return switch (candidate.tipoMidia()) {
      case FOTO -> Set.of("image/jpeg", "image/png", "image/webp").contains(mime);
      case VIDEO -> Set.of("video/mp4", "video/quicktime").contains(mime);
      case DOCUMENTO -> Set.of("application/pdf", "image/jpeg", "image/png").contains(mime);
    };
  }

  private boolean finalidadeCompativel(Candidato candidate) {
    return switch (candidate.entidadeTipo()) {
      case ANUNCIO -> Set.of(Finalidade.CAPA, Finalidade.GALERIA, Finalidade.VIDEO)
          .contains(candidate.finalidade());
      case REVISAO_ANUNCIO -> candidate.finalidade() == Finalidade.REVISAO;
      case KYC -> Set.of(Finalidade.KYC_IDENTIDADE, Finalidade.KYC_IDADE)
          .contains(candidate.finalidade());
      case EDITORIAL -> Set.of(
          Finalidade.BLOG_CAPA, Finalidade.BLOG_OG, Finalidade.EDITORIAL)
          .contains(candidate.finalidade());
      default -> false;
    };
  }

  private Visibilidade visibilidadeDestino(Candidato candidate) {
    if (candidate.entidadeTipo() == EntidadeTipo.KYC
        || candidate.entidadeTipo() == EntidadeTipo.REVISAO_ANUNCIO
        || candidate.estadoModeracao() != EstadoModeracao.APROVADA) {
      return Visibilidade.PRIVADA;
    }
    return candidate.visibilidade();
  }

  private Item item(
      Candidato candidate,
      Destino destination,
      Decisao decision,
      String reason,
      Visibilidade visibility) {
    return new Item(
        candidate.idOrigem(),
        candidate.entidadeTipo(),
        candidate.entidadeOrigemId(),
        candidate.entidadeV3Id(),
        candidate.proprietarioOrigemId(),
        candidate.proprietarioV3Id(),
        candidate.referenciaOrigemId(),
        candidate.finalidade(),
        candidate.tipoMidia(),
        visibility,
        candidate.estadoModeracao(),
        candidate.originalExiste(),
        candidate.capaValida(),
        candidate.ordem(),
        candidate.mimeType(),
        candidate.tamanhoBytes(),
        candidate.sha256(),
        candidate.origem(),
        destination,
        decision,
        reason);
  }

  private static long limite(TipoMidia type) {
    return switch (type) {
      case FOTO -> MAX_IMAGE_BYTES;
      case VIDEO -> MAX_VIDEO_BYTES;
      case DOCUMENTO -> MAX_DOCUMENT_BYTES;
    };
  }

  private static boolean vazio(String value) {
    return value == null || value.isBlank();
  }

  public record Candidato(
      String idOrigem,
      EntidadeTipo entidadeTipo,
      String entidadeOrigemId,
      String entidadeV3Id,
      String proprietarioOrigemId,
      String proprietarioV3Id,
      String referenciaOrigemId,
      Finalidade finalidade,
      TipoMidia tipoMidia,
      Visibilidade visibilidade,
      EstadoModeracao estadoModeracao,
      boolean referenciada,
      boolean originalExiste,
      boolean capaValida,
      int ordem,
      String mimeType,
      long tamanhoBytes,
      String sha256,
      String extensao,
      Origem origem) {

    public Candidato {
      Objects.requireNonNull(entidadeTipo, "entidadeTipo obrigatorio");
      Objects.requireNonNull(finalidade, "finalidade obrigatoria");
      Objects.requireNonNull(tipoMidia, "tipoMidia obrigatorio");
      Objects.requireNonNull(visibilidade, "visibilidade obrigatoria");
      Objects.requireNonNull(estadoModeracao, "estadoModeracao obrigatorio");
      Objects.requireNonNull(origem, "origem obrigatoria");
    }
  }
}
