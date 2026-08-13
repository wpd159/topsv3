package br.com.topsdojob.v3.importacao.anuncio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SnapshotAnunciosFaseUm {
  private SnapshotAnunciosFaseUm() {
  }

  public record Snapshot(
      String snapshotId,
      OffsetDateTime capturadoEm,
      UUID atorSistemaV3Id,
      Map<String, UUID> proprietariosV3,
      Map<String, UUID> atoresV3,
      Map<String, LocalidadeMapeada> localidadesV3,
      List<AnuncioLegado> anuncios,
      ManifestoMidiaAnuncios manifesto,
      List<FilhoRevisaoOrfao> filhosOrfaos) {

    public Snapshot {
      snapshotId = textoObrigatorio(snapshotId, "snapshotId");
      if (capturadoEm == null) {
        throw new IllegalArgumentException("capturadoEm deve ser informado");
      }
      proprietariosV3 = Map.copyOf(proprietariosV3 == null ? Map.of() : proprietariosV3);
      atoresV3 = Map.copyOf(atoresV3 == null ? Map.of() : atoresV3);
      localidadesV3 = Map.copyOf(localidadesV3 == null ? Map.of() : localidadesV3);
      anuncios = List.copyOf(anuncios == null ? List.of() : anuncios);
      manifesto = manifesto == null ? new ManifestoMidiaAnuncios(List.of()) : manifesto;
      filhosOrfaos = List.copyOf(filhosOrfaos == null ? List.of() : filhosOrfaos);
    }
  }

  public record AnuncioLegado(
      String idOrigem,
      String proprietarioOrigemId,
      String slug,
      String titulo,
      String descricao,
      String status,
      String categoria,
      Boolean atendimentoExclusivamenteVirtual,
      BigDecimal preco,
      String whatsappNormalizado,
      LocalizacaoLegada localizacao,
      ModeracaoLegada moderacao,
      String classificacaoConteudo,
      BloqueioJuridicoLegado bloqueioJuridico,
      List<String> servicos,
      List<String> locaisAtendimento,
      OffsetDateTime criadoEm,
      OffsetDateTime publicadoEm,
      OffsetDateTime removidoEm,
      List<RevisaoLegada> revisoes) {

    public AnuncioLegado {
      idOrigem = textoObrigatorio(idOrigem, "idOrigem");
      proprietarioOrigemId = textoObrigatorio(proprietarioOrigemId, "proprietarioOrigemId");
      slug = textoObrigatorio(slug, "slug");
      titulo = textoObrigatorio(titulo, "titulo");
      descricao = textoOpcional(descricao);
      status = textoObrigatorio(status, "status");
      categoria = textoObrigatorio(categoria, "categoria");
      whatsappNormalizado = textoOpcional(whatsappNormalizado);
      classificacaoConteudo = textoOpcional(classificacaoConteudo);
      servicos = List.copyOf(servicos == null ? List.of() : servicos);
      locaisAtendimento = List.copyOf(locaisAtendimento == null ? List.of() : locaisAtendimento);
      if (criadoEm == null) {
        throw new IllegalArgumentException("criadoEm deve ser informado");
      }
      revisoes = List.copyOf(revisoes == null ? List.of() : revisoes);
    }
  }

  public record LocalizacaoLegada(String chaveMapeamento) {
    public LocalizacaoLegada {
      chaveMapeamento = textoObrigatorio(chaveMapeamento, "chaveMapeamento");
    }
  }

  public record LocalidadeMapeada(
      UUID estadoId,
      UUID cidadeId,
      UUID bairroId,
      boolean correspondenciaSegura) {

    public LocalidadeMapeada {
      if (estadoId == null || cidadeId == null) {
        correspondenciaSegura = false;
      }
    }
  }

  public record ModeracaoLegada(
      String status,
      String motivoSanitizado,
      String observacaoSanitizada,
      String atorOrigemId,
      OffsetDateTime decididaEm) {

    public ModeracaoLegada {
      status = textoOpcional(status);
      motivoSanitizado = textoOpcional(motivoSanitizado);
      observacaoSanitizada = textoOpcional(observacaoSanitizada);
      atorOrigemId = textoOpcional(atorOrigemId);
    }
  }

  public record BloqueioJuridicoLegado(
      boolean ativo,
      String categoria,
      String motivoSanitizado,
      String observacaoSanitizada,
      String atorOrigemId,
      OffsetDateTime bloqueadoEm) {

    public BloqueioJuridicoLegado {
      categoria = textoOpcional(categoria);
      motivoSanitizado = textoOpcional(motivoSanitizado);
      observacaoSanitizada = textoOpcional(observacaoSanitizada);
      atorOrigemId = textoOpcional(atorOrigemId);
    }
  }

  public record RevisaoLegada(
      String idOrigem,
      int numero,
      String tipo,
      String status,
      String origem,
      String motivoSanitizado,
      String classificacaoConteudo,
      String atorOrigemId,
      OffsetDateTime submetidaEm,
      OffsetDateTime finalizadaEm,
      List<RevisaoMidiaLegada> midias,
      List<RevisaoServicoLegado> servicos,
      List<RevisaoLocalAtendimentoLegado> locaisAtendimento) {

    public RevisaoLegada {
      idOrigem = textoObrigatorio(idOrigem, "revisao.idOrigem");
      tipo = textoObrigatorio(tipo, "revisao.tipo");
      status = textoObrigatorio(status, "revisao.status");
      origem = textoOpcional(origem);
      motivoSanitizado = textoOpcional(motivoSanitizado);
      classificacaoConteudo = textoOpcional(classificacaoConteudo);
      atorOrigemId = textoOpcional(atorOrigemId);
      if (submetidaEm == null) {
        throw new IllegalArgumentException("revisao.submetidaEm deve ser informada");
      }
      midias = List.copyOf(midias == null ? List.of() : midias);
      servicos = List.copyOf(servicos == null ? List.of() : servicos);
      locaisAtendimento = List.copyOf(
          locaisAtendimento == null ? List.of() : locaisAtendimento);
    }
  }

  public record RevisaoMidiaLegada(
      String idOrigem,
      String referenciaManifestoId,
      String acao,
      String status,
      Integer ordem,
      String motivoSanitizado) {

    public RevisaoMidiaLegada {
      idOrigem = textoObrigatorio(idOrigem, "revisaoMidia.idOrigem");
      referenciaManifestoId = textoObrigatorio(
          referenciaManifestoId, "revisaoMidia.referenciaManifestoId");
      acao = textoObrigatorio(acao, "revisaoMidia.acao");
      status = textoObrigatorio(status, "revisaoMidia.status");
      motivoSanitizado = textoOpcional(motivoSanitizado);
    }
  }

  public record RevisaoServicoLegado(String idOrigem, String servico) {
    public RevisaoServicoLegado {
      idOrigem = textoObrigatorio(idOrigem, "revisaoServico.idOrigem");
      servico = textoObrigatorio(servico, "revisaoServico.servico");
    }
  }

  public record RevisaoLocalAtendimentoLegado(
      String idOrigem,
      String localAtendimento) {

    public RevisaoLocalAtendimentoLegado {
      idOrigem = textoObrigatorio(
          idOrigem, "revisaoLocalAtendimento.idOrigem");
      localAtendimento = textoObrigatorio(
          localAtendimento, "revisaoLocalAtendimento.localAtendimento");
    }
  }

  public record FilhoRevisaoOrfao(
      String tabelaOrigem,
      String idOrigem,
      String revisaoOrigemId) {

    public FilhoRevisaoOrfao {
      tabelaOrigem = textoObrigatorio(tabelaOrigem, "tabelaOrigem");
      idOrigem = textoObrigatorio(idOrigem, "idOrigem");
      revisaoOrigemId = textoObrigatorio(revisaoOrigemId, "revisaoOrigemId");
    }
  }

  private static String textoObrigatorio(String valor, String campo) {
    String normalizado = textoOpcional(valor);
    if (normalizado == null) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return normalizado;
  }

  private static String textoOpcional(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
