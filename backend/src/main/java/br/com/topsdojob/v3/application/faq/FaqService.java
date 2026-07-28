package br.com.topsdojob.v3.application.faq;

import br.com.topsdojob.v3.application.faq.FaqDtos.Edicao;
import br.com.topsdojob.v3.application.faq.FaqDtos.Item;
import br.com.topsdojob.v3.application.faq.FaqDtos.Ordem;
import br.com.topsdojob.v3.application.faq.FaqDtos.Versao;
import br.com.topsdojob.v3.persistence.entity.faq.FaqEntity;
import br.com.topsdojob.v3.persistence.repository.faq.FaqRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FaqService {

  private static final Set<String> CATEGORIAS =
      Set.of("GERAL", "CONTA", "PAGAMENTOS", "SEGURANCA", "ANUNCIOS");
  private static final Set<String> STATUS =
      Set.of("RASCUNHO", "PUBLICADO", "ARQUIVADO");
  private static final Pattern PROTOCOLO_INSEGURO =
      Pattern.compile("(?i)\\b(?:javascript|vbscript|data)\\s*:");

  private final FaqRepository repository;
  private final FaqAuditoriaService auditoria;

  public FaqService(FaqRepository repository, FaqAuditoriaService auditoria) {
    this.repository = repository;
    this.auditoria = auditoria;
  }

  @Transactional(readOnly = true)
  public List<Item> listarAdmin(String termo, String status, String categoria) {
    String termoSeguro = opcional(termo);
    String statusSeguro = filtro(status, STATUS, "status");
    String categoriaSegura = filtro(categoria, CATEGORIAS, "categoria");
    return repository.buscarAdmin(termoSeguro, statusSeguro, categoriaSegura)
        .stream()
        .map(this::toItem)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Item> listarPublicadas() {
    return repository.findAllByStatusOrderByOrdemAscAtualizadoEmDescIdAsc("PUBLICADO")
        .stream()
        .map(this::toItem)
        .toList();
  }

  @Transactional
  public Item criar(Edicao request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    String requestIdSeguro = requestId(requestId);
    var existente = repository.findByCriadoPorUsuarioIdAndCriadoRequestId(
        atorId, requestIdSeguro);
    if (existente.isPresent()) {
      return toItem(existente.get());
    }
    Dados dados = validar(request);
    OffsetDateTime agora = agora();
    FaqEntity faq = FaqEntity.criarRascunho(
        UUID.randomUUID(),
        dados.pergunta(),
        dados.resposta(),
        dados.categoria(),
        dados.ordem(),
        atorId,
        requestIdSeguro,
        agora);
    FaqEntity salva = repository.saveAndFlush(faq);
    auditoria.registrar(
        atorId, "FAQ_CRIADA", salva, requestIdSeguro, agora, null);
    return toItem(salva);
  }

  @Transactional
  public Item atualizar(
      UUID id, Edicao request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    FaqEntity faq = buscarParaAtualizar(id);
    validarVersao(request == null ? null : request.versao(), faq);
    Dados dados = validar(request);
    Map<String, Object> antes = auditoria.snapshot(faq);
    OffsetDateTime agora = agora();
    faq.atualizar(
        dados.pergunta(),
        dados.resposta(),
        dados.categoria(),
        dados.ordem(),
        atorId,
        agora);
    FaqEntity salva = repository.saveAndFlush(faq);
    auditoria.registrar(
        atorId, "FAQ_ATUALIZADA", salva, requestId(requestId), agora, antes);
    return toItem(salva);
  }

  @Transactional
  public Item publicar(
      UUID id, Versao request, UUID atorId, String requestId) {
    FaqEntity faq = prepararAcao(id, request, atorId);
    if ("PUBLICADO".equals(faq.getStatus())) {
      return toItem(faq);
    }
    Map<String, Object> antes = auditoria.snapshot(faq);
    OffsetDateTime agora = agora();
    faq.publicar(atorId, agora);
    FaqEntity salva = repository.saveAndFlush(faq);
    auditoria.registrar(
        atorId, "FAQ_PUBLICADA", salva, requestId(requestId), agora, antes);
    return toItem(salva);
  }

  @Transactional
  public Item retirar(
      UUID id, Versao request, UUID atorId, String requestId) {
    FaqEntity faq = prepararAcao(id, request, atorId);
    if ("RASCUNHO".equals(faq.getStatus())) {
      return toItem(faq);
    }
    if (!"PUBLICADO".equals(faq.getStatus())) {
      throw conflito("FAQ arquivada nao pode ser retirada da publicacao");
    }
    Map<String, Object> antes = auditoria.snapshot(faq);
    OffsetDateTime agora = agora();
    faq.retirar(atorId, agora);
    FaqEntity salva = repository.saveAndFlush(faq);
    auditoria.registrar(
        atorId, "FAQ_RETIRADA", salva, requestId(requestId), agora, antes);
    return toItem(salva);
  }

  @Transactional
  public Item arquivar(
      UUID id, Versao request, UUID atorId, String requestId) {
    FaqEntity faq = prepararAcao(id, request, atorId);
    if ("ARQUIVADO".equals(faq.getStatus())) {
      return toItem(faq);
    }
    Map<String, Object> antes = auditoria.snapshot(faq);
    OffsetDateTime agora = agora();
    faq.arquivar(atorId, agora);
    FaqEntity salva = repository.saveAndFlush(faq);
    auditoria.registrar(
        atorId, "FAQ_ARQUIVADA", salva, requestId(requestId), agora, antes);
    return toItem(salva);
  }

  @Transactional
  public Item reordenar(
      UUID id, Ordem request, UUID atorId, String requestId) {
    exigirAtor(atorId);
    FaqEntity faq = buscarParaAtualizar(id);
    validarVersao(request == null ? null : request.versao(), faq);
    int ordem = validarOrdem(request == null ? null : request.ordem());
    Map<String, Object> antes = auditoria.snapshot(faq);
    OffsetDateTime agora = agora();
    faq.reordenar(ordem, atorId, agora);
    FaqEntity salva = repository.saveAndFlush(faq);
    auditoria.registrar(
        atorId, "FAQ_REORDENADA", salva, requestId(requestId), agora, antes);
    return toItem(salva);
  }

  private FaqEntity prepararAcao(
      UUID id, Versao request, UUID atorId) {
    exigirAtor(atorId);
    FaqEntity faq = buscarParaAtualizar(id);
    validarVersao(request == null ? null : request.versao(), faq);
    return faq;
  }

  private FaqEntity buscarParaAtualizar(UUID id) {
    return repository.findByIdForUpdate(id)
        .orElseThrow(() -> notFound("FAQ nao encontrada"));
  }

  private Dados validar(Edicao request) {
    if (request == null) {
      throw badRequest("dados da FAQ obrigatorios");
    }
    String pergunta = textoSimples(request.pergunta(), 5, 240, "pergunta");
    String resposta = resposta(request.resposta());
    String categoria = filtroObrigatorio(request.categoria(), CATEGORIAS, "categoria");
    int ordem = validarOrdem(request.ordem());
    return new Dados(pergunta, resposta, categoria, ordem);
  }

  private String textoSimples(String value, int min, int max, String campo) {
    String normalizado = value == null
        ? ""
        : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    if (normalizado.length() < min
        || normalizado.length() > max
        || normalizado.contains("<")
        || normalizado.contains(">")
        || PROTOCOLO_INSEGURO.matcher(normalizado).find()) {
      throw badRequest(campo + " invalida");
    }
    return normalizado;
  }

  private String resposta(String value) {
    String normalizado = value == null
        ? ""
        : value.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\t", "  ")
            .trim();
    if (normalizado.length() < 10
        || normalizado.length() > 4000
        || normalizado.contains("<")
        || normalizado.contains(">")
        || PROTOCOLO_INSEGURO.matcher(normalizado).find()
        || normalizado.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n')) {
      throw badRequest("resposta invalida");
    }
    return normalizado;
  }

  private int validarOrdem(Integer ordem) {
    int valor = ordem == null ? 0 : ordem;
    if (valor < 0 || valor > 100000) {
      throw badRequest("ordem invalida");
    }
    return valor;
  }

  private String filtro(
      String value, Set<String> permitidos, String campo) {
    if (value == null || value.isBlank() || "TODOS".equalsIgnoreCase(value)) {
      return null;
    }
    return filtroObrigatorio(value, permitidos, campo);
  }

  private String filtroObrigatorio(
      String value, Set<String> permitidos, String campo) {
    String normalizado = value == null
        ? ""
        : value.trim().toUpperCase(Locale.ROOT);
    if (!permitidos.contains(normalizado)) {
      throw badRequest(campo + " invalida");
    }
    return normalizado;
  }

  private String opcional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return textoSimples(value, 1, 120, "termo");
  }

  private void validarVersao(Long versao, FaqEntity faq) {
    if (versao == null || versao != faq.getVersao()) {
      throw conflito("FAQ foi alterada por outra sessao");
    }
  }

  private void exigirAtor(UUID atorId) {
    if (atorId == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
    }
  }

  private String requestId(String requestId) {
    String value = requestId == null ? "" : requestId.trim();
    if (value.length() < 8
        || value.length() > 128
        || !value.matches("^[A-Za-z0-9._:-]+$")) {
      throw badRequest("requestId administrativo invalido");
    }
    return value;
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private Item toItem(FaqEntity faq) {
    return new Item(
        faq.getId(),
        faq.getPergunta(),
        faq.getResposta(),
        faq.getCategoria(),
        rotuloCategoria(faq.getCategoria()),
        faq.getStatus(),
        faq.getOrdem(),
        faq.getPublicadoEm(),
        faq.getAtualizadoEm(),
        faq.getVersao());
  }

  private String rotuloCategoria(String categoria) {
    return switch (categoria) {
      case "CONTA" -> "Conta";
      case "PAGAMENTOS" -> "Pagamentos";
      case "SEGURANCA" -> "Seguranca";
      case "ANUNCIOS" -> "Anuncios";
      default -> "Geral";
    };
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException conflito(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private record Dados(
      String pergunta,
      String resposta,
      String categoria,
      int ordem) {
  }
}
