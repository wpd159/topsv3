package br.com.topsdojob.v3.application.aviso;

import br.com.topsdojob.v3.application.aviso.AvisoDtos.AdminItem;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Edicao;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Indicadores;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Pagina;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.PublicoItem;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Versao;
import br.com.topsdojob.v3.persistence.entity.aviso.AvisoEntity;
import br.com.topsdojob.v3.persistence.repository.aviso.AvisoRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvisoService {

  private static final Set<String> LOCAIS =
      Set.of("SITE", "LOGIN_POPUP", "ANUNCIO_RODAPE");
  private static final Set<String> FREQUENCIAS =
      Set.of("SEMPRE", "UMA_VEZ", "DIARIO");
  private static final Set<String> STATUS =
      Set.of("RASCUNHO", "PUBLICADO", "ARQUIVADO");
  private static final Pattern PROTOCOLO_INSEGURO =
      Pattern.compile("(?i)\\b(?:javascript|vbscript|data)\\s*:");

  private final AvisoRepository repository;
  private final AvisoAuditoriaService auditoria;

  public AvisoService(
      AvisoRepository repository,
      AvisoAuditoriaService auditoria) {
    this.repository = repository;
    this.auditoria = auditoria;
  }

  @Transactional(readOnly = true)
  public Pagina<AdminItem> listarAdmin(
      String termo, String status, String local, int page, int size) {
    int pagina = Math.max(0, page);
    int tamanho = Math.min(100, Math.max(1, size));
    OffsetDateTime agora = agora();
    var result = repository.buscarAdmin(
        opcional(termo),
        filtro(status, STATUS, "status"),
        filtro(local, LOCAIS, "local de exibicao"),
        PageRequest.of(pagina, tamanho));
    return new Pagina<>(
        result.getContent().stream().map(item -> toAdmin(item, agora)).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.isFirst(),
        result.isLast());
  }

  @Transactional(readOnly = true)
  public Indicadores indicadores() {
    OffsetDateTime agora = agora();
    return new Indicadores(
        repository.count(),
        repository.countByStatus("RASCUNHO"),
        repository.countVigentes(agora),
        repository.countAgendados(agora),
        repository.countExpirados(agora),
        repository.countByStatus("ARQUIVADO"));
  }

  @Transactional(readOnly = true)
  public Pagina<PublicoItem> listarPublicos(
      String local, int page, int size) {
    String localSeguro = filtroObrigatorio(local, LOCAIS, "local de exibicao");
    int pagina = Math.max(0, page);
    int tamanho = Math.min(20, Math.max(1, size));
    var result = repository.buscarVigentes(
        localSeguro, agora(), PageRequest.of(pagina, tamanho));
    return new Pagina<>(
        result.getContent().stream().map(this::toPublico).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.isFirst(),
        result.isLast());
  }

  @Transactional
  public AdminItem criar(
      Edicao request,
      UUID atorId,
      String atorNome,
      String idempotencyKey,
      String requestId) {
    exigirAtor(atorId, atorNome);
    Dados dados = validar(request);
    String idempotencyKeySeguro = requestId(idempotencyKey);
    String requestIdSeguro = requestId(requestId);
    var existente = repository.findByCriadoPorUsuarioIdAndCriadoRequestId(
        atorId, idempotencyKeySeguro);
    if (existente.isPresent()) {
      if (!mesmosDados(existente.get(), dados)) {
        throw conflito("chave de idempotencia reutilizada com dados diferentes");
      }
      return toAdmin(existente.get(), agora());
    }
    OffsetDateTime agora = agora();
    AvisoEntity aviso = AvisoEntity.criarRascunho(
        UUID.randomUUID(),
        dados.titulo(),
        dados.descricao(),
        dados.local(),
        dados.frequencia(),
        dados.permiteDispensar(),
        dados.ativoDe(),
        dados.ativoAte(),
        atorId,
        textoSimples(atorNome, 1, 160, "nome do ator"),
        idempotencyKeySeguro,
        agora);
    AvisoEntity salvo = repository.saveAndFlush(aviso);
    auditoria.registrar(
        atorId, "AVISO_CRIADO", salvo, requestIdSeguro, agora, null);
    return toAdmin(salvo, agora);
  }

  @Transactional
  public AdminItem atualizar(
      UUID id, Edicao request, UUID atorId, String requestId) {
    exigirAtor(atorId, "ator");
    AvisoEntity aviso = buscarParaAtualizar(id);
    validarVersao(request == null ? null : request.versao(), aviso);
    Dados dados = validar(request);
    Map<String, Object> antes = auditoria.snapshot(aviso);
    OffsetDateTime agora = agora();
    aviso.atualizar(
        dados.titulo(),
        dados.descricao(),
        dados.local(),
        dados.frequencia(),
        dados.permiteDispensar(),
        dados.ativoDe(),
        dados.ativoAte(),
        atorId,
        agora);
    AvisoEntity salvo = repository.saveAndFlush(aviso);
    auditoria.registrar(
        atorId, "AVISO_ATUALIZADO", salvo, requestId(requestId), agora, antes);
    return toAdmin(salvo, agora);
  }

  @Transactional
  public AdminItem publicar(
      UUID id, Versao request, UUID atorId, String requestId) {
    exigirAtor(atorId, "ator");
    AvisoEntity aviso = buscarParaAtualizar(id);
    if ("PUBLICADO".equals(aviso.getStatus())) {
      return toAdmin(aviso, agora());
    }
    validarVersao(request == null ? null : request.versao(), aviso);
    if ("ARQUIVADO".equals(aviso.getStatus())) {
      throw conflito("aviso arquivado nao pode ser publicado");
    }
    Map<String, Object> antes = auditoria.snapshot(aviso);
    OffsetDateTime agora = agora();
    aviso.publicar(atorId, agora);
    AvisoEntity salvo = repository.saveAndFlush(aviso);
    auditoria.registrar(
        atorId, "AVISO_PUBLICADO", salvo, requestId(requestId), agora, antes);
    return toAdmin(salvo, agora);
  }

  @Transactional
  public AdminItem retirar(
      UUID id, Versao request, UUID atorId, String requestId) {
    exigirAtor(atorId, "ator");
    AvisoEntity aviso = buscarParaAtualizar(id);
    if ("RASCUNHO".equals(aviso.getStatus())) {
      return toAdmin(aviso, agora());
    }
    validarVersao(request == null ? null : request.versao(), aviso);
    if (!"PUBLICADO".equals(aviso.getStatus())) {
      throw conflito("aviso arquivado nao pode ser retirado");
    }
    Map<String, Object> antes = auditoria.snapshot(aviso);
    OffsetDateTime agora = agora();
    aviso.retirar(atorId, agora);
    AvisoEntity salvo = repository.saveAndFlush(aviso);
    auditoria.registrar(
        atorId, "AVISO_RETIRADO", salvo, requestId(requestId), agora, antes);
    return toAdmin(salvo, agora);
  }

  @Transactional
  public AdminItem arquivar(
      UUID id, Versao request, UUID atorId, String requestId) {
    exigirAtor(atorId, "ator");
    AvisoEntity aviso = buscarParaAtualizar(id);
    if ("ARQUIVADO".equals(aviso.getStatus())) {
      return toAdmin(aviso, agora());
    }
    validarVersao(request == null ? null : request.versao(), aviso);
    Map<String, Object> antes = auditoria.snapshot(aviso);
    OffsetDateTime agora = agora();
    aviso.arquivar(atorId, agora);
    AvisoEntity salvo = repository.saveAndFlush(aviso);
    auditoria.registrar(
        atorId, "AVISO_ARQUIVADO", salvo, requestId(requestId), agora, antes);
    return toAdmin(salvo, agora);
  }

  private AvisoEntity buscarParaAtualizar(UUID id) {
    return repository.findByIdForUpdate(id)
        .orElseThrow(() -> notFound("aviso nao encontrado"));
  }

  private Dados validar(Edicao request) {
    if (request == null) {
      throw badRequest("dados do aviso obrigatorios");
    }
    String titulo = textoSimples(request.titulo(), 3, 160, "titulo");
    String descricao = textoMultilinha(request.descricao(), 5, 4000, "descricao");
    String local = filtroObrigatorio(
        request.localExibicao(), LOCAIS, "local de exibicao");
    String frequencia = filtroObrigatorio(
        request.frequenciaExibicao(), FREQUENCIAS, "frequencia");
    OffsetDateTime inicio = request.ativoDe();
    OffsetDateTime fim = request.ativoAte();
    if (inicio != null && fim != null && !fim.isAfter(inicio)) {
      throw badRequest("fim da vigencia deve ser posterior ao inicio");
    }
    return new Dados(
        titulo,
        descricao,
        local,
        frequencia,
        !Boolean.FALSE.equals(request.permiteDispensar()),
        inicio,
        fim);
  }

  private String textoSimples(String value, int min, int max, String campo) {
    String normalizado = value == null
        ? ""
        : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    validarTexto(normalizado, min, max, campo);
    return normalizado;
  }

  private String textoMultilinha(
      String value, int min, int max, String campo) {
    String normalizado = value == null
        ? ""
        : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    validarTexto(normalizado, min, max, campo);
    if (normalizado.chars().anyMatch(
        valueCode -> Character.isISOControl(valueCode) && valueCode != '\n')) {
      throw badRequest(campo + " invalida");
    }
    return normalizado;
  }

  private void validarTexto(
      String value, int min, int max, String campo) {
    if (value.length() < min
        || value.length() > max
        || value.contains("<")
        || value.contains(">")
        || PROTOCOLO_INSEGURO.matcher(value).find()) {
      throw badRequest(campo + " invalida");
    }
  }

  private String opcional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return textoSimples(value, 1, 120, "termo");
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

  private void validarVersao(Long versao, AvisoEntity aviso) {
    if (versao == null || versao != aviso.getVersao()) {
      throw conflito("aviso foi alterado por outra sessao");
    }
  }

  private void exigirAtor(UUID atorId, String atorNome) {
    if (atorId == null || atorNome == null || atorNome.isBlank()) {
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

  private boolean mesmosDados(AvisoEntity aviso, Dados dados) {
    return aviso.getTitulo().equals(dados.titulo())
        && aviso.getDescricao().equals(dados.descricao())
        && aviso.getLocalExibicao().equals(dados.local())
        && aviso.getFrequenciaExibicao().equals(dados.frequencia())
        && aviso.isPermiteDispensar() == dados.permiteDispensar()
        && java.util.Objects.equals(aviso.getAtivoDe(), dados.ativoDe())
        && java.util.Objects.equals(aviso.getAtivoAte(), dados.ativoAte());
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private AdminItem toAdmin(AvisoEntity aviso, OffsetDateTime agora) {
    return new AdminItem(
        aviso.getId(),
        aviso.getTitulo(),
        aviso.getDescricao(),
        aviso.getLocalExibicao(),
        rotuloLocal(aviso.getLocalExibicao()),
        aviso.getFrequenciaExibicao(),
        rotuloFrequencia(aviso.getFrequenciaExibicao()),
        aviso.getStatus(),
        situacao(aviso, agora),
        aviso.isPermiteDispensar(),
        aviso.getAtivoDe(),
        aviso.getAtivoAte(),
        aviso.getCriadoPorNome(),
        aviso.getPublicadoEm(),
        aviso.getCriadoEm(),
        aviso.getAtualizadoEm(),
        aviso.getVersao());
  }

  private PublicoItem toPublico(AvisoEntity aviso) {
    return new PublicoItem(
        aviso.getId(),
        aviso.getTitulo(),
        aviso.getDescricao(),
        aviso.getLocalExibicao(),
        aviso.getFrequenciaExibicao(),
        aviso.isPermiteDispensar(),
        aviso.getAtivoDe(),
        aviso.getAtivoAte(),
        aviso.getPublicadoEm());
  }

  private String situacao(AvisoEntity aviso, OffsetDateTime agora) {
    if (!"PUBLICADO".equals(aviso.getStatus())) {
      return aviso.getStatus();
    }
    if (aviso.getAtivoDe() != null && aviso.getAtivoDe().isAfter(agora)) {
      return "AGENDADO";
    }
    if (aviso.getAtivoAte() != null && !aviso.getAtivoAte().isAfter(agora)) {
      return "EXPIRADO";
    }
    return "VIGENTE";
  }

  private String rotuloLocal(String local) {
    return switch (local) {
      case "LOGIN_POPUP" -> "Apos login";
      case "ANUNCIO_RODAPE" -> "Detalhe do anuncio";
      default -> "Site";
    };
  }

  private String rotuloFrequencia(String frequencia) {
    return switch (frequencia) {
      case "UMA_VEZ" -> "Uma vez";
      case "DIARIO" -> "Diario";
      default -> "Sempre";
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
      String titulo,
      String descricao,
      String local,
      String frequencia,
      boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte) {
  }
}
