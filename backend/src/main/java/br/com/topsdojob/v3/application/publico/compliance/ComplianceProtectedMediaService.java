package br.com.topsdojob.v3.application.publico.compliance;

import br.com.topsdojob.v3.domain.compliance.ComplianceVisitorTypes.EscopoConteudoVisitante;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComplianceProtectedMediaService {

  private final ComplianceVisitorAccessService accessService;
  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;
  private final AnuncioRepository anuncioRepository;
  private final ObjectProvider<ObjectStorage> storageProvider;
  private final R2StorageProperties storageProperties;

  public ComplianceProtectedMediaService(
      ComplianceVisitorAccessService accessService,
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository,
      AnuncioRepository anuncioRepository,
      ObjectProvider<ObjectStorage> storageProvider,
      R2StorageProperties storageProperties) {
    this.accessService = accessService;
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
    this.anuncioRepository = anuncioRepository;
    this.storageProvider = storageProvider;
    this.storageProperties = storageProperties;
  }

  @Transactional
  public Conteudo carregar(UUID midiaId, HttpServletRequest request) {
    if (!accessService.autorizado(request, EscopoConteudoVisitante.MIDIA_RESTRITA)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "verificacao reforcada necessaria");
    }
    AnuncioMidiaEntity midia = midiaRepository.findById(midiaId)
        .filter(this::midiaProtegidaPublicavel)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "midia protegida nao encontrada"));
    AnuncioEntity anuncio = anuncioRepository.findById(midia.getAnuncioId())
        .filter(this::anuncioPublicavel)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "midia protegida nao encontrada"));
    ArquivoMidiaEntity arquivo = arquivoRepository.findById(midia.getArquivoMidiaId())
        .filter(this::arquivoPrivadoValido)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "midia protegida nao encontrada"));
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "midia protegida indisponivel");
    }
    StoredObject stored;
    try {
      stored = storage.get(StorageArea.PRIVATE_MEDIA, arquivo.getChaveObjeto());
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "midia protegida nao encontrada");
    }
    String mimeType = normalizeMime(stored.contentType(), arquivo.getMimeType());
    return new Conteudo(stored.content(), mimeType);
  }

  private boolean midiaProtegidaPublicavel(AnuncioMidiaEntity midia) {
    return midia.getStatus() == StatusAnuncioMidia.PUBLICAVEL
        && midia.getVisibilidadeMidia() == VisibilidadeMidia.RESTRITA_18;
  }

  private boolean anuncioPublicavel(AnuncioEntity anuncio) {
    return anuncio.getRemovidoEm() == null
        && anuncio.getStatus() == StatusAnuncio.PUBLICADO
        && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO;
  }

  private boolean arquivoPrivadoValido(ArquivoMidiaEntity arquivo) {
    return arquivo.getStatusArquivo() == StatusArquivoMidia.VALIDADO
        && "R2".equals(arquivo.getStorageProvider())
        && Objects.equals(storageProperties.getPrivateMediaBucket(), arquivo.getBucket())
        && arquivo.getChaveObjeto() != null
        && arquivo.getChaveObjeto().startsWith(storageProperties.getPrivateMediaPrefix());
  }

  private String normalizeMime(String storedMime, String persistedMime) {
    String value = storedMime == null || storedMime.isBlank()
        ? persistedMime
        : storedMime;
    if (value == null || value.isBlank()) {
      return "application/octet-stream";
    }
    String normalized = value.split(";", 2)[0].trim().toLowerCase();
    return normalized.startsWith("image/") || normalized.startsWith("video/")
        ? normalized
        : "application/octet-stream";
  }

  public record Conteudo(byte[] bytes, String mimeType) {

    public Conteudo {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}
