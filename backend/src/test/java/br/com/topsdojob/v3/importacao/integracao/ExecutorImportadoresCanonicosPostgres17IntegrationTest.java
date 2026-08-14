package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.blog.BlogConteudoValidator;
import br.com.topsdojob.v3.importacao.anuncio.ImportadorAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.AnuncioLegado;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.LocalizacaoLegada;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm.ModeracaoLegada;
import br.com.topsdojob.v3.importacao.comercial.ImportadorConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.comercial.MatrizMapeamentoComercialImportacao;
import br.com.topsdojob.v3.importacao.comercial.SanitizadorDescricaoComercialLegada;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.BeneficioLegado;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.OpcaoBeneficioLegada;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres.PacoteCreditoLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.GeradorMetadataSeoImportacao;
import br.com.topsdojob.v3.importacao.conteudoseo.ImportadorConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.conteudoseo.PlanejadorConteudoSeoImportacao;
import br.com.topsdojob.v3.importacao.conteudoseo.PoliticaIndexacaoLocalidadeImportacao;
import br.com.topsdojob.v3.importacao.conteudoseo.SanitizadorConteudoLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.FaqLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.ReferenciaAnuncioSeo;
import br.com.topsdojob.v3.importacao.financeiro.ImportadorFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.financeiro.MatrizPagamentoHistoricoImportacao;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.AtivacaoLegada;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.CarteiraLegada;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.GrupoAtivacaoLegado;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro.PagamentoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.CredencialLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.DocumentoKycLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.FavoritoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.LocalidadeLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.MetricaAnuncioLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.OrfaoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoLocalidade;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoOrfao;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.UsuarioLegado;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
    named = "MIGRACAO_INTEGRAL_PG17_ENABLED",
    matches = "true")
class ExecutorImportadoresCanonicosPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-01T12:00:00Z");
  private static final UUID ATOR = UUID.fromString("90000000-0000-4000-8000-000000000001");
  private static final UUID OWNER = UUID.fromString("90000000-0000-4000-8000-000000000002");
  private static final byte[] FOTO_PUBLICA = new byte[] {
      (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3, 4
  };
  private static final byte[] FOTO_RESTRITA = new byte[] {
      (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe1, 5, 6, 7, 8
  };
  private static final byte[] DOCUMENTO = "%PDF-1.7\nfixture\n".getBytes(
      java.nio.charset.StandardCharsets.US_ASCII);

  @Test
  void executaImportadoresReaisComDryRunRetomadaIdempotenciaESmokeDeMidia() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-integral-real-" + sufixo + "-net";
    String container = "topsv3-integral-real-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "topsv3_migracao_integral_real";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-migracao-integral-real-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", rede);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", rede, "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=" + banco,
        "-e", "POSTGRES_USER=" + usuario,
        "-e", CREDENTIAL_ENV + "=" + credencialEfemera,
        "postgres:17-alpine");
    try {
      aguardarPostgres(container, usuario, banco, logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "validate", logs);
      int porta = portaPublicada(container, logs);
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + porta + "/" + banco,
          usuario,
          credencialEfemera);
      DataSourceTransactionManager transacoes = new DataSourceTransactionManager(dataSource);
      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      MemoriaStorage fonte = new MemoriaStorage();
      MemoriaStorage destino = new MemoriaStorage();
      semearFonte(fonte);
      ArmazenamentosMigracaoIntegral armazenamentos = new ArmazenamentosMigracaoIntegral(
          FonteMidiaMigracao.objectStorage(fonte),
          destino,
          Map.of(
              StorageArea.PUBLIC_MEDIA, "destino-publico",
              StorageArea.PRIVATE_MEDIA, "destino-privado",
              StorageArea.PRIVATE_DOCUMENT, "destino-documentos"));
      DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
      beans.registerSingleton("armazenamentosMigracaoIntegral", armazenamentos);

      ExecutorImportadoresCanonicos executor = executor(
          dataSource, transacoes, mapper, beans);
      PacoteMigracaoIntegral pacote = pacote();
      OrquestradorMigracaoIntegral dryRun = new OrquestradorMigracaoIntegral(
          dataSource, transacoes, mapper, executor);
      var relatorioDryRun = dryRun.executar(pacote, ModoMigracaoIntegral.DRY_RUN, 2);

      assertThat(relatorioDryRun.fasesConcluidas()).containsExactlyElementsOf(
          OrquestradorMigracaoIntegral.ordemCanonica());
      assertThat(contar(jdbc, "importacao_execucao")).isZero();
      assertThat(destino.totalObjetos()).isZero();

      InterromperAntesDoFinanceiro interrompivel = new InterromperAntesDoFinanceiro(executor);
      OrquestradorMigracaoIntegral primeira = new OrquestradorMigracaoIntegral(
          dataSource, transacoes, mapper, interrompivel);
      assertThatThrownBy(() -> primeira.executar(pacote, ModoMigracaoIntegral.APPLY, 2))
          .isInstanceOf(OrquestradorMigracaoIntegral.ExecucaoInterrompidaException.class);
      assertThat(destino.totalObjetos()).isEqualTo(3);

      OrquestradorMigracaoIntegral retomada = new OrquestradorMigracaoIntegral(
          dataSource, transacoes, mapper, executor);
      var concluida = retomada.executar(pacote, ModoMigracaoIntegral.APPLY, 2);
      assertThat(concluida.retomada()).isTrue();
      assertThat(concluida.status()).isEqualTo("CONCLUIDA_COM_PENDENCIAS");
      assertThat(concluida.fasesConcluidas()).containsExactlyElementsOf(
          OrquestradorMigracaoIntegral.ordemCanonica());
      assertThat(concluida.metricas().fases()).containsKeys(
          OrquestradorMigracaoIntegral.ordemCanonica().toArray(FaseMigracaoIntegral[]::new));
      assertThat(concluida.resultados()).containsKeys(
          OrquestradorMigracaoIntegral.ordemCanonica().toArray(FaseMigracaoIntegral[]::new));

      validarCatalogoESeo(jdbc);
      validarFinanceiro(jdbc);
      validarKycEOrfaos(jdbc);
      validarSmokeLocal(jdbc, destino);

      FingerprintMigracaoIntegral fingerprint = new FingerprintMigracaoIntegral(mapper);
      String antes = fingerprint.calcular(jdbc);
      String bancoSegundaExecucao = banco + "_segunda";
      command(true, logs.resolve("create-second-database.log"),
          "docker", "exec", container, "createdb", "-U", usuario, bancoSegundaExecucao);
      flyway(
          rede,
          container,
          usuario,
          bancoSegundaExecucao,
          credencialEfemera,
          "migrate",
          logs);
      flyway(
          rede,
          container,
          usuario,
          bancoSegundaExecucao,
          credencialEfemera,
          "validate",
          logs);
      DriverManagerDataSource segundaDataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + porta + "/" + bancoSegundaExecucao,
          usuario,
          credencialEfemera);
      DataSourceTransactionManager segundasTransacoes =
          new DataSourceTransactionManager(segundaDataSource);
      MemoriaStorage segundaFonte = new MemoriaStorage();
      MemoriaStorage segundoDestino = new MemoriaStorage();
      semearFonte(segundaFonte);
      DefaultListableBeanFactory segundosBeans = new DefaultListableBeanFactory();
      segundosBeans.registerSingleton(
          "armazenamentosMigracaoIntegral",
          new ArmazenamentosMigracaoIntegral(
              FonteMidiaMigracao.objectStorage(segundaFonte),
              segundoDestino,
              Map.of(
                  StorageArea.PUBLIC_MEDIA, "destino-publico",
                  StorageArea.PRIVATE_MEDIA, "destino-privado",
                  StorageArea.PRIVATE_DOCUMENT, "destino-documentos")));
      OrquestradorMigracaoIntegral segundaExecucao = new OrquestradorMigracaoIntegral(
          segundaDataSource,
          segundasTransacoes,
          mapper,
          executor(segundaDataSource, segundasTransacoes, mapper, segundosBeans));
      segundaExecucao.executar(pacote, ModoMigracaoIntegral.APPLY, 2);
      FingerprintMigracaoIntegral segundoFingerprint = new FingerprintMigracaoIntegral(mapper);
      JdbcTemplate segundoJdbc = new JdbcTemplate(segundaDataSource);
      assertThat(segundoFingerprint.calcularPorTabela(segundoJdbc))
          .isEqualTo(fingerprint.calcularPorTabela(jdbc));
      assertThat(segundoFingerprint.calcular(segundoJdbc)).isEqualTo(antes);

      String checkpointAntes = jdbc.queryForObject(
          "SELECT resumo_json::text FROM importacao_execucao WHERE id = ?",
          String.class,
          concluida.execucaoId());
      int escritasAntes = destino.escritasCondicionais();
      var idempotente = retomada.executar(pacote, ModoMigracaoIntegral.APPLY, 2);
      assertThat(idempotente.retomada()).isTrue();
      assertThat(idempotente.resultados()).containsKeys(
          OrquestradorMigracaoIntegral.ordemCanonica().toArray(FaseMigracaoIntegral[]::new));
      assertThat(fingerprint.calcular(jdbc)).isEqualTo(antes);
      assertThat(destino.escritasCondicionais()).isEqualTo(escritasAntes);
      assertThat(jdbc.queryForObject(
          "SELECT resumo_json::text FROM importacao_execucao WHERE id = ?",
          String.class,
          concluida.execucaoId()))
          .isEqualTo(checkpointAntes);

      jdbc.update("UPDATE anuncio SET titulo = 'Alteracao real de negocio' WHERE slug = ?",
          "migracao-publico");
      assertThat(fingerprint.calcular(jdbc)).isNotEqualTo(antes);
      flyway(rede, container, usuario, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "validate", logs);
    } finally {
      command(false, logs.resolve("cleanup-container.log"),
          "docker", "rm", "-f", "-v", container);
      command(false, logs.resolve("cleanup-network.log"),
          "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static ExecutorImportadoresCanonicos executor(
      DriverManagerDataSource dataSource,
      DataSourceTransactionManager transacoes,
      ObjectMapper mapper,
      DefaultListableBeanFactory beans) {
    BlogConteudoValidator blog = new BlogConteudoValidator();
    GeradorMetadataSeoImportacao metadata = new GeradorMetadataSeoImportacao();
    return new ExecutorImportadoresCanonicos(
        dataSource,
        transacoes,
        mapper,
        new ImportadorAnunciosFaseUm(dataSource, transacoes, mapper),
        new ImportadorConteudoSeoFaseDois(
            dataSource,
            transacoes,
            mapper,
            new SanitizadorConteudoLegado(blog),
            new PoliticaIndexacaoLocalidadeImportacao(),
            metadata,
            new PlanejadorConteudoSeoImportacao(metadata),
            blog),
        new ImportadorConfiguracaoComercialFaseTres(
            dataSource,
            transacoes,
            mapper,
            new MatrizMapeamentoComercialImportacao(),
            new SanitizadorDescricaoComercialLegada()),
        new ImportadorFinanceiroFaseQuatro(
            dataSource, transacoes, mapper, new MatrizPagamentoHistoricoImportacao()),
        beans.getBeanProvider(ArmazenamentosMigracaoIntegral.class));
  }

  private static PacoteMigracaoIntegral pacote() {
    return new PacoteMigracaoIntegral(
        "pacote-integral-real-sintetico",
        "v1",
        base(),
        anuncios(),
        seo(),
        comercial(),
        financeiro(),
        manifesto());
  }

  private static SnapshotBaseMigracaoIntegral.Snapshot base() {
    return new SnapshotBaseMigracaoIntegral.Snapshot(
        "base-integral-real",
        AGORA,
        List.of(
            new LocalidadeLegada(
                "estado-go", TipoLocalidade.ESTADO, null, null, "GO", "Goias",
                "goias", "goias", AGORA.minusYears(1)),
            new LocalidadeLegada(
                "cidade-qa", TipoLocalidade.CIDADE, "estado-go", "local-cidade", null,
                "Cidade QA", "cidade qa", "cidade-qa", AGORA.minusYears(1)),
            new LocalidadeLegada(
                "bairro-qa", TipoLocalidade.BAIRRO, "cidade-qa", "local-bairro", null,
                "Bairro QA", "bairro qa", "bairro-qa", AGORA.minusYears(1))),
        List.of(
            usuario("actor", ATOR, "Sistema QA", "qa-actor@example.invalid", "SISTEMA",
                Set.of("ADMIN")),
            usuario("owner", OWNER, "Anunciante QA", "qa-owner@example.invalid", "ANUNCIANTE",
                Set.of("USUARIO"))),
        List.of(new CredencialLegada(
            "credential-owner",
            "owner",
            "$2a$12$abcdefghijklmnopqrstuu012345678901234567890123456789",
            "BCRYPT",
            AGORA.minusDays(2),
            true,
            AGORA.minusDays(3))),
        List.of(new DocumentoKycLegado(
            "doc-owner",
            "envio-owner",
            "owner",
            "media-kyc",
            "IDENTIDADE",
            "UNICO",
            "VALIDADO",
            AGORA.minusDays(4),
            AGORA.minusDays(3),
            ATOR,
            AGORA.minusDays(3),
            null)),
        List.of(
            new OrfaoLegado(TipoOrfao.CARTEIRA, "wallet-orphan", 1, 2_100),
            new OrfaoLegado(TipoOrfao.HISTORICO_CREDITO, "credit-history-orphan", 3, 0),
            new OrfaoLegado(TipoOrfao.PAGAMENTO, "payments-orphan", 16, 0),
            new OrfaoLegado(TipoOrfao.SUPORTE, "support-orphan", 4, 0)),
        List.of(new FavoritoLegado(
            "favorite-public", "owner", "ad-public", AGORA.minusDays(1))),
        List.of(new MetricaAnuncioLegada(
            "views-public", "ad-public", 17, AGORA.minusHours(1))));
  }

  private static UsuarioLegado usuario(
      String origem,
      UUID id,
      String nome,
      String email,
      String tipo,
      Set<String> papeis) {
    return new UsuarioLegado(
        origem,
        id,
        nome,
        email,
        null,
        "ATIVO",
        tipo,
        null,
        null,
        null,
        AGORA.minusDays(5),
        null,
        AGORA.minusDays(10),
        AGORA.minusDays(1),
        null,
        papeis);
  }

  private static SnapshotAnunciosFaseUm.Snapshot anuncios() {
    return new SnapshotAnunciosFaseUm.Snapshot(
        "fase-1-integral-real",
        AGORA,
        ATOR,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(
            anuncio("ad-public", "migracao-publico", "Anuncio publico", true),
            anuncio("ad-restricted", "migracao-restrito", "Anuncio restrito", true),
            anuncio("ad-no-media", "migracao-sem-midia", "Anuncio sem midia", false)),
        new ManifestoMidiaAnuncios(List.of()),
        List.of());
  }

  private static AnuncioLegado anuncio(
      String id,
      String slug,
      String titulo,
      boolean publicado) {
    return new AnuncioLegado(
        id,
        "owner",
        slug,
        titulo,
        "Conteudo sintetico suficiente para validar a importacao integral do anuncio.",
        "ATIVO",
        "ACOMPANHANTE_FEMININA",
        false,
        new BigDecimal("100.00"),
        null,
        new LocalizacaoLegada("local-bairro"),
        new ModeracaoLegada(
            "APROVADA", "MOTIVO_SINTETICO", "OBSERVACAO_SINTETICA", "actor",
            AGORA.minusDays(4)),
        "ADULT_RESTRICTED",
        null,
        List.of("ORAL"),
        List.of("A_COMBINAR"),
        AGORA.minusDays(30),
        publicado ? AGORA.minusDays(20) : null,
        null,
        List.of());
  }

  private static SnapshotConteudoSeoFaseDois.Snapshot seo() {
    return new SnapshotConteudoSeoFaseDois.Snapshot(
        "fase-2-integral-real",
        AGORA,
        ATOR,
        Map.of("actor", ATOR),
        List.of(new FaqLegada(
            "faq-integral",
            "Como funciona a fotografia sintetica?",
            "Ela valida o caminho integral sem carregar dados pessoais ou chamar sistemas externos.",
            "GERAL",
            "PUBLICADO",
            true,
            1,
            AGORA.minusDays(10),
            AGORA.minusDays(1),
            AGORA.minusDays(9))),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(
            new ReferenciaAnuncioSeo(
                "ad-public", uuid("seo-public"), "migracao-publico", true, AGORA.minusDays(1)),
            new ReferenciaAnuncioSeo(
                "ad-no-media", uuid("seo-review"), "migracao-sem-midia", true,
                AGORA.minusDays(1))),
        List.of());
  }

  private static SnapshotConfiguracaoComercialFaseTres.Snapshot comercial() {
    return new SnapshotConfiguracaoComercialFaseTres.Snapshot(
        "fase-3-integral-real",
        AGORA,
        ATOR,
        List.of(new BeneficioLegado(
            "benefit-top", "ANUNCIO_TOPO", "Anuncio no topo", "Beneficio sintetico",
            "ANUNCIO", true, 1)),
        List.of(new OpcaoBeneficioLegada(
            "option-top", "benefit-top", 1, 5, null, true, 1)),
        List.of(new PacoteCreditoLegado(
            "package-50", "PACOTE_PRATA", "Pacote 50", "Pacote sintetico", 50, 0,
            new BigDecimal("10.00"), "BRL", true, 1, null)),
        List.of());
  }

  private static SnapshotFinanceiroFaseQuatro.Snapshot financeiro() {
    OffsetDateTime inicio = AGORA.minusHours(1);
    OffsetDateTime fim = AGORA.plusHours(23);
    return new SnapshotFinanceiroFaseQuatro.Snapshot(
        "fase-4-integral-real",
        AGORA,
        ATOR,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(new PagamentoLegado(
            "payment-approved",
            "owner",
            "package-50",
            "EFI",
            "APPROVED",
            "provider-synthetic",
            new BigDecimal("10.00"),
            50,
            "BRL",
            true,
            null,
            AGORA.minusDays(3),
            AGORA.minusDays(2),
            new BigDecimal("10.00"),
            50)),
        List.of(new GrupoAtivacaoLegado(
            "activation-group",
            "owner",
            "ad-public",
            "IMPORTACAO",
            null,
            "ATIVO",
            inicio,
            fim,
            List.of(new AtivacaoLegada(
                "activation-top", "ANUNCIO_TOPO", 0, null, inicio, fim)))),
        List.of(new CarteiraLegada("wallet-owner", "owner", 100, false, "ATIVO")));
  }

  private static ManifestoMidiaFaseCinco manifesto() {
    return new ManifestoMidiaFaseCinco(List.of(
        item(
            "media-public", EntidadeTipo.ANUNCIO, "ad-public", "owner",
            Finalidade.CAPA, Visibilidade.LIVRE, StorageArea.PUBLIC_MEDIA,
            "source/public.jpg", StorageArea.PUBLIC_MEDIA, "destino-publico",
            "migration/public.jpg", FOTO_PUBLICA, "image/jpeg"),
        item(
            "media-restricted", EntidadeTipo.ANUNCIO, "ad-restricted", "owner",
            Finalidade.CAPA, Visibilidade.RESTRITA_18, StorageArea.PRIVATE_MEDIA,
            "source/restricted.jpg", StorageArea.PRIVATE_MEDIA, "destino-privado",
            "migration/restricted.jpg", FOTO_RESTRITA, "image/jpeg"),
        item(
            "media-kyc", EntidadeTipo.KYC, "doc-owner", "owner",
            Finalidade.KYC_IDENTIDADE, Visibilidade.PRIVADA, StorageArea.PRIVATE_DOCUMENT,
            "source/document.pdf", StorageArea.PRIVATE_DOCUMENT, "destino-documentos",
            "migration/document.pdf", DOCUMENTO, "application/pdf")));
  }

  private static Item item(
      String id,
      EntidadeTipo entidade,
      String entidadeOrigem,
      String proprietario,
      Finalidade finalidade,
      Visibilidade visibilidade,
      StorageArea areaOrigem,
      String chaveOrigem,
      StorageArea areaDestino,
      String bucketDestino,
      String chaveDestino,
      byte[] conteudo,
      String mimeType) {
    return new Item(
        id,
        entidade,
        entidadeOrigem,
        null,
        proprietario,
        null,
        "reference-" + id,
        finalidade,
        finalidade == Finalidade.KYC_IDENTIDADE ? TipoMidia.DOCUMENTO : TipoMidia.FOTO,
        visibilidade,
        finalidade == Finalidade.KYC_IDENTIDADE
            ? EstadoModeracao.NAO_APLICAVEL
            : EstadoModeracao.APROVADA,
        true,
        finalidade == Finalidade.CAPA,
        0,
        mimeType,
        conteudo.length,
        sha256(conteudo),
        new Origem(TipoOrigem.OBJECT_STORAGE, areaOrigem, chaveOrigem),
        new Destino(areaDestino, bucketDestino, chaveDestino),
        Decisao.IMPORTAR,
        null);
  }

  private static void semearFonte(MemoriaStorage fonte) {
    fonte.put(StorageArea.PUBLIC_MEDIA, "source/public.jpg", FOTO_PUBLICA, "image/jpeg");
    fonte.put(StorageArea.PRIVATE_MEDIA, "source/restricted.jpg", FOTO_RESTRITA, "image/jpeg");
    fonte.put(
        StorageArea.PRIVATE_DOCUMENT,
        "source/document.pdf",
        DOCUMENTO,
        "application/pdf");
  }

  private static void validarCatalogoESeo(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio WHERE status = 'PUBLICADO'", Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio WHERE status = 'PENDENTE_REVISAO'", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio a JOIN anuncio_localizacao l ON l.anuncio_id = a.id "
            + "WHERE a.status = 'PUBLICADO' AND l.cidade_id IS NOT NULL",
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM seo_url WHERE tipo = 'ANUNCIO' AND indexavel = true",
        Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM seo_url WHERE tipo = 'ANUNCIO' AND indexavel = false",
        Long.class))
        .isEqualTo(1);
  }

  private static void validarFinanceiro(JdbcTemplate jdbc) {
    assertThat(contar(jdbc, "pagamento")).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM pagamento WHERE status_interno = 'APROVADO'", Long.class))
        .isEqualTo(1);
    assertThat(contar(jdbc, "grupo_ativacao_beneficio")).isEqualTo(1);
    assertThat(contar(jdbc, "ativacao_beneficio")).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM movimento_credito WHERE tipo = 'MIGRACAO_SALDO_INICIAL'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM movimento_credito "
            + "WHERE origem = 'PAGAMENTO' OR referencia_tipo = 'PAGAMENTO'",
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT saldo_depois FROM movimento_credito WHERE tipo = 'MIGRACAO_SALDO_INICIAL'",
        Integer.class)).isEqualTo(100);
  }

  private static void validarKycEOrfaos(JdbcTemplate jdbc) {
    assertThat(contar(jdbc, "documento_usuario")).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'CARTEIRA_ORFA_QUARENTENA'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT detalhe_resumido FROM importacao_pendencia "
            + "WHERE codigo = 'CARTEIRA_ORFA_QUARENTENA'",
        String.class)).contains("quantidade=1", "valorAgregado=2100");
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia "
            + "WHERE codigo = 'HISTORICO_CREDITO_ORFAO_QUARENTENA'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT detalhe_resumido FROM importacao_pendencia "
            + "WHERE codigo = 'HISTORICO_CREDITO_ORFAO_QUARENTENA'",
        String.class)).contains("quantidade=3");
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'PAGAMENTO_ORFAO_QUARENTENA'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT detalhe_resumido FROM importacao_pendencia "
            + "WHERE codigo = 'PAGAMENTO_ORFAO_QUARENTENA'",
        String.class)).contains("quantidade=16");
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'SUPORTE_ORFAO'",
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_mapeamento "
            + "WHERE tabela_origem = 'mensagens_suporte_orfas' AND status = 'REJEITADO'",
        Long.class)).isEqualTo(1);
  }

  private static void validarSmokeLocal(JdbcTemplate jdbc, MemoriaStorage destino) {
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio WHERE slug = 'migracao-publico' AND status = 'PUBLICADO'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM credencial_usuario", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM papel_usuario WHERE usuario_id = ? AND papel = 'ADMIN'",
        Long.class,
        ATOR)).isEqualTo(1);
    assertThat(contar(jdbc, "favorito_anuncio")).isEqualTo(1);
    assertThat(contar(jdbc, "agregado_visualizacao_inicial")).isEqualTo(1);
    assertThat(destino.publicUrl(StorageArea.PUBLIC_MEDIA, "migration/public.jpg")).isPresent();
    assertThat(destino.publicUrl(StorageArea.PRIVATE_MEDIA, "migration/restricted.jpg")).isEmpty();
    assertThat(destino.publicUrl(StorageArea.PRIVATE_DOCUMENT, "migration/document.pdf")).isEmpty();
  }

  private static long contar(JdbcTemplate jdbc, String tabela) {
    Long total = jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
    return total == null ? 0 : total;
  }

  private static UUID uuid(String valor) {
    return UUID.nameUUIDFromBytes(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String sha256(byte[] conteudo) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void aguardarPostgres(
      String container, String usuario, String banco, Path logs) throws Exception {
    for (int tentativa = 0; tentativa < 60; tentativa++) {
      int exit = command(false, logs.resolve("pg-ready.log"),
          "docker", "exec", container, "pg_isready", "--host", "127.0.0.1",
          "--username", usuario, "--dbname", banco);
      if (exit == 0) {
        return;
      }
      Thread.sleep(500L);
    }
    throw new IllegalStateException("PostgreSQL 17 descartavel nao ficou pronto");
  }

  private static void flyway(
      String rede,
      String container,
      String usuario,
      String banco,
      String credencial,
      String acao,
      Path logs) throws Exception {
    command(true, logs.resolve("flyway-" + acao + ".log"),
        "docker", "run", "--pull=never", "--rm", "--network", rede,
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/" + banco,
        "-user=" + usuario, FLYWAY_CREDENTIAL_OPTION + credencial,
        "-locations=filesystem:/flyway/sql", acao);
  }

  private static int portaPublicada(String container, Path logs) throws Exception {
    Path log = logs.resolve("docker-port.log");
    command(true, log, "docker", "port", container, "5432/tcp");
    Matcher matcher = Pattern.compile(".*:(\\d+)$").matcher(Files.readString(log).trim());
    if (!matcher.find()) {
      throw new IllegalStateException("porta do PostgreSQL descartavel nao encontrada");
    }
    return Integer.parseInt(matcher.group(1));
  }

  private static int command(boolean check, Path output, String... argumentos) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(argumentos);
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (check && exit != 0) {
      String detalhe = Files.readString(output).lines()
          .filter(linha -> linha.contains("ERROR:") || linha.contains("Exception"))
          .reduce((primeiro, segundo) -> segundo)
          .orElse("erro sem detalhe sanitizado");
      throw new IllegalStateException("comando descartavel falhou: " + detalhe);
    }
    return exit;
  }

  private static void deleteTree(Path raiz) throws IOException {
    if (!Files.exists(raiz)) {
      return;
    }
    try (var caminhos = Files.walk(raiz)) {
      for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(caminho);
      }
    }
  }

  private static final class InterromperAntesDoFinanceiro implements ExecutorFasesMigracaoIntegral {
    private final ExecutorFasesMigracaoIntegral delegate;
    private final AtomicBoolean interromper = new AtomicBoolean(true);

    private InterromperAntesDoFinanceiro(ExecutorFasesMigracaoIntegral delegate) {
      this.delegate = delegate;
    }

    @Override
    public ResultadoEtapa executar(FaseMigracaoIntegral fase, Contexto contexto) {
      if (fase == FaseMigracaoIntegral.FINANCEIRO && interromper.compareAndSet(true, false)) {
        throw new OrquestradorMigracaoIntegral.ExecucaoInterrompidaException();
      }
      return delegate.executar(fase, contexto);
    }
  }

  private static final class MemoriaStorage implements ObjectStorage {
    private final Map<StorageArea, Map<String, StoredObject>> objetos =
        new EnumMap<>(StorageArea.class);
    private int escritasCondicionais;

    @Override
    public synchronized void put(
        StorageArea area, String key, byte[] content, String contentType) {
      area(area).put(key, new StoredObject(content, contentType));
    }

    @Override
    public synchronized ObjectWriteResult putIfAbsent(
        StorageArea area, String key, byte[] content, String contentType) {
      Map<String, StoredObject> itens = area(area);
      if (itens.containsKey(key)) {
        return ObjectWriteResult.ALREADY_EXISTS;
      }
      itens.put(key, new StoredObject(content, contentType));
      escritasCondicionais++;
      return ObjectWriteResult.CREATED;
    }

    @Override
    public synchronized boolean exists(StorageArea area, String key) {
      return area(area).containsKey(key);
    }

    @Override
    public synchronized StoredObject get(StorageArea area, String key) {
      StoredObject objeto = area(area).get(key);
      if (objeto == null) {
        throw new IllegalStateException("objeto sintetico ausente");
      }
      return objeto;
    }

    @Override
    public synchronized void delete(StorageArea area, String key) {
      area(area).remove(key);
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      if (!exists(area, key)) {
        throw new IllegalStateException("objeto sintetico ausente");
      }
      return URI.create("https://storage.example.invalid/temporary");
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      if (area != StorageArea.PUBLIC_MEDIA || !exists(area, key)) {
        return Optional.empty();
      }
      return Optional.of(URI.create("https://storage.example.invalid/public"));
    }

    synchronized long totalObjetos() {
      return objetos.values().stream().mapToLong(Map::size).sum();
    }

    synchronized int escritasCondicionais() {
      return escritasCondicionais;
    }

    private Map<String, StoredObject> area(StorageArea area) {
      return objetos.computeIfAbsent(area, ignored -> new LinkedHashMap<>());
    }
  }
}
