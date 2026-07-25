package br.com.topsdojob.v3.application.conteudo;

import br.com.topsdojob.v3.application.conteudo.ConteudoSiteCatalogo.Definicao;
import br.com.topsdojob.v3.application.conteudo.dto.ConteudoSiteDto;
import br.com.topsdojob.v3.application.conteudo.dto.ConteudoSiteRequest;
import br.com.topsdojob.v3.persistence.entity.seo.SeoConteudoPaginaEntity;
import br.com.topsdojob.v3.persistence.entity.seo.SeoUrlEntity;
import br.com.topsdojob.v3.persistence.repository.SeoConteudoPaginaRepository;
import br.com.topsdojob.v3.persistence.repository.SeoUrlRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusSeoConteudo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConteudoSiteService {

  private static final int TITULO_MAX = 180;
  private static final int CORPO_MAX = 200_000;
  private static final Set<StatusSeoConteudo> STATUS_PUBLICOS =
      Set.of(StatusSeoConteudo.APROVADO, StatusSeoConteudo.PUBLICADO);
  private static final Pattern HTML = Pattern.compile("<\\s*/?\\s*[a-z][^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern PROTOCOLO_PERIGOSO =
      Pattern.compile("(?i)(?:javascript|vbscript|data)\\s*:");

  private final SeoConteudoPaginaRepository conteudoRepository;
  private final SeoUrlRepository seoUrlRepository;
  private final Clock clock;

  @Autowired
  public ConteudoSiteService(
      SeoConteudoPaginaRepository conteudoRepository,
      SeoUrlRepository seoUrlRepository) {
    this(conteudoRepository, seoUrlRepository, Clock.systemUTC());
  }

  ConteudoSiteService(
      SeoConteudoPaginaRepository conteudoRepository,
      SeoUrlRepository seoUrlRepository,
      Clock clock) {
    this.conteudoRepository = conteudoRepository;
    this.seoUrlRepository = seoUrlRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ConteudoSiteDto> listarPublicados() {
    Map<String, SeoConteudoPaginaEntity> conteudos = conteudosPublicadosPorChave();
    return ConteudoSiteCatalogo.todas().stream()
        .map(definicao -> toDto(definicao, conteudos.get(definicao.chavePersistida())))
        .filter(dto -> dto.titulo() != null && dto.corpo() != null)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ConteudoSiteDto> listarAdministracao() {
    Map<String, SeoConteudoPaginaEntity> conteudos = conteudosPublicadosPorChave();
    return ConteudoSiteCatalogo.todas().stream()
        .map(definicao -> toDto(definicao, conteudos.get(definicao.chavePersistida())))
        .toList();
  }

  @Transactional
  public ConteudoSiteDto publicar(String contentKey, ConteudoSiteRequest request, UUID atorId) {
    Definicao definicao = ConteudoSiteCatalogo.porChavePublica(contentKey)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "conteudo do site nao encontrado"));
    if (atorId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
    }
    String titulo = validarTitulo(request == null ? null : request.titulo());
    String corpo = validarCorpo(request == null ? null : request.corpo());
    OffsetDateTime agora = OffsetDateTime.now(clock);

    SeoUrlEntity seoUrl = seoUrlRepository.findByCaminhoPublico(definicao.caminhoPublico())
        .orElseGet(() -> seoUrlRepository.saveAndFlush(
            SeoUrlEntity.criarConteudoInstitucional(
                UUID.randomUUID(),
                definicao.caminhoPublico(),
                agora)));

    SeoConteudoPaginaEntity conteudo = conteudoRepository
        .findTopBySeoUrlIdAndChaveOrderByVersaoDesc(seoUrl.getId(), definicao.chavePersistida())
        .orElseGet(() -> SeoConteudoPaginaEntity.criarPublicada(
            UUID.randomUUID(),
            seoUrl.getId(),
            definicao.chavePersistida(),
            titulo,
            corpo,
            atorId,
            agora));

    if (conteudo.getId() != null) {
      conteudo.publicar(titulo, corpo, atorId, agora);
    }
    return toDto(definicao, conteudoRepository.saveAndFlush(conteudo));
  }

  private Map<String, SeoConteudoPaginaEntity> conteudosPublicadosPorChave() {
    List<SeoConteudoPaginaEntity> conteudos = conteudoRepository.findAllByStatusIn(STATUS_PUBLICOS);
    if (conteudos.isEmpty()) {
      return Map.of();
    }
    Map<UUID, SeoUrlEntity> urls = seoUrlRepository.findAllById(
            conteudos.stream().map(SeoConteudoPaginaEntity::getSeoUrlId).collect(Collectors.toSet()))
        .stream()
        .collect(Collectors.toMap(SeoUrlEntity::getId, Function.identity()));
    Map<String, SeoConteudoPaginaEntity> resultado = new HashMap<>();
    for (SeoConteudoPaginaEntity conteudo : conteudos) {
      Definicao definicao = ConteudoSiteCatalogo.todas().stream()
          .filter(item -> item.chavePersistida().equals(conteudo.getChave()))
          .findFirst()
          .orElse(null);
      SeoUrlEntity url = urls.get(conteudo.getSeoUrlId());
      if (definicao != null
          && url != null
          && definicao.caminhoPublico().equals(url.getCaminhoPublico())) {
        resultado.put(definicao.chavePersistida(), conteudo);
      }
    }
    return resultado;
  }

  private ConteudoSiteDto toDto(Definicao definicao, SeoConteudoPaginaEntity entity) {
    if (entity == null) {
      return new ConteudoSiteDto(definicao.chavePublica(), null, null, null, null, null);
    }
    return new ConteudoSiteDto(
        definicao.chavePublica(),
        entity.getTitulo(),
        entity.getCorpoMarkdown(),
        entity.getVersao(),
        sha256(entity.getTitulo() + "\n" + entity.getCorpoMarkdown()),
        entity.getAtualizadoEm());
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private String validarTitulo(String valor) {
    String titulo = valor == null ? "" : valor.trim();
    if (titulo.isEmpty() || titulo.length() > TITULO_MAX || titulo.contains("<") || titulo.contains(">")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "titulo deve ter entre 1 e " + TITULO_MAX + " caracteres e nao pode conter HTML");
    }
    return titulo;
  }

  private String validarCorpo(String valor) {
    String corpo = valor == null ? "" : valor.replace("\r\n", "\n").trim();
    if (corpo.isEmpty() || corpo.length() > CORPO_MAX) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "conteudo deve ter entre 1 e " + CORPO_MAX + " caracteres");
    }
    if (HTML.matcher(corpo).find() || PROTOCOLO_PERIGOSO.matcher(corpo).find()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "conteudo aceita somente Markdown seguro, sem HTML ou protocolos executaveis");
    }
    return corpo;
  }
}
