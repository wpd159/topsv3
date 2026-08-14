package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.anuncio.ImportadorAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios;
import br.com.topsdojob.v3.importacao.anuncio.ResultadoImportacaoAnuncios;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalidadeMapeada;
import br.com.topsdojob.v3.importacao.comercial.ImportadorConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.comercial.ResultadoImportacaoConfiguracaoComercial;
import br.com.topsdojob.v3.importacao.conteudoseo.ImportadorConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.conteudoseo.ResultadoImportacaoConteudoSeo;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.ReferenciaAnuncioSeo;
import br.com.topsdojob.v3.importacao.financeiro.ImportadorFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.financeiro.ResultadoImportacaoFinanceiraFaseQuatro;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.integracao.ImportadorBaseMigracaoIntegral.ResultadoLote;
import br.com.topsdojob.v3.importacao.integracao.PoliticaKycMigracaoIntegral.Consolidacao;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.LocalidadeLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoLocalidade;
import br.com.topsdojob.v3.importacao.midia.AdaptadorManifestoMidiaFasesAnteriores;
import br.com.topsdojob.v3.importacao.midia.CheckpointMidiaMigracaoJdbc;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.MotorCopiaMidiaFaseCinco.RelatorioExecucao;
import br.com.topsdojob.v3.importacao.midia.ReconciliadorMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ReconciliadorMidiaFaseCinco.Reconciliacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
public class ExecutorImportadoresCanonicos implements ExecutorFasesMigracaoIntegral {

  private final JdbcTemplate jdbc;
  private final PlatformTransactionManager transactionManager;
  private final ObjectMapper mapper;
  private final ImportadorBaseMigracaoIntegral base;
  private final ImportadorAnunciosFaseUm faseUm;
  private final ImportadorConteudoSeoFaseDois faseDois;
  private final ImportadorConfiguracaoComercialFaseTres faseTres;
  private final ImportadorFinanceiroFaseQuatro faseQuatro;
  private final ObjectProvider<ArmazenamentosMigracaoIntegral> armazenamentosProvider;
  private final PoliticaKycMigracaoIntegral politicaKyc = new PoliticaKycMigracaoIntegral();
  private final AdaptadorManifestoMidiaFasesAnteriores adaptadorMidia =
      new AdaptadorManifestoMidiaFasesAnteriores();
  private final ReconciliadorMidiaFaseCinco reconciliadorMidia =
      new ReconciliadorMidiaFaseCinco();
  private final FingerprintMigracaoIntegral fingerprint;

  public ExecutorImportadoresCanonicos(
      DataSource dataSource,
      PlatformTransactionManager transactionManager,
      ObjectMapper mapper,
      ImportadorAnunciosFaseUm faseUm,
      ImportadorConteudoSeoFaseDois faseDois,
      ImportadorConfiguracaoComercialFaseTres faseTres,
      ImportadorFinanceiroFaseQuatro faseQuatro,
      ObjectProvider<ArmazenamentosMigracaoIntegral> armazenamentosProvider) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transactionManager = transactionManager;
    this.mapper = mapper;
    this.base = new ImportadorBaseMigracaoIntegral(dataSource, transactionManager, mapper);
    this.faseUm = faseUm;
    this.faseDois = faseDois;
    this.faseTres = faseTres;
    this.faseQuatro = faseQuatro;
    this.armazenamentosProvider = armazenamentosProvider;
    this.fingerprint = new FingerprintMigracaoIntegral(mapper);
  }

  @Override
  public ResultadoEtapa executar(FaseMigracaoIntegral fase, Contexto contexto) {
    long inicio = System.nanoTime();
    ResultadoEtapa resultado = switch (fase) {
      case REFERENCIAS_LOCALIDADES -> localidades(contexto);
      case USUARIOS_MAPEAMENTOS -> usuarios(contexto);
      case CREDENCIAIS -> credenciais(contexto);
      case KYC_PLANEJAMENTO -> planejarKyc(contexto);
      case CONTEUDO_SEO -> conteudoSeo(contexto, false);
      case CATALOGO_COMERCIAL -> comercial(contexto);
      case MANIFESTO_MIDIA -> midias(contexto);
      case KYC_PERSISTENCIA -> persistirKyc(contexto);
      case ANUNCIOS_E_VINCULOS -> anuncios(contexto);
      case SEO_ANUNCIOS -> conteudoSeo(contexto, true);
      case FINANCEIRO -> financeiro(contexto);
      case FAVORITOS_METRICAS -> complementos(contexto);
      case RECONCILIADORES -> reconciliar(contexto);
      case FINGERPRINT -> fingerprint(contexto);
    };
    Duration duracao = Duration.ofNanos(Math.max(0, System.nanoTime() - inicio));
    contexto.metricas().registrar(
        fase,
        duracao,
        resultado.processados(),
        resultado.retries(),
        resultado.quarentenas(),
        resultado.bytes());
    return resultado;
  }

  private ResultadoEtapa localidades(Contexto contexto) {
    return lotes(() -> base.importarLocalidades(
        contexto.pacote().base(), contexto.execucaoId(), contexto.tamanhoLote()));
  }

  private ResultadoEtapa usuarios(Contexto contexto) {
    return lotes(() -> base.importarUsuarios(
        contexto.pacote().base(), contexto.execucaoId(), contexto.tamanhoLote()));
  }

  private ResultadoEtapa credenciais(Contexto contexto) {
    return lotes(() -> base.importarCredenciais(
        contexto.pacote().base(), contexto.execucaoId(), contexto.tamanhoLote()));
  }

  private ResultadoEtapa planejarKyc(Contexto contexto) {
    ResultadoLote resultado = base.registrarPlanejamentoKycEOrfaos(
        contexto.pacote().base(), contexto.execucaoId(), consolidacao(contexto));
    return resultado(resultado, Map.of(
        "documentosCanonicos", consolidacao(contexto).canonicos().size(),
        "duplicatasConsolidadas", consolidacao(contexto).historicosConsolidados().size(),
        "vinculosCruzados", consolidacao(contexto).quarentena().size(),
        "coorteHistorica233", "NAO_RECONSTRUIDA_NAO_BLOQUEADORA"));
  }

  private ResultadoEtapa persistirKyc(Contexto contexto) {
    UUID ator = contexto.pacote().faseUm().atorSistemaV3Id();
    return lotes(() -> base.importarKyc(
        contexto.pacote().base(),
        contexto.execucaoId(),
        consolidacao(contexto),
        contexto.tamanhoLote(),
        ator));
  }

  private ResultadoEtapa conteudoSeo(Contexto contexto, boolean somenteAnuncios) {
    SnapshotConteudoSeoFaseDois.Snapshot snapshot = snapshotFaseDois(contexto, somenteAnuncios);
    long processados = 0;
    long quarentenas = 0;
    ResultadoImportacaoConteudoSeo resultado;
    do {
      resultado = faseDois.executar(snapshot, contexto.tamanhoLote());
      processados += resultado.processadosNestaChamada();
      quarentenas = resultado.quarentena();
      interromperSeSolicitado();
    } while (resultado.restantes() > 0);
    return new ResultadoEtapa(
        processados,
        quarentenas,
        0,
        0,
        Map.of(
            "importados", resultado.importados(),
            "publicados", resultado.publicados(),
            "noindex", resultado.noindex(),
            "quarentena", resultado.quarentena()));
  }

  private ResultadoEtapa comercial(Contexto contexto) {
    long processados = 0;
    ResultadoImportacaoConfiguracaoComercial resultado;
    do {
      resultado = faseTres.executar(contexto.pacote().faseTres(), contexto.tamanhoLote());
      processados += resultado.processadosNestaChamada();
      interromperSeSolicitado();
    } while (resultado.restantes() > 0);
    long quarentenas = resultado.produtosQuarentena()
        + resultado.opcoesQuarentena()
        + resultado.pacotesQuarentena()
        + resultado.storiesQuarentena();
    return new ResultadoEtapa(
        processados,
        quarentenas,
        0,
        0,
        Map.of(
            "produtos", resultado.produtosImportadosAtivos()
                + resultado.produtosImportadosInativos(),
            "opcoes", resultado.opcoesImportadas(),
            "pacotesAtivos", resultado.pacotesImportadosAtivos(),
            "quarentena", quarentenas));
  }

  private ResultadoEtapa midias(Contexto contexto) {
    ManifestoMidiaFaseCinco manifesto = consolidacao(contexto).manifestoSeguro();
    ArmazenamentosMigracaoIntegral armazenamentos = armazenamentos();
    validarBuckets(manifesto, armazenamentos);
    if (contexto.modo() == ModoMigracaoIntegral.APPLY && !contexto.retomada()) {
      boolean ocupado = manifesto.itens().stream()
          .filter(item -> item.decisao() == Decisao.IMPORTAR)
          .anyMatch(item -> armazenamentos.destino().exists(
              item.destino().area(), item.destino().chave()));
      if (ocupado) {
        throw new IllegalStateException("destino de objetos nao esta vazio");
      }
    }
    RelatorioExecucao execucao = motor(armazenamentos, contexto).executar(
        contexto.execucaoId(),
        manifesto,
        contexto.modo() == ModoMigracaoIntegral.DRY_RUN);
    Reconciliacao reconciliacao = reconciliadorMidia.reconciliar(manifesto, execucao);
    if (!reconciliacao.aprovada()) {
      throw new IllegalStateException("reconciliacao da Fase 5 nao fechou");
    }
    long retries = execucao.resultados().stream()
        .filter(item -> "FALHA_TRANSITORIA_ESGOTADA".equals(item.motivo()))
        .count();
    return new ResultadoEtapa(
        execucao.resultados().size(),
        reconciliacao.quarentena(),
        retries,
        reconciliacao.bytesValidados(),
        Map.of(
            "manifesto", execucao.manifestoSha256(),
            "objetos", reconciliacao.objetosConcluidos(),
            "bytes", reconciliacao.bytesValidados(),
            "quarentena", reconciliacao.quarentena()));
  }

  private ResultadoEtapa anuncios(Contexto contexto) {
    SnapshotAnunciosFaseUm.Snapshot snapshot = snapshotFaseUm(contexto);
    long processados = 0;
    ResultadoImportacaoAnuncios resultado;
    do {
      resultado = faseUm.executar(snapshot, contexto.tamanhoLote());
      processados += resultado.processadosNestaChamada();
      interromperSeSolicitado();
    } while (resultado.restantes() > 0);
    if (resultado.analisados() > 0 && resultado.publicos() == 0) {
      throw new IllegalStateException("nenhum anuncio do snapshot ficou publicavel");
    }
    return new ResultadoEtapa(
        processados,
        resultado.quarentena(),
        0,
        0,
        Map.of(
            "analisados", resultado.analisados(),
            "importados", resultado.importados(),
            "publicos", resultado.publicos(),
            "revisao", resultado.emRevisao(),
            "quarentena", resultado.quarentena(),
            "descartados", resultado.descartados()));
  }

  private ResultadoEtapa financeiro(Contexto contexto) {
    SnapshotFinanceiroFaseQuatro.Snapshot snapshot = snapshotFaseQuatro(contexto);
    long processados = 0;
    ResultadoImportacaoFinanceiraFaseQuatro resultado;
    do {
      resultado = faseQuatro.executar(snapshot, contexto.tamanhoLote());
      processados += resultado.processadosNestaChamada();
      interromperSeSolicitado();
    } while (resultado.restantes() > 0);
    long quarentenas = resultado.pagamentosQuarentena()
        + resultado.gruposQuarentena()
        + resultado.ativacoesQuarentena()
        + resultado.carteirasQuarentena();
    if (resultado.movimentosSaldoInicial() != resultado.carteirasComSaldoImportadas()) {
      throw new IllegalStateException("saldo inicial nao fechou por carteira importada");
    }
    return new ResultadoEtapa(
        processados,
        quarentenas,
        0,
        0,
        Map.ofEntries(
            Map.entry("pagamentosImportados", resultado.pagamentosImportados()),
            Map.entry("pagamentosAprovados", resultado.pagamentosAprovadosImportados()),
            Map.entry("eventosHistoricos", resultado.eventosHistoricos()),
            Map.entry("ativacoesImportadas", resultado.ativacoesImportadas()),
            Map.entry("ativacoesDescartadas", resultado.ativacoesDescartadas()),
            Map.entry("saldoImportado", resultado.saldoInicialImportado()),
            Map.entry("saldoQuarentena", resultado.saldoPositivoQuarentena()),
            Map.entry("movimentosSaldoInicial", resultado.movimentosSaldoInicial()),
            Map.entry("movimentosPagamento", 0),
            Map.entry("quarentena", quarentenas)));
  }

  private ResultadoEtapa complementos(Contexto contexto) {
    return resultado(base.importarComplementos(
        contexto.pacote().base(), contexto.execucaoId(), contexto.fingerprintPacote()), Map.of());
  }

  private ResultadoEtapa reconciliar(Contexto contexto) {
    var baseResultado = base.reconciliar(contexto.pacote().base(), contexto.execucaoId());
    if (!baseResultado.aprovada()) {
      throw new IllegalStateException("reconciliacao da base integral nao fechou");
    }
    ManifestoMidiaFaseCinco manifesto = consolidacao(contexto).manifestoSeguro();
    ArmazenamentosMigracaoIntegral armazenamentos = armazenamentos();
    RelatorioExecucao execucao = motor(armazenamentos, contexto).executar(
        contexto.execucaoId(),
        manifesto,
        contexto.modo() == ModoMigracaoIntegral.DRY_RUN);
    Reconciliacao midia = reconciliadorMidia.reconciliar(manifesto, execucao);
    if (!midia.aprovada()) {
      throw new IllegalStateException("reconciliacao final de midia nao fechou");
    }
    return new ResultadoEtapa(
        manifesto.itens().size(),
        midia.quarentena(),
        0,
        midia.bytesValidados(),
        Map.of(
            "baseAprovada", true,
            "midiaAprovada", true,
            "documentosCompartilhados", baseResultado.documentosCompartilhadosEntreUsuarios(),
            "movimentosOrfaos", baseResultado.movimentosOrfaos()));
  }

  private ResultadoEtapa fingerprint(Contexto contexto) {
    String valor = fingerprint.calcular(jdbc);
    return new ResultadoEtapa(1, 0, 0, 0, Map.of("fingerprint", valor));
  }

  private SnapshotAnunciosFaseUm.Snapshot snapshotFaseUm(Contexto contexto) {
    SnapshotAnunciosFaseUm.Snapshot original = contexto.pacote().faseUm();
    Map<String, UUID> usuarios = new LinkedHashMap<>(original.proprietariosV3());
    usuarios.putAll(base.usuariosMapeados(contexto.pacote().base(), contexto.execucaoId()));
    Map<String, UUID> atores = new LinkedHashMap<>(original.atoresV3());
    atores.putAll(usuarios);
    Map<String, LocalidadeMapeada> localidades = new LinkedHashMap<>(original.localidadesV3());
    localidades.putAll(localidadesAnuncio(contexto));
    ManifestoMidiaAnuncios manifesto = adaptadorMidia.paraFaseUm(
        consolidacao(contexto).manifestoSeguro());
    return new SnapshotAnunciosFaseUm.Snapshot(
        original.snapshotId(),
        original.capturadoEm(),
        original.atorSistemaV3Id(),
        usuarios,
        atores,
        localidades,
        original.anuncios(),
        manifesto,
        original.filhosOrfaos());
  }

  private Map<String, LocalidadeMapeada> localidadesAnuncio(Contexto contexto) {
    Map<String, UUID> ids = base.localidadesMapeadas(
        contexto.pacote().base(), contexto.execucaoId());
    Map<String, LocalidadeLegada> porId = contexto.pacote().base().localidades().stream()
        .collect(Collectors.toMap(LocalidadeLegada::idOrigem, Function.identity()));
    Map<String, LocalidadeMapeada> resultado = new LinkedHashMap<>();
    for (LocalidadeLegada localidade : contexto.pacote().base().localidades()) {
      if (localidade.chaveMapeamento() == null || localidade.tipo() == TipoLocalidade.ESTADO) {
        continue;
      }
      LocalidadeLegada cidade = localidade.tipo() == TipoLocalidade.CIDADE
          ? localidade
          : porId.get(localidade.paiOrigemId());
      LocalidadeLegada estado = cidade == null ? null : porId.get(cidade.paiOrigemId());
      UUID estadoId = estado == null ? null : ids.get(estado.idOrigem());
      UUID cidadeId = cidade == null ? null : ids.get(cidade.idOrigem());
      UUID bairroId = localidade.tipo() == TipoLocalidade.BAIRRO
          ? ids.get(localidade.idOrigem())
          : null;
      resultado.put(
          localidade.chaveMapeamento(),
          new LocalidadeMapeada(estadoId, cidadeId, bairroId, estadoId != null && cidadeId != null));
    }
    return resultado;
  }

  private SnapshotConteudoSeoFaseDois.Snapshot snapshotFaseDois(
      Contexto contexto,
      boolean somenteAnuncios) {
    SnapshotConteudoSeoFaseDois.Snapshot original = contexto.pacote().faseDois();
    List<ReferenciaAnuncioSeo> anuncios = somenteAnuncios
        ? anunciosSeoMapeados(original.anunciosSeo())
        : List.of();
    String sufixo = somenteAnuncios ? ":anuncios" : ":conteudo";
    return new SnapshotConteudoSeoFaseDois.Snapshot(
        original.snapshotId() + sufixo,
        original.capturadoEm(),
        original.atorSistemaV3Id(),
        original.atoresV3(),
        somenteAnuncios ? List.of() : original.faqs(),
        somenteAnuncios ? List.of() : original.avisos(),
        somenteAnuncios ? List.of() : original.categoriasBlog(),
        somenteAnuncios ? List.of() : original.postsBlog(),
        somenteAnuncios ? List.of() : original.conteudosInstitucionais(),
        somenteAnuncios ? List.of() : original.localidadesSeo(),
        anuncios,
        somenteAnuncios ? List.of() : original.redirects());
  }

  private List<ReferenciaAnuncioSeo> anunciosSeoMapeados(List<ReferenciaAnuncioSeo> referencias) {
    List<ReferenciaAnuncioSeo> resultado = new ArrayList<>();
    for (ReferenciaAnuncioSeo referencia : referencias) {
      UUID anuncioId = base.destinoMapeadoQualquerExecucao("anuncios", referencia.idOrigem());
      if (anuncioId == null) {
        continue;
      }
      boolean indexavel = Boolean.TRUE.equals(jdbc.queryForObject(
          """
          SELECT a.status = 'PUBLICADO'
             AND a.status_moderacao = 'APROVADO'
             AND EXISTS (
               SELECT 1 FROM anuncio_midia am
               JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
               WHERE am.anuncio_id = a.id
                 AND am.status = 'PUBLICAVEL'
                 AND ar.status_arquivo = 'VALIDADO'
             )
          FROM anuncio a WHERE a.id = ?
          """,
          Boolean.class,
          anuncioId));
      resultado.add(new ReferenciaAnuncioSeo(
          referencia.idOrigem(), anuncioId, referencia.slug(), indexavel, referencia.atualizadoEm()));
    }
    return List.copyOf(resultado);
  }

  private SnapshotFinanceiroFaseQuatro.Snapshot snapshotFaseQuatro(Contexto contexto) {
    SnapshotFinanceiroFaseQuatro.Snapshot original = contexto.pacote().faseQuatro();
    Map<String, UUID> usuarios = new LinkedHashMap<>(original.usuariosV3());
    usuarios.putAll(base.usuariosMapeados(contexto.pacote().base(), contexto.execucaoId()));
    Map<String, UUID> anuncios = new LinkedHashMap<>(original.anunciosV3());
    original.gruposAtivacao().stream()
        .map(SnapshotFinanceiroFaseQuatro.GrupoAtivacaoLegado::anuncioOrigemId)
        .filter(Objects::nonNull)
        .distinct()
        .forEach(id -> {
          UUID destino = base.destinoMapeadoQualquerExecucao("anuncios", id);
          if (destino != null) {
            anuncios.put(id, destino);
          }
        });
    Map<String, UUID> planos = new LinkedHashMap<>(original.planosCreditoV3());
    original.pagamentos().stream()
        .map(SnapshotFinanceiroFaseQuatro.PagamentoLegado::planoOrigemId)
        .filter(Objects::nonNull)
        .distinct()
        .forEach(id -> {
          UUID destino = base.destinoMapeadoQualquerExecucao("planos_credito", id);
          if (destino != null) {
            planos.put(id, destino);
          }
        });
    return new SnapshotFinanceiroFaseQuatro.Snapshot(
        original.snapshotId(),
        original.capturadoEm(),
        original.atorSistemaV3Id(),
        usuarios,
        anuncios,
        planos,
        original.pagamentos(),
        original.gruposAtivacao(),
        original.carteiras());
  }

  private Consolidacao consolidacao(Contexto contexto) {
    return politicaKyc.consolidar(
        contexto.pacote().base().documentosKyc(), contexto.pacote().faseCinco());
  }

  private MotorCopiaMidiaFaseCinco motor(
      ArmazenamentosMigracaoIntegral armazenamentos,
      Contexto contexto) {
    CheckpointMidiaMigracaoJdbc checkpoint = new CheckpointMidiaMigracaoJdbc(
        jdbc,
        new TransactionTemplate(transactionManager),
        mapper);
    return new MotorCopiaMidiaFaseCinco(
        armazenamentos.fonte(),
        armazenamentos.destino(),
        checkpoint,
        new MotorCopiaMidiaFaseCinco.Config(
            Math.min(4, Math.max(1, contexto.tamanhoLote())),
            3,
            Duration.ofMillis(100),
            Duration.ofHours(2)));
  }

  private ArmazenamentosMigracaoIntegral armazenamentos() {
    ArmazenamentosMigracaoIntegral armazenamentos = armazenamentosProvider.getIfAvailable();
    if (armazenamentos == null) {
      throw new IllegalStateException("storage da migracao integral nao foi configurado");
    }
    return armazenamentos;
  }

  private void validarBuckets(
      ManifestoMidiaFaseCinco manifesto,
      ArmazenamentosMigracaoIntegral armazenamentos) {
    boolean divergente = manifesto.itens().stream()
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .anyMatch(item -> !item.destino().bucket().equals(
            armazenamentos.bucketDestino(item.destino().area())));
    if (divergente) {
      throw new IllegalStateException("manifesto aponta para bucket diferente do destino configurado");
    }
  }

  private ResultadoEtapa lotes(Supplier<ResultadoLote> operacao) {
    long processados = 0;
    long quarentenas = 0;
    long retries = 0;
    long bytes = 0;
    ResultadoLote resultado;
    do {
      resultado = operacao.get();
      processados += resultado.processados();
      quarentenas += resultado.quarentenas();
      retries += resultado.retries();
      bytes += resultado.bytes();
      interromperSeSolicitado();
    } while (resultado.restantes() > 0);
    return new ResultadoEtapa(
        processados, quarentenas, retries, bytes,
        Map.of("processados", processados, "quarentenas", quarentenas));
  }

  private ResultadoEtapa resultado(ResultadoLote lote, Map<String, Object> adicional) {
    Map<String, Object> resumo = new LinkedHashMap<>(adicional);
    resumo.put("processados", lote.processados());
    resumo.put("quarentenas", lote.quarentenas());
    return new ResultadoEtapa(
        lote.processados(), lote.quarentenas(), lote.retries(), lote.bytes(), resumo);
  }

  private void interromperSeSolicitado() {
    if (Thread.currentThread().isInterrupted()) {
      throw new OrquestradorMigracaoIntegral.ExecucaoInterrompidaException();
    }
  }
}
