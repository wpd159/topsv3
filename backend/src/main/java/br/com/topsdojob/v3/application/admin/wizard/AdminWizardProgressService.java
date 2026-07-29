package br.com.topsdojob.v3.application.admin.wizard;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.Dashboard;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.EtapaAtual;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.EtapaFunil;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.Indicadores;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.Item;
import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressDtos.Periodo;
import br.com.topsdojob.v3.persistence.repository.wizard.WizardProgressJdbcRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminWizardProgressService {

  static final ZoneId FUSO_CANONICO = ZoneId.of("America/Sao_Paulo");
  private static final Set<String> PERIODOS =
      Set.of("HOJE", "7_DIAS", "30_DIAS", "PERSONALIZADO");
  private static final Set<String> MODOS = Set.of("TODOS", "CREATE", "EDIT");
  private static final Set<String> STATUS = Set.of(
      "TODOS", "EM_PREENCHIMENTO", "AGUARDANDO_MODERACAO", "PUBLICADO", "REJEITADO");
  private static final Set<String> KYC =
      Set.of("TODOS", "NAO_INICIADO", "PENDENTE", "APROVADO", "REJEITADO");
  private static final Set<String> ANUNCIO_STATUS = Set.of(
      "TODOS", "SEM_ANUNCIO", "RASCUNHO", "PENDENTE_REVISAO", "APROVADO",
      "PUBLICADO", "PAUSADO", "REJEITADO", "BLOQUEADO", "REMOVIDO");
  private static final List<String> STEP_CODES = List.of(
      "PERFIL", "LOCALIZACAO", "SERVICOS", "FOTOS",
      "REVISAO", "PREMIUM", "KYC", "CONCLUIDO");
  private static final List<String> STEP_LABELS = List.of(
      "Perfil", "Localizacao", "Servicos", "Fotos e videos",
      "Revisao", "Premium opcional", "KYC", "Concluido");

  private final WizardProgressJdbcRepository repository;
  private final Clock clock;

  @Autowired
  public AdminWizardProgressService(WizardProgressJdbcRepository repository) {
    this(repository, Clock.systemUTC());
  }

  AdminWizardProgressService(WizardProgressJdbcRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Dashboard consultar(
      String periodo,
      LocalDate inicio,
      LocalDate fim,
      String termo,
      String modo,
      String status,
      String uf,
      String cidade,
      String kyc,
      String anuncioStatus,
      int page,
      int size) {
    OffsetDateTime agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    Datas datas = datas(periodo, inicio, fim, agora);
    String termoSeguro = textoOpcional(termo, 120);
    String modoSeguro = permitido(modo, MODOS, "TODOS", "modo invalido");
    String statusSeguro = permitido(status, STATUS, "TODOS", "status invalido");
    String kycSeguro = permitido(kyc, KYC, "TODOS", "status de KYC invalido");
    String anuncioStatusSeguro = permitido(
        anuncioStatus,
        ANUNCIO_STATUS,
        "TODOS",
        "estado do anuncio invalido");
    String ufSegura = uf(uf);
    String cidadeSegura = textoOpcional(cidade, 120);
    int pageSegura = Math.max(0, page);
    int sizeSegura = switch (size) {
      case 20, 30, 50 -> size;
      default -> 20;
    };

    MapSqlParameterSource filtros = WizardProgressJdbcRepository.filtros(
        datas.inicioUtc(),
        datas.fimExclusivoUtc(),
        termoSeguro,
        modoSeguro,
        statusSeguro,
        ufSegura,
        cidadeSegura,
        kycSeguro,
        anuncioStatusSeguro);
    var resumo = repository.resumo(filtros);
    long[] quantidadesFunil = repository.funil(filtros);
    Map<String, Long> etapasAtuais = repository.etapasAtuais(filtros);
    var pagina = repository.listar(filtros, pageSegura, sizeSegura);

    List<Item> itens = pagina.itens().stream()
        .map(item -> new Item(
            item.id(),
            item.usuarioId(),
            nomeSeguro(item.usuario()),
            mascararEmail(item.email()),
            item.modo(),
            item.status(),
            item.ultimoStep(),
            item.kycStatus(),
            item.anuncioId(),
            item.anuncioSlug(),
            item.anuncioTitulo(),
            item.anuncioStatus(),
            item.criadoEm(),
            item.atualizadoEm()))
        .toList();
    int totalPages = pagina.totalElementos() == 0
        ? 0
        : (int) Math.ceil((double) pagina.totalElementos() / sizeSegura);

    return new Dashboard(
        new Periodo(datas.inicio(), datas.fim(), FUSO_CANONICO.getId(), agora),
        new Indicadores(
            resumo.sessoesObservadas(),
            resumo.usuariosObservados(),
            resumo.kycNaoIniciado(),
            resumo.kycPendente(),
            resumo.kycAprovado(),
            resumo.emPreenchimento(),
            resumo.aguardandoModeracao(),
            resumo.anunciosRascunho(),
            resumo.anunciosRejeitados(),
            resumo.anunciosPublicados(),
            resumo.tempoMedioConclusaoMinutos()),
        funil(quantidadesFunil),
        etapasAtuais(etapasAtuais),
        new AdminPaginaDto<>(
            itens,
            pageSegura,
            sizeSegura,
            pagina.totalElementos(),
            totalPages,
            totalPages == 0 || pageSegura >= totalPages - 1));
  }

  private List<EtapaFunil> funil(long[] quantidades) {
    List<EtapaFunil> result = new ArrayList<>();
    long anterior = 0L;
    for (int index = 0; index < STEP_CODES.size(); index++) {
      long atual = quantidades[index];
      long perda = index == 0 ? 0L : Math.max(0L, anterior - atual);
      BigDecimal conversao = index == 0
          ? (atual == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100))
          : percentual(atual, anterior);
      result.add(new EtapaFunil(
          STEP_CODES.get(index),
          STEP_LABELS.get(index),
          atual,
          perda,
          conversao));
      anterior = atual;
    }
    return result;
  }

  private List<EtapaAtual> etapasAtuais(Map<String, Long> quantidades) {
    List<EtapaAtual> result = new ArrayList<>();
    for (int index = 0; index < STEP_CODES.size(); index++) {
      String codigo = STEP_CODES.get(index);
      result.add(new EtapaAtual(codigo, STEP_LABELS.get(index), quantidades.getOrDefault(codigo, 0L)));
    }
    return result;
  }

  private BigDecimal percentual(long atual, long anterior) {
    if (anterior <= 0L) return BigDecimal.ZERO;
    return BigDecimal.valueOf(atual)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(anterior), 1, RoundingMode.HALF_UP);
  }

  private Datas datas(
      String periodoRaw,
      LocalDate inicioRaw,
      LocalDate fimRaw,
      OffsetDateTime agora) {
    String periodo = permitido(periodoRaw, PERIODOS, "30_DIAS", "periodo invalido");
    LocalDate hoje = agora.atZoneSameInstant(FUSO_CANONICO).toLocalDate();
    LocalDate inicio;
    LocalDate fim;
    switch (periodo) {
      case "HOJE" -> {
        inicio = hoje;
        fim = hoje;
      }
      case "7_DIAS" -> {
        inicio = hoje.minusDays(6);
        fim = hoje;
      }
      case "30_DIAS" -> {
        inicio = hoje.minusDays(29);
        fim = hoje;
      }
      case "PERSONALIZADO" -> {
        if (inicioRaw == null || fimRaw == null) {
          throw invalido("periodo personalizado exige inicio e fim");
        }
        inicio = inicioRaw;
        fim = fimRaw;
      }
      default -> throw invalido("periodo invalido");
    }
    if (fim.isBefore(inicio)) throw invalido("fim anterior ao inicio");
    if (inicio.isBefore(hoje.minusDays(365))) {
      throw invalido("periodo maximo de 366 dias");
    }
    if (fim.isAfter(hoje)) throw invalido("fim nao pode estar no futuro");
    OffsetDateTime inicioUtc = inicio.atStartOfDay(FUSO_CANONICO)
        .toOffsetDateTime()
        .withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime fimExclusivoUtc = fim.plusDays(1)
        .atStartOfDay(FUSO_CANONICO)
        .toOffsetDateTime()
        .withOffsetSameInstant(ZoneOffset.UTC);
    return new Datas(inicio, fim, inicioUtc, fimExclusivoUtc);
  }

  private String permitido(
      String raw,
      Set<String> permitidos,
      String padrao,
      String mensagem) {
    String value = raw == null || raw.isBlank()
        ? padrao
        : raw.trim().toUpperCase(Locale.ROOT);
    if (!permitidos.contains(value)) throw invalido(mensagem);
    return value;
  }

  private String textoOpcional(String raw, int limite) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.trim();
    if (value.length() > limite) throw invalido("filtro excede o limite permitido");
    return value;
  }

  private String uf(String raw) {
    String value = textoOpcional(raw, 2);
    if (value == null) return null;
    value = value.toUpperCase(Locale.ROOT);
    if (!value.matches("^[A-Z]{2}$")) throw invalido("UF invalida");
    return value;
  }

  private String nomeSeguro(String value) {
    return value == null || value.isBlank() ? "Usuario sem nome" : value.trim();
  }

  private String mascararEmail(String email) {
    if (email == null || email.isBlank() || !email.contains("@")) return null;
    String[] parts = email.split("@", 2);
    String prefix = parts[0];
    String visivel = prefix.substring(0, Math.min(1, prefix.length()));
    return visivel + "***@" + parts[1];
  }

  private ResponseStatusException invalido(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private record Datas(
      LocalDate inicio,
      LocalDate fim,
      OffsetDateTime inicioUtc,
      OffsetDateTime fimExclusivoUtc) {
  }
}
