package br.com.topsdojob.v3.importacao.anuncio;

import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios.Item;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.AnuncioLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalidadeMapeada;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlanejadorAnuncioImportacao {
  private final MatrizStatusAnuncioImportacao matrizStatus =
      new MatrizStatusAnuncioImportacao();

  public Plano planejar(
      AnuncioLegado anuncio,
      UUID proprietarioV3Id,
      boolean proprietarioAtivo,
      LocalidadeMapeada localidade,
      List<Item> midiasValidas) {
    List<CodigoPendenciaImportacao> pendencias = new ArrayList<>();
    var estadoBase = matrizStatus.mapear(anuncio.status());
    if (estadoBase.isEmpty()) {
      pendencias.add(CodigoPendenciaImportacao.ANUNCIO_STATUS_DESCONHECIDO);
    }
    if (proprietarioV3Id == null) {
      pendencias.add(CodigoPendenciaImportacao.ANUNCIO_PROPRIETARIO_AUSENTE);
    }
    if (localidade == null || !localidade.correspondenciaSegura()) {
      pendencias.add(CodigoPendenciaImportacao.ANUNCIO_LOCALIDADE_AMBIGUA);
    }
    Optional<String> categoria = mapearCategoria(
        anuncio.categoria(), anuncio.atendimentoExclusivamenteVirtual());
    if (categoria.isEmpty()) {
      pendencias.add(CodigoPendenciaImportacao.ANUNCIO_CATEGORIA_AMBIGUA);
    }

    boolean quarentena = estadoBase.isEmpty()
        || proprietarioV3Id == null
        || localidade == null
        || !localidade.correspondenciaSegura()
        || categoria.isEmpty();
    if (quarentena) {
      return Plano.quarentena(pendencias);
    }

    boolean virtual = Boolean.TRUE.equals(anuncio.atendimentoExclusivamenteVirtual());
    if (anuncio.atendimentoExclusivamenteVirtual() == null) {
      pendencias.add(
          CodigoPendenciaImportacao.ANUNCIO_ATENDIMENTO_VIRTUAL_INDETERMINADO);
    }
    StatusModeracaoAnuncio moderacao = mapearModeracao(
        anuncio, estadoBase.orElseThrow().statusModeracao());
    StatusAnuncio status = estadoBase.orElseThrow().status();

    if (anuncio.removidoEm() != null || status == StatusAnuncio.REMOVIDO) {
      status = StatusAnuncio.REMOVIDO;
    } else if (bloqueado(anuncio)
        || moderacaoBloqueada(anuncio)
        || status == StatusAnuncio.BLOQUEADO) {
      status = StatusAnuncio.BLOQUEADO;
      pendencias.add(bloqueado(anuncio)
              || estadoBase.orElseThrow().status() == StatusAnuncio.BLOQUEADO
          ? CodigoPendenciaImportacao.ANUNCIO_BLOQUEIO_JURIDICO_PRESERVADO
          : CodigoPendenciaImportacao.ANUNCIO_MODERACAO_NAO_APROVADA);
    } else if (status == StatusAnuncio.REJEITADO
        || moderacao == StatusModeracaoAnuncio.REJEITADO) {
      status = StatusAnuncio.REJEITADO;
      moderacao = StatusModeracaoAnuncio.REJEITADO;
    } else if (estadoBase.orElseThrow().candidatoPublicacao()) {
      if (!proprietarioAtivo) {
        status = StatusAnuncio.PAUSADO;
        pendencias.add(CodigoPendenciaImportacao.ANUNCIO_PROPRIETARIO_INATIVO);
      } else if (anuncio.atendimentoExclusivamenteVirtual() == null) {
        status = StatusAnuncio.PENDENTE_REVISAO;
        moderacao = StatusModeracaoAnuncio.PENDENTE;
      } else if (moderacao != StatusModeracaoAnuncio.APROVADO) {
        status = StatusAnuncio.PENDENTE_REVISAO;
        moderacao = StatusModeracaoAnuncio.PENDENTE;
        pendencias.add(CodigoPendenciaImportacao.ANUNCIO_MODERACAO_NAO_APROVADA);
      } else if (midiasValidas.isEmpty()) {
        status = StatusAnuncio.PENDENTE_REVISAO;
        moderacao = StatusModeracaoAnuncio.PENDENTE;
        pendencias.add(CodigoPendenciaImportacao.ANUNCIO_SEM_MIDIA_VALIDA);
      } else if (anuncio.publicadoEm() == null) {
        status = StatusAnuncio.PENDENTE_REVISAO;
        moderacao = StatusModeracaoAnuncio.PENDENTE;
        pendencias.add(CodigoPendenciaImportacao.ANUNCIO_PUBLICACAO_SEM_DATA);
      }
    }

    Set<String> servicos = normalizarValores(anuncio.servicos());
    if (virtual) {
      servicos.add("VIDEOCHAMADA");
    }
    return new Plano(
        false,
        categoria.orElseThrow(),
        virtual,
        status,
        moderacao,
        List.copyOf(pendencias),
        Set.copyOf(servicos),
        Set.copyOf(normalizarValores(anuncio.locaisAtendimento())),
        List.copyOf(midiasValidas));
  }

  private static StatusModeracaoAnuncio mapearModeracao(
      AnuncioLegado anuncio,
      StatusModeracaoAnuncio estadoEfetivo) {
    String origem = anuncio.moderacao() == null ? null : anuncio.moderacao().status();
    if (origem == null || origem.isBlank()) {
      return estadoEfetivo;
    }
    return switch (origem.trim().toUpperCase(Locale.ROOT)) {
      case "APROVADA", "APROVADO" -> StatusModeracaoAnuncio.APROVADO;
      case "REJEITADA", "REJEITADO" -> StatusModeracaoAnuncio.REJEITADO;
      default -> StatusModeracaoAnuncio.PENDENTE;
    };
  }

  private static Optional<String> mapearCategoria(String origem, Boolean virtual) {
    String categoria = origem.trim().toUpperCase(Locale.ROOT);
    return switch (categoria) {
      case "ACOMPANHANTE_FEMININA", "ACOMPANHANTE_MASCULINO",
          "TRANSEX_TRAVESTIS", "MASSAGENS" -> Optional.of(categoria);
      case "VENDA_DE_CONTEUDO" -> Boolean.TRUE.equals(virtual)
          ? Optional.of("ACOMPANHANTE_FEMININA")
          : Optional.empty();
      default -> Optional.empty();
    };
  }

  private static boolean bloqueado(AnuncioLegado anuncio) {
    return anuncio.bloqueioJuridico() != null && anuncio.bloqueioJuridico().ativo();
  }

  private static boolean moderacaoBloqueada(AnuncioLegado anuncio) {
    String status = anuncio.moderacao() == null ? null : anuncio.moderacao().status();
    return status != null
        && Set.of("BLOQUEADA", "BLOQUEADO").contains(status.trim().toUpperCase(Locale.ROOT));
  }


  private static LinkedHashSet<String> normalizarValores(List<String> valores) {
    LinkedHashSet<String> resultado = new LinkedHashSet<>();
    for (String valor : valores) {
      if (valor != null && !valor.isBlank()) {
        resultado.add(valor.trim().toUpperCase(Locale.ROOT));
      }
    }
    return resultado;
  }

  public record Plano(
      boolean quarentena,
      String categoria,
      boolean atendimentoExclusivamenteVirtual,
      StatusAnuncio status,
      StatusModeracaoAnuncio statusModeracao,
      List<CodigoPendenciaImportacao> pendencias,
      Set<String> servicos,
      Set<String> locaisAtendimento,
      List<Item> midiasValidas) {

    private static Plano quarentena(List<CodigoPendenciaImportacao> pendencias) {
      return new Plano(
          true, null, false, null, null, List.copyOf(pendencias),
          Set.of(), Set.of(), List.of());
    }

    public boolean publicavel() {
      return status == StatusAnuncio.PUBLICADO
          && statusModeracao == StatusModeracaoAnuncio.APROVADO
          && !midiasValidas.isEmpty();
    }
  }
}
