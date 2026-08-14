package br.com.topsdojob.v3.importacao.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.channels.FileChannel;
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
import java.util.List;
import java.util.Set;

public final class RepositorioManifestoMidiaFaseCinco {

  private final ObjectMapper mapper;

  public RepositorioManifestoMidiaFaseCinco(ObjectMapper mapper) {
    this.mapper = mapper.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  public ArquivoManifestoMidiaFaseCinco carregar(Path caminho) {
    Path normalizado = arquivoRegular(caminho);
    rejeitarParcial(normalizado);
    rejeitarDentroDoRepositorio(normalizado);
    return ler(normalizado);
  }

  public ResultadoEscrita gravarAtomico(
      Path destino,
      ArquivoManifestoMidiaFaseCinco arquivo,
      ValidadorManifestoMidiaFaseCinco validador,
      String origemEsperada,
      String snapshotEsperado) {
    if (destino == null) {
      throw new IllegalArgumentException("destino do manifesto deve ser informado");
    }
    Path normalizado = destino.toAbsolutePath().normalize();
    rejeitarParcial(normalizado);
    rejeitarDentroDoRepositorio(normalizado);
    Path pai = normalizado.getParent();
    if (pai == null || !Files.isDirectory(pai, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("diretorio de saida do manifesto nao existe");
    }
    if (Files.isSymbolicLink(normalizado)) {
      throw new IllegalArgumentException("destino simbolico nao e permitido");
    }
    validador.validar(arquivo, origemEsperada, snapshotEsperado);
    if (Files.exists(normalizado, LinkOption.NOFOLLOW_LINKS)) {
      ArquivoManifestoMidiaFaseCinco existente = carregar(normalizado);
      validador.validar(existente, origemEsperada, snapshotEsperado);
      if (existente.fingerprint().equals(arquivo.fingerprint())) {
        return new ResultadoEscrita(
            EstadoEscrita.PRESERVADO,
            existente.fingerprint(),
            hashArquivo(normalizado));
      }
      throw new IllegalStateException("manifesto existente diverge e nao sera sobrescrito");
    }

    Path parcial = pai.resolve(normalizado.getFileName() + ".partial");
    if (Files.exists(parcial, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("arquivo partial preexistente bloqueia a escrita atomica");
    }
    try {
      byte[] conteudo = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(arquivo);
      Files.write(
          parcial,
          conteudo,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      restringirPermissoes(parcial);
      try (FileChannel canal = FileChannel.open(parcial, StandardOpenOption.WRITE)) {
        canal.force(true);
      }
      ArquivoManifestoMidiaFaseCinco relido = ler(parcial);
      validador.validar(relido, origemEsperada, snapshotEsperado);
      String hash = hashArquivo(parcial);
      try {
        Files.move(parcial, normalizado, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException exception) {
        throw new IllegalStateException(
            "filesystem nao oferece move atomico para o manifesto", exception);
      }
      restringirPermissoes(normalizado);
      return new ResultadoEscrita(EstadoEscrita.CRIADO, arquivo.fingerprint(), hash);
    } catch (IOException exception) {
      throw new IllegalStateException("manifesto nao pode ser gravado atomicamente", exception);
    } finally {
      try {
        Files.deleteIfExists(parcial);
      } catch (IOException ignored) {
        // O arquivo parcial nunca recebe o nome final nem e aceito pelo leitor publico.
      }
    }
  }

  private ArquivoManifestoMidiaFaseCinco ler(Path arquivo) {
    try {
      return mapper.readValue(arquivo.toFile(), ArquivoManifestoMidiaFaseCinco.class);
    } catch (IOException exception) {
      throw new IllegalStateException("manifesto invalido ou truncado", exception);
    }
  }

  private static Path arquivoRegular(Path caminho) {
    if (caminho == null) {
      throw new IllegalArgumentException("caminho do manifesto deve ser informado");
    }
    Path normalizado = caminho.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalizado, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(normalizado)) {
      throw new IllegalArgumentException("manifesto nao e um arquivo regular");
    }
    return normalizado;
  }

  private static void rejeitarParcial(Path arquivo) {
    String nome = arquivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    if (nome.endsWith(".partial") || nome.endsWith(".tmp")) {
      throw new IllegalArgumentException("arquivo parcial nao e um manifesto valido");
    }
  }

  private static void rejeitarDentroDoRepositorio(Path arquivo) {
    Path atual = Path.of("").toAbsolutePath().normalize();
    while (atual != null) {
      if (Files.exists(atual.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
        if (arquivo.startsWith(atual)) {
          throw new IllegalArgumentException("manifesto deve permanecer fora do Git");
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
      throw new IllegalStateException("hash do manifesto nao pode ser calculado", exception);
    }
  }

  private static void restringirPermissoes(Path arquivo) throws IOException {
    try {
      Files.setPosixFilePermissions(
          arquivo,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      return;
    } catch (UnsupportedOperationException ignored) {
      // Windows usa ACL; a ramificacao abaixo remove acessos herdados.
    }
    AclFileAttributeView view = Files.getFileAttributeView(
        arquivo, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IOException("filesystem nao oferece ACL para proteger o manifesto");
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
