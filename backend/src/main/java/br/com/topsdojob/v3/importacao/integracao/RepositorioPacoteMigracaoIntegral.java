package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class RepositorioPacoteMigracaoIntegral {

  private final ObjectMapper mapper;

  public RepositorioPacoteMigracaoIntegral(ObjectMapper mapper) {
    this.mapper = mapper.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  public ArquivoPacoteMigracaoIntegral carregar(Path caminho) {
    Path normalizado = arquivoRegular(caminho);
    rejeitarTemporario(normalizado);
    rejeitarDentroDoRepositorio(normalizado);
    return ler(normalizado);
  }

  private ArquivoPacoteMigracaoIntegral ler(Path caminho) {
    try {
      return mapper.readValue(caminho.toFile(), ArquivoPacoteMigracaoIntegral.class);
    } catch (IOException exception) {
      throw new IllegalStateException("pacote integral invalido ou truncado", exception);
    }
  }

  public ResultadoEscrita gravarAtomico(
      Path destino,
      ArquivoPacoteMigracaoIntegral arquivo,
      ValidadorPacoteMigracaoIntegral validador,
      String origemEsperada,
      Path diretorioManifestos) {
    Path normalizado = destino.toAbsolutePath().normalize();
    rejeitarTemporario(normalizado);
    rejeitarDentroDoRepositorio(normalizado);
    Path pai = normalizado.getParent();
    if (pai == null || !Files.isDirectory(pai, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("diretorio de saida do pacote nao existe");
    }
    if (Files.isSymbolicLink(normalizado)) {
      throw new IllegalArgumentException("destino simbolico nao e permitido");
    }
    validador.validar(arquivo, origemEsperada, diretorioManifestos);
    if (Files.exists(normalizado, LinkOption.NOFOLLOW_LINKS)) {
      ArquivoPacoteMigracaoIntegral existente = carregar(normalizado);
      validador.validar(existente, origemEsperada, diretorioManifestos);
      if (existente.snapshotSha256().equals(arquivo.snapshotSha256())
          && existente.fingerprint().equals(arquivo.fingerprint())) {
        return new ResultadoEscrita(
            EstadoEscrita.PRESERVADO,
            existente.fingerprint(),
            hashArquivo(normalizado));
      }
      throw new IllegalStateException("pacote existente diverge e nao sera sobrescrito");
    }

    Path temporario = pai.resolve("." + normalizado.getFileName() + "." + UUID.randomUUID()
        + ".tmp");
    try {
      byte[] conteudo = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(arquivo);
      Files.write(
          temporario,
          conteudo,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      restringirPermissoes(temporario);
      ArquivoPacoteMigracaoIntegral relido = ler(temporario);
      String fingerprintRelido = ArquivoPacoteMigracaoIntegral.fingerprint(mapper, relido);
      if (!relido.fingerprint().equals(fingerprintRelido)) {
        throw new IllegalArgumentException(
            "fingerprint do pacote diverge apos round-trip em "
                + primeiraDiferenca(arquivo, relido));
      }
      validador.validar(relido, origemEsperada, diretorioManifestos);
      String hash = hashArquivo(temporario);
      try {
        Files.move(temporario, normalizado, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException exception) {
        throw new IllegalStateException("filesystem nao oferece move atomico para o pacote", exception);
      }
      restringirPermissoes(normalizado);
      return new ResultadoEscrita(EstadoEscrita.CRIADO, arquivo.fingerprint(), hash);
    } catch (IOException exception) {
      throw new IllegalStateException("pacote nao pode ser gravado atomicamente", exception);
    } finally {
      try {
        Files.deleteIfExists(temporario);
      } catch (IOException ignored) {
        // O arquivo parcial nunca recebe o nome final e continua inacessivel a outros usuarios.
      }
    }
  }

  private static Path arquivoRegular(Path caminho) {
    if (caminho == null) {
      throw new IllegalArgumentException("caminho do pacote deve ser informado");
    }
    Path normalizado = caminho.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalizado, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(normalizado)) {
      throw new IllegalArgumentException("pacote nao e um arquivo regular");
    }
    return normalizado;
  }

  private static void rejeitarTemporario(Path arquivo) {
    String nome = arquivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    if (nome.endsWith(".tmp") || nome.endsWith(".partial")) {
      throw new IllegalArgumentException("arquivo temporario nao e pacote final valido");
    }
  }

  private static void rejeitarDentroDoRepositorio(Path arquivo) {
    Path atual = Path.of("").toAbsolutePath().normalize();
    while (atual != null) {
      if (Files.exists(atual.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
        if (arquivo.startsWith(atual)) {
          throw new IllegalArgumentException("pacote integral deve permanecer fora do Git");
        }
        return;
      }
      atual = atual.getParent();
    }
  }

  private static String hashArquivo(Path arquivo) {
    try {
      return FingerprintMigracaoIntegral.sha256(Files.readAllBytes(arquivo));
    } catch (IOException exception) {
      throw new IllegalStateException("hash do pacote nao pode ser calculado", exception);
    }
  }

  private String primeiraDiferenca(
      ArquivoPacoteMigracaoIntegral original,
      ArquivoPacoteMigracaoIntegral relido) {
    return primeiraDiferenca(canonico(original), canonico(relido), "$");
  }

  private JsonNode canonico(ArquivoPacoteMigracaoIntegral arquivo) {
    Map<String, Object> campos = new LinkedHashMap<>();
    campos.put("schemaVersion", arquivo.schemaVersion());
    campos.put("origemId", arquivo.origemId());
    campos.put("snapshotSha256", arquivo.snapshotSha256());
    campos.put("manifestoFaseCincoArquivo", arquivo.manifestoFaseCincoArquivo());
    campos.put("manifestoFaseCincoSha256", arquivo.manifestoFaseCincoSha256());
    campos.put("manifestosSha256", arquivo.manifestosSha256());
    campos.put("contagens", arquivo.contagens());
    campos.put("pacote", arquivo.pacote());
    return mapper.valueToTree(campos);
  }

  private static String primeiraDiferenca(JsonNode original, JsonNode relido, String caminho) {
    if (original == null || relido == null || original.getNodeType() != relido.getNodeType()) {
      return caminho;
    }
    if (original.isObject()) {
      Set<String> nomes = new TreeSet<>();
      original.fieldNames().forEachRemaining(nomes::add);
      relido.fieldNames().forEachRemaining(nomes::add);
      for (String nome : nomes) {
        if (!original.has(nome) || !relido.has(nome)) {
          return caminho + "." + nome;
        }
        String diferenca = primeiraDiferenca(
            original.get(nome), relido.get(nome), caminho + "." + nome);
        if (diferenca != null) {
          return diferenca;
        }
      }
      return null;
    }
    if (original.isArray()) {
      if (original.size() != relido.size()) {
        return caminho + "[tamanho]";
      }
      for (int indice = 0; indice < original.size(); indice++) {
        String diferenca = primeiraDiferenca(
            original.get(indice), relido.get(indice), caminho + "[" + indice + "]");
        if (diferenca != null) {
          return diferenca;
        }
      }
      return null;
    }
    return original.equals(relido) ? null : caminho;
  }

  private static void restringirPermissoes(Path arquivo) throws IOException {
    try {
      Files.setPosixFilePermissions(
          arquivo,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      return;
    } catch (UnsupportedOperationException ignored) {
      // Windows usa ACL; a ramificacao abaixo remove acessos herdados ao arquivo temporario.
    }
    AclFileAttributeView view = Files.getFileAttributeView(
        arquivo, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IOException("filesystem nao oferece ACL para proteger o pacote");
    }
    var owner = Files.getOwner(arquivo, LinkOption.NOFOLLOW_LINKS);
    EnumSet<AclEntryPermission> permissoes = EnumSet.of(
        AclEntryPermission.READ_DATA,
        AclEntryPermission.WRITE_DATA,
        AclEntryPermission.APPEND_DATA,
        AclEntryPermission.READ_NAMED_ATTRS,
        AclEntryPermission.WRITE_NAMED_ATTRS,
        AclEntryPermission.EXECUTE,
        AclEntryPermission.DELETE_CHILD,
        AclEntryPermission.READ_ATTRIBUTES,
        AclEntryPermission.WRITE_ATTRIBUTES,
        AclEntryPermission.DELETE,
        AclEntryPermission.READ_ACL,
        AclEntryPermission.WRITE_ACL,
        AclEntryPermission.WRITE_OWNER,
        AclEntryPermission.SYNCHRONIZE);
    AclEntry acessoDoOwner = AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(owner)
        .setPermissions(permissoes)
        .setFlags(EnumSet.noneOf(AclEntryFlag.class))
        .build();
    view.setAcl(List.of(acessoDoOwner));
    arquivo.getFileSystem().provider().checkAccess(
        arquivo, AccessMode.READ, AccessMode.WRITE);
  }

  public enum EstadoEscrita {
    CRIADO,
    PRESERVADO
  }

  public record ResultadoEscrita(
      EstadoEscrita estado,
      String fingerprint,
      String arquivoSha256) {
  }
}
