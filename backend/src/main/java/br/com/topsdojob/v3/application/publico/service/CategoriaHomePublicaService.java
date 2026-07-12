package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.application.publico.dto.CategoriaHomePublicaDto;
import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaHomePublicaService {

  private final CategoriaHomeRepository repository;

  public CategoriaHomePublicaService(CategoriaHomeRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<CategoriaHomePublicaDto> listarAtivas() {
    return repository.findByAtivoTrueOrderByOrdemAscIdAsc().stream()
        .map(this::toDto)
        .toList();
  }

  private CategoriaHomePublicaDto toDto(CategoriaHomeEntity entity) {
    return new CategoriaHomePublicaDto(
        entity.getId(),
        entity.getCategoriaEnum(),
        entity.getNome(),
        entity.getDescricao(),
        caminhoPublico(entity.getDestino(), "destino"),
        caminhoPublico(entity.getImagemPublicaUrl(), "imagem publica"),
        entity.getOrdem(),
        Boolean.TRUE.equals(entity.getAtivo()));
  }

  private String caminhoPublico(String value, String campo) {
    String caminho = value == null ? "" : value.trim();
    if (!caminho.startsWith("/") || caminho.startsWith("//") || caminho.contains("\\")) {
      throw new IllegalStateException(campo + " invalida no catalogo de categorias");
    }
    return caminho;
  }
}
