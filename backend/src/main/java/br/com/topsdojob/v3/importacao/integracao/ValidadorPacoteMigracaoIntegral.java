package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ValidadorPacoteMigracaoIntegral {

  private final ObjectMapper mapper;
  private final ValidadorManifestoMidiaFaseCinco validadorManifesto;

  public ValidadorPacoteMigracaoIntegral(
      ObjectMapper mapper,
      R2StorageProperties destinoProperties) {
    this.mapper = mapper;
    this.validadorManifesto = new ValidadorManifestoMidiaFaseCinco(
        mapper, destinoProperties);
  }

  public void validar(
      ArquivoPacoteMigracaoIntegral arquivo,
      String origemEsperada,
      Path diretorioManifestos) {
    if (arquivo == null) {
      throw new IllegalArgumentException("arquivo do pacote deve ser informado");
    }
    if (!ArquivoPacoteMigracaoIntegral.SCHEMA_VERSION.equals(arquivo.schemaVersion())) {
      throw new IllegalArgumentException("schemaVersion do pacote nao e suportada");
    }
    if (origemEsperada == null || !arquivo.origemId().equals(origemEsperada.trim())) {
      throw new IllegalArgumentException("origem do pacote diverge da origem declarada");
    }
    PacoteMigracaoIntegral pacote = arquivo.pacote();
    if (!"1".equals(pacote.versao())) {
      throw new IllegalArgumentException("versao interna do pacote nao e suportada");
    }
    validarFotografia(arquivo.snapshotSha256(), pacote);
    validarManifestos(arquivo, diretorioManifestos);
    validarContagens(arquivo);
    validarUnicidade(pacote);
    validarReferencias(pacote);
    validarMidias(pacote.faseCinco());
    String calculado = ArquivoPacoteMigracaoIntegral.fingerprint(mapper, arquivo);
    if (!calculado.equals(arquivo.fingerprint())) {
      throw new IllegalArgumentException("fingerprint do pacote diverge do conteudo");
    }
  }

  public Map<String, String> calcularHashesManifestos(Path diretorio) {
    Path raiz = diretorio == null ? null : diretorio.toAbsolutePath().normalize();
    if (raiz == null || !Files.isDirectory(raiz, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("diretorio de manifests invalido");
    }
    try (var caminhos = Files.walk(raiz)) {
      Map<String, String> hashes = new LinkedHashMap<>();
      caminhos.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
          .sorted()
          .forEach(path -> {
            if (Files.isSymbolicLink(path)) {
              throw new IllegalArgumentException("manifest simbolico nao e permitido");
            }
            String relativo = raiz.relativize(path).toString().replace('\\', '/');
            if (relativo.isBlank() || relativo.contains("..")) {
              throw new IllegalArgumentException("caminho de manifest invalido");
            }
            try {
              hashes.put(relativo, FingerprintMigracaoIntegral.sha256(Files.readAllBytes(path)));
            } catch (IOException exception) {
              throw new IllegalStateException("manifest nao pode ser lido", exception);
            }
          });
      if (hashes.isEmpty()) {
        throw new IllegalArgumentException("nenhum manifest foi encontrado");
      }
      return Map.copyOf(hashes);
    } catch (IOException exception) {
      throw new IllegalStateException("diretorio de manifests nao pode ser percorrido", exception);
    }
  }

  private void validarFotografia(String snapshotSha256, PacoteMigracaoIntegral pacote) {
    Set<String> ids = new HashSet<>(java.util.List.of(
        pacote.base().snapshotId(),
        pacote.faseUm().snapshotId(),
        pacote.faseDois().snapshotId(),
        pacote.faseTres().snapshotId(),
        pacote.faseQuatro().snapshotId()));
    if (ids.size() != 1 || !ids.contains(snapshotSha256)) {
      throw new IllegalArgumentException("snapshots das fases divergem do hash declarado");
    }
  }

  private void validarManifestos(
      ArquivoPacoteMigracaoIntegral arquivo,
      Path diretorioManifestos) {
    Map<String, String> atuais = calcularHashesManifestos(diretorioManifestos);
    if (!atuais.equals(arquivo.manifestosSha256())) {
      throw new IllegalArgumentException("fingerprints dos manifests divergiram");
    }
    String hashArquivo = atuais.get(arquivo.manifestoFaseCincoArquivo());
    if (hashArquivo == null) {
      throw new IllegalArgumentException("manifest da Fase 5 nao pertence ao diretorio validado");
    }
    ArquivoManifestoMidiaFaseCinco artefatoManifesto = lerManifestoSelecionado(
        diretorioManifestos, arquivo.manifestoFaseCincoArquivo());
    validadorManifesto.validar(
        artefatoManifesto, arquivo.origemId(), arquivo.snapshotSha256());
    ManifestoMidiaFaseCinco manifestoArquivo = artefatoManifesto.manifesto();
    if (!manifestoArquivo.equals(arquivo.pacote().faseCinco())
        || !manifestoArquivo.sha256().equals(arquivo.manifestoFaseCincoSha256())) {
      throw new IllegalArgumentException("fingerprint logico do manifest da Fase 5 divergiu");
    }
  }

  private ArquivoManifestoMidiaFaseCinco lerManifestoSelecionado(
      Path diretorioManifestos,
      String relativo) {
    Path raiz = diretorioManifestos.toAbsolutePath().normalize();
    Path arquivo = raiz.resolve(relativo).normalize();
    if (!arquivo.startsWith(raiz)
        || !Files.isRegularFile(arquivo, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(arquivo)) {
      throw new IllegalArgumentException("manifest selecionado nao e um arquivo regular seguro");
    }
    try {
      return mapper.readValue(arquivo.toFile(), ArquivoManifestoMidiaFaseCinco.class);
    } catch (IOException exception) {
      throw new IllegalArgumentException("manifest selecionado nao pode ser interpretado", exception);
    }
  }

  private void validarContagens(ArquivoPacoteMigracaoIntegral arquivo) {
    Map<String, Long> calculadas = ArquivoPacoteMigracaoIntegral.contagens(arquivo.pacote());
    if (!calculadas.equals(arquivo.contagens())) {
      throw new IllegalArgumentException("contagens do pacote divergem do conteudo");
    }
  }

  private void validarUnicidade(PacoteMigracaoIntegral pacote) {
    validarUnico(pacote.base().localidades(), item -> item.idOrigem(), "localidade");
    validarUnico(pacote.base().usuarios(), item -> item.idOrigem(), "usuario");
    validarUnico(pacote.base().credenciais(), item -> item.idOrigem(), "credencial");
    validarUnico(pacote.base().documentosKyc(), item -> item.idOrigem(), "documento KYC");
    validarUnico(pacote.base().usuariosStaging(), item -> item.idOrigem(), "usuario staging");
    validarUnico(pacote.base().favoritos(), item -> item.idOrigem(), "favorito");
    validarUnico(pacote.base().metricas(), item -> item.idOrigem(), "metrica");
    validarUnico(pacote.faseUm().anuncios(), item -> item.idOrigem(), "anuncio");
    validarUnico(pacote.faseDois().anunciosSeo(), item -> item.idOrigem(), "SEO de anuncio");
    validarUnico(pacote.faseTres().beneficios(), item -> item.idOrigem(), "beneficio");
    validarUnico(pacote.faseTres().opcoes(), item -> item.idOrigem(), "opcao de beneficio");
    validarUnico(pacote.faseTres().pacotes(), item -> item.idOrigem(), "pacote de credito");
    validarUnico(pacote.faseQuatro().pagamentos(), item -> item.idOrigem(), "pagamento");
    validarUnico(pacote.faseQuatro().gruposAtivacao(), item -> item.idOrigem(), "ativacao");
    validarUnico(
        pacote.faseQuatro().gruposAtivacao().stream()
            .flatMap(item -> item.ativacoes().stream())
            .toList(),
        item -> item.idOrigem(),
        "item de ativacao");
    validarUnico(pacote.faseQuatro().carteiras(), item -> item.idOrigem(), "carteira");
    validarUnico(pacote.faseCinco().itens(), item -> item.idOrigem(), "item de midia");
  }

  private void validarReferencias(PacoteMigracaoIntegral pacote) {
    Set<String> usuarios = ids(pacote.base().usuarios(), item -> item.idOrigem());
    Set<String> anuncios = ids(pacote.faseUm().anuncios(), item -> item.idOrigem());
    Set<String> localidades = pacote.base().localidades().stream()
        .map(item -> item.chaveMapeamento())
        .filter(java.util.Objects::nonNull)
        .collect(java.util.stream.Collectors.toSet());

    pacote.base().credenciais().forEach(item ->
        exigirReferencia(usuarios, item.usuarioOrigemId(), "credencial sem usuario"));
    pacote.base().documentosKyc().forEach(item ->
        exigirReferencia(usuarios, item.usuarioOrigemId(), "documento KYC sem usuario"));
    pacote.base().usuariosStaging().stream()
        .filter(item -> item.usuarioCanonicoOrigemId() != null)
        .forEach(item -> exigirReferencia(
            usuarios, item.usuarioCanonicoOrigemId(), "staging sem usuario canonico"));
    pacote.faseUm().anuncios().forEach(item -> {
      exigirReferencia(usuarios, item.proprietarioOrigemId(), "anuncio sem proprietario");
      if (item.localizacao() != null) {
        exigirReferencia(
            localidades, item.localizacao().chaveMapeamento(), "anuncio sem localidade");
      }
    });
    pacote.faseDois().anunciosSeo().forEach(item ->
        exigirReferencia(anuncios, item.idOrigem(), "SEO sem anuncio"));
    pacote.faseTres().opcoes().forEach(item ->
        exigirReferencia(
            ids(pacote.faseTres().beneficios(), beneficio -> beneficio.idOrigem()),
            item.beneficioOrigemId(),
            "opcao sem beneficio"));
    pacote.base().favoritos().forEach(item -> {
      exigirReferencia(usuarios, item.usuarioOrigemId(), "favorito sem usuario");
      exigirReferencia(anuncios, item.anuncioOrigemId(), "favorito sem anuncio");
    });
    pacote.base().metricas().forEach(item ->
        exigirReferencia(anuncios, item.anuncioOrigemId(), "metrica sem anuncio"));
    pacote.faseQuatro().pagamentos().forEach(item ->
        exigirReferencia(usuarios, item.usuarioOrigemId(), "pagamento sem usuario"));
    pacote.faseQuatro().gruposAtivacao().forEach(item -> {
      exigirReferencia(usuarios, item.usuarioOrigemId(), "ativacao sem usuario");
      if (item.anuncioOrigemId() != null) {
        exigirReferencia(anuncios, item.anuncioOrigemId(), "ativacao sem anuncio");
      }
    });
    pacote.faseQuatro().carteiras().forEach(item ->
        exigirReferencia(usuarios, item.usuarioOrigemId(), "carteira sem usuario"));

    Map<String, String> proprietarios = pacote.faseUm().anuncios().stream()
        .collect(java.util.stream.Collectors.toMap(
            item -> item.idOrigem(),
            item -> item.proprietarioOrigemId()));
    pacote.faseCinco().itens().stream()
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .forEach(item -> {
          switch (item.entidadeTipo()) {
            case ANUNCIO, REVISAO_ANUNCIO -> {
              exigirReferencia(anuncios, item.entidadeOrigemId(), "midia sem anuncio");
              exigirReferencia(usuarios, item.proprietarioOrigemId(), "midia sem proprietario");
              if (!item.proprietarioOrigemId().equals(
                  proprietarios.get(item.entidadeOrigemId()))) {
                throw new IllegalArgumentException("ownership da midia diverge do anuncio");
              }
            }
            case KYC -> exigirReferencia(
                usuarios, item.proprietarioOrigemId(), "KYC sem proprietario");
            case EDITORIAL -> {
              // O vinculo editorial e validado pelo importador de conteudo da Fase 2.
            }
            default -> throw new IllegalArgumentException(
                "dominio de midia importavel fora da migracao integral");
          }
        });
  }

  private void validarMidias(ManifestoMidiaFaseCinco manifesto) {
    for (ManifestoMidiaFaseCinco.Item item : manifesto.itens()) {
      validarLocalizadorOrigem(item.origem().localizador());
      if (item.decisao() == Decisao.IMPORTAR) {
        String chave = item.destino().chave();
        validarChave(chave, "destino");
        if (!chave.startsWith("hml/")) {
          throw new IllegalArgumentException("chave de destino fora do prefixo V3");
        }
      }
      if (item.origem().tipo() == TipoOrigem.OBJECT_STORAGE
          && item.origem().area() == null) {
        throw new IllegalArgumentException("midia R2 sem area de origem");
      }
    }
  }

  static void validarLocalizadorOrigem(String chave) {
    validarChave(chave, "origem");
  }

  private static void validarChave(String chave, String tipo) {
    if (chave == null || chave.isBlank()
        || chave.startsWith("/")
        || chave.contains("://")
        || chave.contains("?")
        || chave.contains("#")
        || chave.contains("\\")
        || chave.indexOf('\0') >= 0
        || chave.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("chave de " + tipo + " invalida");
    }
    for (String segmento : chave.split("/", -1)) {
      if (segmento.isBlank() || ".".equals(segmento) || "..".equals(segmento)) {
        throw new IllegalArgumentException("chave de " + tipo + " invalida");
      }
    }
  }

  private static void exigirReferencia(Set<String> ids, String id, String mensagem) {
    if (!ids.contains(id)) {
      throw new IllegalArgumentException(mensagem);
    }
  }

  private static <T> void validarUnico(
      Collection<T> itens,
      Function<T, String> id,
      String entidade) {
    Set<String> vistos = new HashSet<>();
    if (itens.stream().map(id).anyMatch(valor -> !vistos.add(valor))) {
      throw new IllegalArgumentException("id duplicado em " + entidade);
    }
  }

  private static <T> Set<String> ids(Collection<T> itens, Function<T, String> id) {
    return itens.stream().map(id).collect(java.util.stream.Collectors.toSet());
  }
}
