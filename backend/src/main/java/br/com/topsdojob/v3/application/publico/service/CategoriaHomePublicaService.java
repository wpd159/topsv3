package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.CategoriaHomePublicaDto;
import br.com.topsdojob.v3.domain.anuncio.CategoriaAnuncio;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaHomePublicaService {

  private final CategoriaHomeRepository repository;
  private final ObjectStorage storage;

  public CategoriaHomePublicaService(CategoriaHomeRepository repository, ObjectStorage storage) {
    this.repository = repository;
    this.storage = storage;
  }

  @Transactional(readOnly = true)
  public List<CategoriaHomePublicaDto> listarAtivas() {
    return repository.findByAtivoTrueOrderByOrdemAscIdAsc().stream()
        .map(this::toDto)
        .toList();
  }

  private CategoriaHomePublicaDto toDto(CategoriaHomeEntity entity) {
    CategoriaAnuncio categoria = CategoriaAnuncio.porCodigo(entity.getCategoriaEnum())
        .orElseThrow(() -> new IllegalStateException("categoria da home sem vinculo canonico"));
    return new CategoriaHomePublicaDto(
        entity.getId(),
        categoria.name(),
        entity.getNome(),
        entity.getDescricao(),
        categoria.destinoPublico(),
        imagemPublica(entity),
        entity.getOrdem(),
        Boolean.TRUE.equals(entity.getAtivo()));
  }

  private String imagemPublica(CategoriaHomeEntity entity) {
    if (entity.getImagemObjectKey() != null) {
      return storage.publicUrl(StorageArea.PUBLIC_MEDIA, entity.getImagemObjectKey())
          .map(Object::toString)
          .orElseThrow(() -> new IllegalStateException("dominio publico indisponivel para imagem da categoria"));
    }
    return caminhoPublico(entity.getImagemPublicaUrl());
  }

  private String caminhoPublico(String value) {
    String caminho = value == null ? "" : value.trim();
    if (!caminho.startsWith("/") || caminho.startsWith("//") || caminho.contains("\\")) {
      throw new IllegalStateException("imagem publica invalida no catalogo de categorias");
    }
    return caminho;
  }
}
