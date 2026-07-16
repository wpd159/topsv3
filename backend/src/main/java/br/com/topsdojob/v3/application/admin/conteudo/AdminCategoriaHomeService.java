package br.com.topsdojob.v3.application.admin.conteudo;

import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaAnuncioAdminDto;
import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaHomeAdminDto;
import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaHomeAdminRequest;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminCategoriaHomeService {

  private static final int NOME_MAX = 120;
  private static final int DESCRICAO_MAX = 280;

  private final CategoriaHomeRepository repository;
  private final MidiaUploadValidator uploadValidator;
  private final ObjectStorage storage;
  private final R2StorageProperties storageProperties;

  public AdminCategoriaHomeService(
      CategoriaHomeRepository repository,
      MidiaUploadValidator uploadValidator,
      ObjectStorage storage,
      R2StorageProperties storageProperties) {
    this.repository = repository;
    this.uploadValidator = uploadValidator;
    this.storage = storage;
    this.storageProperties = storageProperties;
  }

  @Transactional(readOnly = true)
  public List<CategoriaHomeAdminDto> listar() {
    return repository.findAllByOrderByOrdemAscIdAsc().stream()
        .map(this::toDto)
        .toList();
  }

  public List<CategoriaAnuncioAdminDto> listarCategoriasCanonicas() {
    return Arrays.stream(CategoriaAnuncio.values())
        .map(categoria -> new CategoriaAnuncioAdminDto(categoria.name(), categoria.nomePublico()))
        .toList();
  }

  @Transactional
  public CategoriaHomeAdminDto criar(CategoriaHomeAdminRequest request, MultipartFile imagem) {
    DadosValidos dados = validar(request);
    validarUnicidade(dados, null);
    MidiaValidada arquivo = validarImagem(imagem);
    UUID id = UUID.randomUUID();
    String chave = chaveImagem(id, arquivo);
    URI imagemUrl = urlPublicaObrigatoria(chave);

    storage.put(StorageArea.PUBLIC_MEDIA, chave, arquivo.bytes(), arquivo.mimeType());
    removerEmRollback(chave);
    try {
      CategoriaHomeEntity entity = CategoriaHomeEntity.criarAdministrativa(
          id,
          dados.categoria(),
          dados.nome(),
          dados.descricao(),
          chave,
          dados.ordem(),
          dados.ativo());
      CategoriaHomeEntity salva = repository.saveAndFlush(entity);
      return toDto(salva, imagemUrl.toString());
    } catch (DataIntegrityViolationException exception) {
      removerSemTransacaoSeNecessario(chave);
      throw conflito("categoria ou ordem ja configurada");
    } catch (RuntimeException exception) {
      removerSemTransacaoSeNecessario(chave);
      throw exception;
    }
  }

  @Transactional
  public CategoriaHomeAdminDto atualizar(
      UUID id,
      CategoriaHomeAdminRequest request,
      MultipartFile novaImagem) {
    CategoriaHomeEntity entity = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "categoria da home nao encontrada"));
    DadosValidos dados = validar(request);
    validarUnicidade(dados, id);

    String chaveAnterior = entity.getImagemObjectKey();
    String chaveNova = null;
    String urlNova = null;
    if (novaImagem != null && !novaImagem.isEmpty()) {
      MidiaValidada arquivo = validarImagem(novaImagem);
      chaveNova = chaveImagem(id, arquivo);
      urlNova = urlPublicaObrigatoria(chaveNova).toString();
      storage.put(StorageArea.PUBLIC_MEDIA, chaveNova, arquivo.bytes(), arquivo.mimeType());
      removerEmRollback(chaveNova);
      entity.substituirImagem(chaveNova);
    }

    entity.atualizarAdministrativa(
        dados.categoria(), dados.nome(), dados.descricao(), dados.ordem(), dados.ativo());
    try {
      CategoriaHomeEntity salva = repository.saveAndFlush(entity);
      if (chaveNova != null && chaveAnterior != null && !chaveAnterior.equals(chaveNova)) {
        removerDepoisDoCommit(chaveAnterior);
      }
      return urlNova == null ? toDto(salva) : toDto(salva, urlNova);
    } catch (DataIntegrityViolationException exception) {
      if (chaveNova != null) {
        removerSemTransacaoSeNecessario(chaveNova);
      }
      throw conflito("categoria ou ordem ja configurada");
    } catch (RuntimeException exception) {
      if (chaveNova != null) {
        removerSemTransacaoSeNecessario(chaveNova);
      }
      throw exception;
    }
  }

  private DadosValidos validar(CategoriaHomeAdminRequest request) {
    if (request == null) {
      throw badRequest("dados da categoria obrigatorios");
    }
    CategoriaAnuncio categoria = CategoriaAnuncio.porCodigo(request.categoriaCodigo())
        .orElseThrow(() -> badRequest("categoria canonica invalida"));
    String nome = texto(request.nome(), "nome", 1, NOME_MAX);
    String descricao = texto(request.descricao(), "descricao", 1, DESCRICAO_MAX);
    if (request.ordem() == null || request.ordem() < 0) {
      throw badRequest("ordem deve ser maior ou igual a zero");
    }
    if (request.ativo() == null) {
      throw badRequest("status ativo obrigatorio");
    }
    return new DadosValidos(categoria, nome, descricao, request.ordem(), request.ativo());
  }

  private void validarUnicidade(DadosValidos dados, UUID id) {
    if (!dados.ativo()) {
      validarOrdem(dados.ordem(), id);
      return;
    }
    boolean categoriaExiste = id == null
        ? repository.existsByCategoriaEnumAndAtivoTrue(dados.categoria().name())
        : repository.existsByCategoriaEnumAndAtivoTrueAndIdNot(dados.categoria().name(), id);
    if (categoriaExiste) {
      throw conflito("categoria canonica ja vinculada a outro card ativo");
    }
    validarOrdem(dados.ordem(), id);
  }

  private void validarOrdem(int ordem, UUID id) {
    boolean ordemExiste = id == null
        ? repository.existsByOrdem(ordem)
        : repository.existsByOrdemAndIdNot(ordem, id);
    if (ordemExiste) {
      throw conflito("ordem ja utilizada por outro card");
    }
  }

  private MidiaValidada validarImagem(MultipartFile imagem) {
    MidiaValidada validada = uploadValidator.validar(imagem);
    if (validada.video()) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "imagem da categoria deve ser JPG, PNG ou WebP");
    }
    return validada;
  }

  private String chaveImagem(UUID categoriaId, MidiaValidada imagem) {
    return storageProperties.getPublicMediaPrefix()
        + "categorias-home/"
        + categoriaId
        + "/"
        + imagem.sha256()
        + "."
        + imagem.extensao();
  }

  private CategoriaHomeAdminDto toDto(CategoriaHomeEntity entity) {
    return toDto(entity, resolverImagem(entity));
  }

  private CategoriaHomeAdminDto toDto(CategoriaHomeEntity entity, String imagemUrl) {
    CategoriaAnuncio categoria = CategoriaAnuncio.porCodigo(entity.getCategoriaEnum())
        .orElseThrow(() -> new IllegalStateException("categoria da home sem vinculo canonico"));
    return new CategoriaHomeAdminDto(
        entity.getId(),
        categoria.name(),
        categoria.nomePublico(),
        entity.getNome(),
        entity.getDescricao(),
        imagemUrl,
        entity.getOrdem(),
        Boolean.TRUE.equals(entity.getAtivo()));
  }

  private String resolverImagem(CategoriaHomeEntity entity) {
    if (entity.getImagemObjectKey() != null) {
      return urlPublicaObrigatoria(entity.getImagemObjectKey()).toString();
    }
    String caminho = entity.getImagemPublicaUrl();
    if (caminho == null || !caminho.startsWith("/") || caminho.startsWith("//") || caminho.contains("\\")) {
      throw new IllegalStateException("imagem publica invalida no catalogo de categorias");
    }
    return caminho;
  }

  private URI urlPublicaObrigatoria(String chave) {
    return storage.publicUrl(StorageArea.PUBLIC_MEDIA, chave)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "dominio publico do storage deve estar configurado para imagens da Home"));
  }

  private String texto(String valor, String campo, int minimo, int maximo) {
    String normalizado = valor == null ? "" : valor.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    if (normalizado.length() < minimo || normalizado.length() > maximo) {
      throw badRequest(campo + " deve ter entre " + minimo + " e " + maximo + " caracteres");
    }
    return normalizado;
  }

  private void removerEmRollback(String chave) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) {
          storage.delete(StorageArea.PUBLIC_MEDIA, chave);
        }
      }
    });
  }

  private void removerDepoisDoCommit(String chave) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      storage.delete(StorageArea.PUBLIC_MEDIA, chave);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        storage.delete(StorageArea.PUBLIC_MEDIA, chave);
      }
    });
  }

  private void removerSemTransacaoSeNecessario(String chave) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      storage.delete(StorageArea.PUBLIC_MEDIA, chave);
    }
  }

  private ResponseStatusException badRequest(String mensagem) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
  }

  private ResponseStatusException conflito(String mensagem) {
    return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
  }

  private record DadosValidos(
      CategoriaAnuncio categoria,
      String nome,
      String descricao,
      int ordem,
      boolean ativo) {
  }
}
