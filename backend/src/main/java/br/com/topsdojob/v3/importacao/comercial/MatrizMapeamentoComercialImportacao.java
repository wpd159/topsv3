package br.com.topsdojob.v3.importacao.comercial;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class MatrizMapeamentoComercialImportacao {
  public static final Set<Integer> DURACOES_PREMIUM_CANONICAS = Set.of(1, 7, 14, 30);
  public static final Set<String> MODOS_STORY_CANONICOS = Set.of("ANUNCIO", "MIDIA_UPLOAD");

  private final Map<String, LinhaBeneficio> beneficios;
  private final Map<String, LinhaPacote> pacotes;

  public MatrizMapeamentoComercialImportacao() {
    this(linhasBeneficioPadrao(), linhasPacotePadrao());
  }

  private MatrizMapeamentoComercialImportacao(
      List<LinhaBeneficio> beneficios,
      List<LinhaPacote> pacotes) {
    this.beneficios = indexarBeneficios(beneficios);
    this.pacotes = indexarPacotes(pacotes);
  }

  public Optional<LinhaBeneficio> beneficio(String codigoLegado) {
    return Optional.ofNullable(beneficios.get(chave(codigoLegado)));
  }

  public Optional<LinhaPacote> pacote(String codigoLegado) {
    return Optional.ofNullable(pacotes.get(chave(codigoLegado)));
  }

  public boolean duracaoPremiumCanonica(Integer dias) {
    return dias != null && DURACOES_PREMIUM_CANONICAS.contains(dias);
  }

  public static MatrizMapeamentoComercialImportacao comLinhasAdicionais(
      List<LinhaBeneficio> beneficiosAdicionais,
      List<LinhaPacote> pacotesAdicionais) {
    List<LinhaBeneficio> beneficios = new ArrayList<>(linhasBeneficioPadrao());
    beneficios.addAll(beneficiosAdicionais == null ? List.of() : beneficiosAdicionais);
    List<LinhaPacote> pacotes = new ArrayList<>(linhasPacotePadrao());
    pacotes.addAll(pacotesAdicionais == null ? List.of() : pacotesAdicionais);
    return new MatrizMapeamentoComercialImportacao(beneficios, pacotes);
  }

  private static List<LinhaBeneficio> linhasBeneficioPadrao() {
    return List.of(
        beneficio("ANUNCIO_TOPO", "ANUNCIO_TOPO", "Anuncio no topo",
            "Prioriza o anuncio nas listagens durante a vigencia.", true, 10),
        beneficio("FOTOS_EXTRA_5", "FOTOS_EXTRA_5", "Ate 10 fotos",
            "Amplia o limite do anuncio para ate dez fotos.", false, 30),
        beneficio("OCULTAR_IDADE", "OCULTAR_IDADE", "Ocultar idade",
            "Oculta a idade publica durante a vigencia.", false, 40),
        beneficio("WHATSAPP_CARD", "WHATSAPP_CARD", "WhatsApp no card",
            "Destaca o contato no card publico do anuncio.", false, 50),
        beneficio("CARROSSEL_FOTOS", "CARROSSEL_FOTOS", "Carrossel de fotos",
            "Habilita a navegacao em carrossel nas fotos publicas.", false, 60),
        beneficio("VIDEO_1", "VIDEO_1", "Video no anuncio",
            "Habilita um video aprovado no anuncio.", false, 70),
        new LinhaBeneficio(
            "STORIES",
            "STORIES",
            EscopoBeneficioPremium.ANUNCIO,
            Decisao.PRESERVAR_HISTORICO,
            PoliticaDuracao.FIXA_24_HORAS,
            PoliticaValor.CUSTO_CREDITOS_NAO_NEGATIVO,
            true,
            "Stories",
            "Identidade tecnica para ledger e ativacoes de Stories",
            false,
            0));
  }

  private static LinhaBeneficio beneficio(
      String legado,
      String destino,
      String nome,
      String descricao,
      boolean afetaRanking,
      int ordem) {
    return new LinhaBeneficio(
        legado,
        destino,
        EscopoBeneficioPremium.ANUNCIO,
        Decisao.IMPORTAR,
        PoliticaDuracao.OPCOES_1_7_14_30,
        PoliticaValor.CUSTO_CREDITOS_POSITIVO,
        false,
        nome,
        descricao,
        afetaRanking,
        ordem);
  }

  private static List<LinhaPacote> linhasPacotePadrao() {
    return List.of(
        new LinhaPacote("PACOTE_PRATA", "PACOTE_50", Decisao.IMPORTAR, 10),
        new LinhaPacote("PACOTE_OURO", "PACOTE_150", Decisao.IMPORTAR, 20),
        new LinhaPacote("PACOTE_DIAMANTE", "PACOTE_400", Decisao.IMPORTAR, 30));
  }

  private static Map<String, LinhaBeneficio> indexarBeneficios(List<LinhaBeneficio> linhas) {
    Map<String, LinhaBeneficio> resultado = new LinkedHashMap<>();
    for (LinhaBeneficio linha : linhas) {
      String chave = chave(linha.codigoLegado());
      if (resultado.putIfAbsent(chave, linha) != null) {
        throw new IllegalArgumentException("Codigo legado de beneficio duplicado na matriz: " + chave);
      }
    }
    return Map.copyOf(resultado);
  }

  private static Map<String, LinhaPacote> indexarPacotes(List<LinhaPacote> linhas) {
    Map<String, LinhaPacote> resultado = new LinkedHashMap<>();
    for (LinhaPacote linha : linhas) {
      String chave = chave(linha.codigoLegado());
      if (resultado.putIfAbsent(chave, linha) != null) {
        throw new IllegalArgumentException("Codigo legado de pacote duplicado na matriz: " + chave);
      }
    }
    return Map.copyOf(resultado);
  }

  private static String chave(String valor) {
    if (valor == null || valor.isBlank()) {
      return "";
    }
    return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "_")
        .replaceAll("^_+|_+$", "");
  }

  public enum Decisao {
    IMPORTAR,
    IMPORTAR_INATIVO,
    PRESERVAR_HISTORICO,
    DESCARTAR,
    QUARENTENA
  }

  public enum PoliticaDuracao {
    OPCOES_1_7_14_30,
    FIXA_24_HORAS,
    NAO_APLICAVEL
  }

  public enum PoliticaValor {
    CUSTO_CREDITOS_POSITIVO,
    CUSTO_CREDITOS_NAO_NEGATIVO,
    PACOTE_BRL_POSITIVO,
    HISTORICO_SEM_EXECUCAO
  }

  public record LinhaBeneficio(
      String codigoLegado,
      String codigoV3,
      EscopoBeneficioPremium escopo,
      Decisao decisao,
      PoliticaDuracao politicaDuracao,
      PoliticaValor politicaValor,
      boolean storiesSeparado,
      String nomeCanonico,
      String descricaoCanonica,
      boolean afetaRanking,
      int ordemCanonica) {
  }

  public record LinhaPacote(
      String codigoLegado,
      String codigoV3,
      Decisao decisao,
      int ordemCanonica) {
  }
}
