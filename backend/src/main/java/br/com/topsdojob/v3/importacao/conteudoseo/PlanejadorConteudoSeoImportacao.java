package br.com.topsdojob.v3.importacao.conteudoseo;

import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.RedirectLegado;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PlanejadorConteudoSeoImportacao {
  private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
  private static final Set<String> CATEGORIAS_FAQ =
      Set.of("GERAL", "CONTA", "PAGAMENTOS", "SEGURANCA", "ANUNCIOS");
  private static final Set<String> LOCAIS_AVISO =
      Set.of("SITE", "LOGIN_POPUP", "ANUNCIO_RODAPE");
  private static final Set<String> FREQUENCIAS_AVISO =
      Set.of("SEMPRE", "UMA_VEZ", "DIARIO");

  private final GeradorMetadataSeoImportacao metadata;

  public PlanejadorConteudoSeoImportacao(GeradorMetadataSeoImportacao metadata) {
    this.metadata = metadata;
  }

  public String normalizarSlug(String valor) {
    String informado = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    if (SLUG.matcher(informado).matches()) {
      return informado;
    }
    String normalizado = Normalizer.normalize(informado, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "")
        .replaceAll("-{2,}", "-");
    return normalizado.isBlank() ? null : normalizado;
  }

  public SlugPlanejado planejarSlug(
      String slugInformado,
      String idOrigem,
      boolean donoDoSlugNoSnapshot,
      Predicate<String> disponivel) {
    String base = normalizarSlug(slugInformado);
    if (base == null) {
      return new SlugPlanejado(null, true, true);
    }
    boolean normalizado = !base.equals(slugInformado);
    if (donoDoSlugNoSnapshot && disponivel.test(base)) {
      return new SlugPlanejado(base, normalizado, false);
    }
    String candidato = limitarSlug(base, 170) + "-" + hashCurto(idOrigem);
    return new SlugPlanejado(candidato, true, true);
  }

  public String categoriaFaq(String valor) {
    String categoria = normalizarEnum(valor);
    return CATEGORIAS_FAQ.contains(categoria) ? categoria : null;
  }

  public String localAviso(String valor) {
    String local = normalizarEnum(valor);
    return LOCAIS_AVISO.contains(local) ? local : "SITE";
  }

  public String frequenciaAviso(String valor) {
    String frequencia = normalizarEnum(valor);
    return FREQUENCIAS_AVISO.contains(frequencia) ? frequencia : "SEMPRE";
  }

  public StatusPlanejado statusBlog(
      String statusOrigem,
      OffsetDateTime publicadoEm,
      OffsetDateTime arquivadoEm) {
    return switch (normalizarEnum(statusOrigem)) {
      case "PUBLICADO", "ATIVO" -> publicadoEm == null
          ? StatusPlanejado.invalido()
          : new StatusPlanejado("PUBLICADO", publicadoEm, null, true);
      case "RASCUNHO", "INATIVO" ->
          new StatusPlanejado("RASCUNHO", null, null, false);
      case "ARQUIVADO" -> arquivadoEm == null
          ? StatusPlanejado.invalido()
          : new StatusPlanejado("ARQUIVADO", null, arquivadoEm, false);
      default -> StatusPlanejado.invalido();
    };
  }

  public StatusPlanejado statusAviso(
      String statusOrigem,
      OffsetDateTime publicadoEm,
      OffsetDateTime atualizadoEm) {
    return switch (normalizarEnum(statusOrigem)) {
      case "PUBLICADO", "ATIVO" -> publicadoEm == null
          ? StatusPlanejado.invalido()
          : new StatusPlanejado("PUBLICADO", publicadoEm, null, true);
      case "RASCUNHO" -> new StatusPlanejado("RASCUNHO", null, null, false);
      case "INATIVO", "ARQUIVADO" ->
          new StatusPlanejado("ARQUIVADO", null, atualizadoEm, false);
      default -> StatusPlanejado.invalido();
    };
  }

  public StatusPlanejado statusInstitucional(
      String statusOrigem,
      OffsetDateTime publicadoEm,
      OffsetDateTime atualizadoEm) {
    return switch (normalizarEnum(statusOrigem)) {
      case "PUBLICADO", "APROVADO", "ATIVO" -> publicadoEm == null
          ? StatusPlanejado.invalido()
          : new StatusPlanejado("PUBLICADO", publicadoEm, null, true);
      case "RASCUNHO", "INATIVO" ->
          new StatusPlanejado("RASCUNHO", null, null, false);
      case "ARQUIVADO" ->
          new StatusPlanejado("ARQUIVADO", null, atualizadoEm, false);
      default -> StatusPlanejado.invalido();
    };
  }

  public boolean faqPublicavel(
      boolean ativa,
      String status,
      OffsetDateTime publicadoEm) {
    return ativa
        && Set.of("PUBLICADO", "ATIVO").contains(normalizarEnum(status))
        && publicadoEm != null;
  }

  public RedirectPlanejado planejarRedirect(
      RedirectLegado atual,
      List<RedirectLegado> redirects) {
    if (!atual.compatibilidadeSemantica()
        || !metadata.caminhoPublicoSeguro(atual.origemCaminho())
        || !metadata.caminhoPublicoSeguro(atual.destinoCaminho())
        || atual.origemCaminho().equals(atual.destinoCaminho())) {
      return RedirectPlanejado.invalido(CodigoPendenciaImportacao.REDIRECT_INVALIDO);
    }
    Map<String, RedirectLegado> porOrigem = new HashMap<>();
    redirects.stream()
        .sorted((a, b) -> a.idOrigem().compareTo(b.idOrigem()))
        .forEach(item -> porOrigem.putIfAbsent(item.origemCaminho(), item));
    String destino = atual.destinoCaminho();
    Set<String> visitados = new HashSet<>();
    visitados.add(atual.origemCaminho());
    int saltos = 0;
    while (porOrigem.containsKey(destino)) {
      if (!visitados.add(destino)) {
        return RedirectPlanejado.invalido(CodigoPendenciaImportacao.REDIRECT_CICLICO);
      }
      RedirectLegado proximo = porOrigem.get(destino);
      if (!proximo.compatibilidadeSemantica()) {
        return RedirectPlanejado.invalido(CodigoPendenciaImportacao.REDIRECT_INVALIDO);
      }
      destino = proximo.destinoCaminho();
      saltos++;
    }
    if (!metadata.caminhoPublicoSeguro(destino)
        || atual.origemCaminho().equals(destino)) {
      return RedirectPlanejado.invalido(CodigoPendenciaImportacao.REDIRECT_INVALIDO);
    }
    return new RedirectPlanejado(destino, saltos, null);
  }

  private String normalizarEnum(String valor) {
    return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
  }

  private String limitarSlug(String valor, int limite) {
    return valor.length() <= limite ? valor : valor.substring(0, limite).replaceAll("-+$", "");
  }

  private String hashCurto(String valor) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(valor.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 8);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  public record SlugPlanejado(String slug, boolean normalizado, boolean colisao) {
  }

  public record StatusPlanejado(
      String status,
      OffsetDateTime publicadoEm,
      OffsetDateTime arquivadoEm,
      boolean publico) {
    private static StatusPlanejado invalido() {
      return new StatusPlanejado(null, null, null, false);
    }

    public boolean valido() {
      return status != null;
    }
  }

  public record RedirectPlanejado(
      String destinoFinal,
      int saltosEliminados,
      CodigoPendenciaImportacao erro) {
    private static RedirectPlanejado invalido(CodigoPendenciaImportacao erro) {
      return new RedirectPlanejado(null, 0, erro);
    }

    public boolean valido() {
      return erro == null;
    }
  }
}
