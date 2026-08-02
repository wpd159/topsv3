package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class StoryAnuncioApresentacaoService {

  private static final int RESUMO_MAXIMO = 220;

  private final AnuncioLocalizacaoRepository localizacaoRepository;
  private final CidadeRepository cidadeRepository;
  private final EstadoRepository estadoRepository;

  public StoryAnuncioApresentacaoService(
      AnuncioLocalizacaoRepository localizacaoRepository,
      CidadeRepository cidadeRepository,
      EstadoRepository estadoRepository) {
    this.localizacaoRepository = localizacaoRepository;
    this.cidadeRepository = cidadeRepository;
    this.estadoRepository = estadoRepository;
  }

  public Apresentacao apresentar(AnuncioEntity anuncio) {
    AnuncioLocalizacaoEntity localizacao = localizacaoRepository
        .findByAnuncioId(anuncio.getId())
        .orElse(null);
    String cidade = localizacao == null || localizacao.getCidadeId() == null
        ? null
        : cidadeRepository.findById(localizacao.getCidadeId()).map(item -> item.getNome()).orElse(null);
    String uf = localizacao == null || localizacao.getEstadoId() == null
        ? null
        : estadoRepository.findById(localizacao.getEstadoId()).map(item -> item.getUf().trim()).orElse(null);
    return new Apresentacao(
        anuncio.getTitulo(),
        cidade,
        uf,
        anuncio.getPreco(),
        resumo(anuncio.getDescricao()));
  }

  private String resumo(String value) {
    if (value == null) {
      return null;
    }
    String normalizado = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
        .replaceAll("\\s+", " ")
        .trim();
    if (normalizado.isEmpty()) {
      return null;
    }
    return normalizado.length() <= RESUMO_MAXIMO
        ? normalizado
        : normalizado.substring(0, RESUMO_MAXIMO - 3).stripTrailing() + "...";
  }

  public record Apresentacao(
      String titulo,
      String cidade,
      String uf,
      BigDecimal preco,
      String resumo) {
  }
}
