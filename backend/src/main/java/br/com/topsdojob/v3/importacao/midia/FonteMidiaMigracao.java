package br.com.topsdojob.v3.importacao.midia;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@FunctionalInterface
public interface FonteMidiaMigracao {

  StoredObject carregar(Origem origem);

  static FonteMidiaMigracao objectStorage(ObjectStorage storage) {
    Objects.requireNonNull(storage, "storage obrigatorio");
    return source -> {
      if (source.tipo() != TipoOrigem.OBJECT_STORAGE || source.area() == null) {
        throw new OrigemMidiaInvalidaException("origem nao pertence ao object storage");
      }
      if (!storage.exists(source.area(), source.localizador())) {
        throw new ObjetoOrigemAusenteException();
      }
      return storage.get(source.area(), source.localizador());
    };
  }

  static FonteMidiaMigracao arquivosLocais(Path root) {
    Objects.requireNonNull(root, "raiz local obrigatoria");
    Path normalizedRoot = root.toAbsolutePath().normalize();
    return source -> {
      if (source.tipo() != TipoOrigem.ARQUIVO_LOCAL) {
        throw new OrigemMidiaInvalidaException("origem nao pertence ao filesystem local");
      }
      Path relative = Path.of(source.localizador());
      if (relative.isAbsolute()) {
        throw new OrigemMidiaInvalidaException("caminho local absoluto bloqueado");
      }
      Path candidate = normalizedRoot.resolve(relative).normalize();
      if (!candidate.startsWith(normalizedRoot)) {
        throw new OrigemMidiaInvalidaException("travessia de diretorio bloqueada");
      }
      validarSemLinks(normalizedRoot, relative);
      if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
        throw new ObjetoOrigemAusenteException();
      }
      try {
        String contentType = Files.probeContentType(candidate);
        return new StoredObject(Files.readAllBytes(candidate), contentType);
      } catch (IOException exception) {
        throw new FalhaTransitoriaOrigemException(exception);
      }
    };
  }

  static FonteMidiaMigracao composta(Map<TipoOrigem, FonteMidiaMigracao> sources) {
    EnumMap<TipoOrigem, FonteMidiaMigracao> copy = new EnumMap<>(TipoOrigem.class);
    copy.putAll(Objects.requireNonNull(sources, "fontes obrigatorias"));
    return source -> {
      FonteMidiaMigracao delegate = copy.get(source.tipo());
      if (delegate == null) {
        throw new OrigemMidiaInvalidaException("fonte nao configurada");
      }
      return delegate.carregar(source);
    };
  }

  private static void validarSemLinks(Path root, Path relative) {
    Path current = root;
    for (Path segment : relative) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current)) {
        throw new OrigemMidiaInvalidaException("link simbolico bloqueado");
      }
    }
  }

  final class ObjetoOrigemAusenteException extends RuntimeException {

    public ObjetoOrigemAusenteException() {
      super("objeto de origem ausente");
    }
  }

  final class OrigemMidiaInvalidaException extends RuntimeException {

    public OrigemMidiaInvalidaException(String message) {
      super(message);
    }
  }

  final class FalhaTransitoriaOrigemException extends RuntimeException {

    public FalhaTransitoriaOrigemException(Throwable cause) {
      super("falha transitoria sanitizada na origem", cause);
    }
  }
}
