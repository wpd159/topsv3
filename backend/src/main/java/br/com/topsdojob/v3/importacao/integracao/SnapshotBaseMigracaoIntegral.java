package br.com.topsdojob.v3.importacao.integracao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SnapshotBaseMigracaoIntegral {

  private SnapshotBaseMigracaoIntegral() {
  }

  public record Snapshot(
      String snapshotId,
      OffsetDateTime capturadoEm,
      List<LocalidadeLegada> localidades,
      List<UsuarioLegado> usuarios,
      List<CredencialLegada> credenciais,
      List<DocumentoKycLegado> documentosKyc,
      List<OrfaoLegado> orfaos,
      List<FavoritoLegado> favoritos,
      List<MetricaAnuncioLegada> metricas) {

    public Snapshot {
      snapshotId = obrigatorio(snapshotId, "snapshotId");
      if (capturadoEm == null) {
        throw new IllegalArgumentException("capturadoEm deve ser informado");
      }
      localidades = copia(localidades);
      usuarios = copia(usuarios);
      credenciais = copia(credenciais);
      documentosKyc = copia(documentosKyc);
      orfaos = copia(orfaos);
      favoritos = copia(favoritos);
      metricas = copia(metricas);
    }
  }

  public enum TipoLocalidade {
    ESTADO,
    CIDADE,
    BAIRRO
  }

  public record LocalidadeLegada(
      String idOrigem,
      TipoLocalidade tipo,
      String paiOrigemId,
      String chaveMapeamento,
      String uf,
      String nome,
      String nomeNormalizado,
      String slug,
      OffsetDateTime criadoEm) {

    public LocalidadeLegada {
      idOrigem = obrigatorio(idOrigem, "localidade.idOrigem");
      if (tipo == null) {
        throw new IllegalArgumentException("localidade.tipo deve ser informado");
      }
      paiOrigemId = opcional(paiOrigemId);
      chaveMapeamento = opcional(chaveMapeamento);
      uf = opcional(uf);
      nome = obrigatorio(nome, "localidade.nome");
      nomeNormalizado = obrigatorio(nomeNormalizado, "localidade.nomeNormalizado")
          .toLowerCase(Locale.ROOT);
      slug = opcional(slug);
      if (criadoEm == null) {
        throw new IllegalArgumentException("localidade.criadoEm deve ser informado");
      }
    }
  }

  public record UsuarioLegado(
      String idOrigem,
      UUID idV3,
      String nomePublico,
      String emailNormalizado,
      String telefoneNormalizado,
      String status,
      String tipoConta,
      String nomeCivil,
      String cpfNormalizado,
      LocalDate dataNascimento,
      OffsetDateTime emailVerificadoEm,
      OffsetDateTime telefoneVerificadoEm,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      OffsetDateTime desativadoEm,
      Set<String> papeis) {

    public UsuarioLegado {
      idOrigem = obrigatorio(idOrigem, "usuario.idOrigem");
      idV3 = idV3 == null ? IdsMigracaoIntegral.uuid("usuario", idOrigem) : idV3;
      nomePublico = opcional(nomePublico);
      emailNormalizado = minusculo(emailNormalizado);
      telefoneNormalizado = opcional(telefoneNormalizado);
      status = obrigatorio(status, "usuario.status").toUpperCase(Locale.ROOT);
      tipoConta = obrigatorio(tipoConta, "usuario.tipoConta").toUpperCase(Locale.ROOT);
      nomeCivil = opcional(nomeCivil);
      cpfNormalizado = opcional(cpfNormalizado);
      if (criadoEm == null || atualizadoEm == null) {
        throw new IllegalArgumentException("datas do usuario devem ser informadas");
      }
      papeis = Set.copyOf(papeis == null ? Set.of() : papeis);
    }
  }

  public record CredencialLegada(
      String idOrigem,
      String usuarioOrigemId,
      String senhaHash,
      String algoritmo,
      OffsetDateTime alteradaEm,
      boolean precisaRedefinir,
      OffsetDateTime criadoEm) {

    public CredencialLegada {
      idOrigem = obrigatorio(idOrigem, "credencial.idOrigem");
      usuarioOrigemId = obrigatorio(usuarioOrigemId, "credencial.usuarioOrigemId");
      senhaHash = obrigatorio(senhaHash, "credencial.senhaHash");
      algoritmo = obrigatorio(algoritmo, "credencial.algoritmo");
      if (alteradaEm == null || criadoEm == null) {
        throw new IllegalArgumentException("datas da credencial devem ser informadas");
      }
    }
  }

  public record DocumentoKycLegado(
      String idOrigem,
      String envioOrigemId,
      String usuarioOrigemId,
      String manifestoItemId,
      String tipo,
      String parte,
      String status,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      UUID revisorV3Id,
      OffsetDateTime revisadoEm,
      String motivoSanitizado) {

    public DocumentoKycLegado {
      idOrigem = obrigatorio(idOrigem, "kyc.idOrigem");
      envioOrigemId = obrigatorio(envioOrigemId, "kyc.envioOrigemId");
      usuarioOrigemId = obrigatorio(usuarioOrigemId, "kyc.usuarioOrigemId");
      manifestoItemId = obrigatorio(manifestoItemId, "kyc.manifestoItemId");
      tipo = obrigatorio(tipo, "kyc.tipo").toUpperCase(Locale.ROOT);
      parte = obrigatorio(parte, "kyc.parte").toUpperCase(Locale.ROOT);
      status = obrigatorio(status, "kyc.status").toUpperCase(Locale.ROOT);
      if (criadoEm == null || atualizadoEm == null) {
        throw new IllegalArgumentException("datas do documento KYC devem ser informadas");
      }
      motivoSanitizado = opcional(motivoSanitizado);
    }
  }

  public enum TipoOrfao {
    CARTEIRA,
    HISTORICO_CREDITO,
    PAGAMENTO,
    SUPORTE
  }

  public record OrfaoLegado(
      TipoOrfao tipo,
      String idOrigem,
      long quantidade,
      long valorAgregado) {

    public OrfaoLegado {
      if (tipo == null) {
        throw new IllegalArgumentException("orfao.tipo deve ser informado");
      }
      idOrigem = obrigatorio(idOrigem, "orfao.idOrigem");
      if (quantidade < 1) {
        throw new IllegalArgumentException("orfao.quantidade deve ser positiva");
      }
    }
  }

  public record FavoritoLegado(
      String idOrigem,
      String usuarioOrigemId,
      String anuncioOrigemId,
      OffsetDateTime criadoEm) {

    public FavoritoLegado {
      idOrigem = obrigatorio(idOrigem, "favorito.idOrigem");
      usuarioOrigemId = obrigatorio(usuarioOrigemId, "favorito.usuarioOrigemId");
      anuncioOrigemId = obrigatorio(anuncioOrigemId, "favorito.anuncioOrigemId");
      if (criadoEm == null) {
        throw new IllegalArgumentException("favorito.criadoEm deve ser informado");
      }
    }
  }

  public record MetricaAnuncioLegada(
      String idOrigem,
      String anuncioOrigemId,
      long totalVisualizacoes,
      OffsetDateTime corteEm) {

    public MetricaAnuncioLegada {
      idOrigem = obrigatorio(idOrigem, "metrica.idOrigem");
      anuncioOrigemId = obrigatorio(anuncioOrigemId, "metrica.anuncioOrigemId");
      if (totalVisualizacoes < 0 || corteEm == null) {
        throw new IllegalArgumentException("metrica de anuncio invalida");
      }
    }
  }

  private static <T> List<T> copia(List<T> valores) {
    return List.copyOf(valores == null ? List.of() : valores);
  }

  private static String obrigatorio(String valor, String campo) {
    String normalizado = opcional(valor);
    if (normalizado == null) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return normalizado;
  }

  private static String minusculo(String valor) {
    String normalizado = opcional(valor);
    return normalizado == null ? null : normalizado.toLowerCase(Locale.ROOT);
  }

  private static String opcional(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
