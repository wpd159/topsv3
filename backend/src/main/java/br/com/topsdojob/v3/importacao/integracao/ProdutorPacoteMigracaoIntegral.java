package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.integracao.ClassificadorUsuariosStaging.IdentidadeUsuario;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.DocumentoKycLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.FavoritoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.LocalidadeLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.MetricaAnuncioLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.OrfaoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoLocalidade;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoOrfao;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.UsuarioLegado;
import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.DestinoProperties;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
public class ProdutorPacoteMigracaoIntegral {

  private static final ZoneId ZONA_LEGADO = ZoneId.of("America/Sao_Paulo");
  private static final List<String> TABELAS_OBRIGATORIAS = List.of(
      "usuarios",
      "usuarios_staging",
      "estado",
      "cidade",
      "bairro",
      "anuncios",
      "anuncio_servicos",
      "anuncio_local_atendimento",
      "faq",
      "avisos",
      "blog_categorias",
      "blog_posts",
      "site_content_entries",
      "feature_catalogo",
      "feature_catalogo_duracoes",
      "planos_credito",
      "pagamentos_mp",
      "feature_ativacao",
      "creditos_usuario",
      "historico_creditos",
      "usuario_favoritos");

  private final DataSource dataSource;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final TransactionTemplate leitura;
  private final ValidadorPacoteMigracaoIntegral validador;
  private final RepositorioPacoteMigracaoIntegral repositorio;
  private final ClassificadorUsuariosStaging classificador = new ClassificadorUsuariosStaging();
  private final DestinoProperties destinoProperties;

  public ProdutorPacoteMigracaoIntegral(
      DataSource dataSource,
      ObjectMapper mapper,
      PlatformTransactionManager transactionManager,
      DestinoProperties destinoProperties) {
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
    this.mapper = mapper;
    this.leitura = new TransactionTemplate(transactionManager);
    this.leitura.setReadOnly(true);
    this.leitura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.destinoProperties = Objects.requireNonNull(
        destinoProperties, "destinoProperties obrigatorio");
    this.validador = new ValidadorPacoteMigracaoIntegral(mapper, destinoProperties.r2());
    this.repositorio = new RepositorioPacoteMigracaoIntegral(mapper);
  }

  public ResultadoProducao produzir(Parametros parametros) {
    Objects.requireNonNull(parametros, "parametros obrigatorios");
    ArquivoPacoteMigracaoIntegral arquivo = leitura.execute(
        status -> montarPacoteEmFotografiaSomenteLeitura(parametros));
    if (arquivo == null) {
      throw new IllegalStateException("fotografia do pacote nao foi produzida");
    }
    var escrita = repositorio.gravarAtomico(
        parametros.saida(),
        arquivo,
        validador,
        parametros.origemId(),
        parametros.diretorioManifestos());
    return new ResultadoProducao(
        escrita.estado(),
        arquivo.fingerprint(),
        escrita.arquivoSha256(),
        arquivo.contagens(),
        resumoStaging(arquivo.pacote().base()));
  }

  private ArquivoPacoteMigracaoIntegral montarPacoteEmFotografiaSomenteLeitura(
      Parametros parametros) {
    validarBancoRestauradoLocal();
    validarEstruturaLegada();
    ManifestoCarregado manifesto = carregarManifesto(parametros);
    List<UsuarioLegado> usuarios = usuarios(parametros.capturadoEm());
    UUID atorSistema = atorSistema(usuarios);

    SnapshotBaseMigracaoIntegral.Snapshot base = base(
        parametros, manifesto.manifesto(), usuarios);
    SnapshotAnunciosFaseUm.Snapshot faseUm = faseUm(
        parametros, manifesto.manifesto(), usuarios);
    SnapshotConteudoSeoFaseDois.Snapshot faseDois = faseDois(
        parametros, manifesto.manifesto(), atorSistema);
    SnapshotConfiguracaoComercialFaseTres.Snapshot faseTres = faseTres(
        parametros, atorSistema);
    SnapshotFinanceiroFaseQuatro.Snapshot faseQuatro = faseQuatro(
        parametros, atorSistema, usuarios);
    PacoteMigracaoIntegral pacote = new PacoteMigracaoIntegral(
        parametros.execucaoId(),
        "1",
        base,
        faseUm,
        faseDois,
        faseTres,
        faseQuatro,
        manifesto.manifesto());

    Map<String, String> hashes = validador.calcularHashesManifestos(
        parametros.diretorioManifestos());
    return ArquivoPacoteMigracaoIntegral.criar(
        parametros.origemId(),
        parametros.snapshotSha256(),
        manifesto.relativo(),
        hashes,
        parametros.capturadoEm(),
        pacote,
        mapper);
  }

  private SnapshotBaseMigracaoIntegral.Snapshot base(
      Parametros parametros,
      ManifestoMidiaFaseCinco manifesto,
      List<UsuarioLegado> usuarios) {
    List<IdentidadeUsuario> identidades = identidades("usuarios");
    List<IdentidadeUsuario> staging = identidades("usuarios_staging");
    return new SnapshotBaseMigracaoIntegral.Snapshot(
        parametros.snapshotSha256(),
        parametros.capturadoEm(),
        localidades(parametros.capturadoEm()),
        usuarios,
        List.of(),
        documentosKyc(manifesto, parametros.capturadoEm()),
        classificador.classificar(identidades, staging, parametros.capturadoEm()),
        orfaos(),
        favoritos(),
        metricas(parametros.capturadoEm()));
  }

  private List<LocalidadeLegada> localidades(OffsetDateTime capturadoEm) {
    List<LocalidadeLegada> resultado = new ArrayList<>();
    jdbc.queryForList("SELECT id, uf, nome, nome_normalizado, slug FROM estado ORDER BY id")
        .forEach(row -> resultado.add(new LocalidadeLegada(
            "estado:" + texto(row, "id"),
            TipoLocalidade.ESTADO,
            null,
            "estado:" + texto(row, "id"),
            texto(row, "uf").toUpperCase(Locale.ROOT),
            texto(row, "nome"),
            normalizado(row, "nome_normalizado", texto(row, "nome")),
            slug(row, "slug", texto(row, "nome"), texto(row, "id")),
            capturadoEm)));
    jdbc.queryForList(
            "SELECT id, estado_id, nome, nome_normalizado, slug FROM cidade ORDER BY id")
        .forEach(row -> resultado.add(new LocalidadeLegada(
            "cidade:" + texto(row, "id"),
            TipoLocalidade.CIDADE,
            "estado:" + texto(row, "estado_id"),
            "cidade:" + texto(row, "id"),
            null,
            texto(row, "nome"),
            normalizado(row, "nome_normalizado", texto(row, "nome")),
            slug(row, "slug", texto(row, "nome"), texto(row, "id")),
            capturadoEm)));
    jdbc.queryForList(
            "SELECT id, cidade_id, nome, nome_normalizado, slug FROM bairro ORDER BY id")
        .forEach(row -> resultado.add(new LocalidadeLegada(
            "bairro:" + texto(row, "id"),
            TipoLocalidade.BAIRRO,
            "cidade:" + texto(row, "cidade_id"),
            "bairro:" + texto(row, "id"),
            null,
            texto(row, "nome"),
            normalizado(row, "nome_normalizado", texto(row, "nome")),
            slug(row, "slug", texto(row, "nome"), texto(row, "id")),
            capturadoEm)));
    return List.copyOf(resultado);
  }

  private List<UsuarioLegado> usuarios(OffsetDateTime capturadoEm) {
    Map<String, Long> usernames = jdbc.queryForList(
            "SELECT lower(btrim(username)) valor, count(*) total FROM usuarios "
                + "WHERE nullif(btrim(username), '') IS NOT NULL GROUP BY lower(btrim(username))")
        .stream()
        .collect(Collectors.toMap(
            row -> texto(row, "valor"),
            row -> numero(row, "total").longValue()));
    return jdbc.queryForList(
            "SELECT id, username, email, nome_completo, cpf, telefone, data_nascimento, "
                + "status, role, criado_em, verificado, is_verificado FROM usuarios ORDER BY id")
        .stream()
        .map(row -> {
          String id = texto(row, "id");
          String username = opcional(row.get("username"));
          String nomePublico = username == null
              || usernames.getOrDefault(username.toLowerCase(Locale.ROOT), 0L) > 1
              ? "usuario-" + id
              : username;
          OffsetDateTime criadoEm = data(row.get("criado_em"), capturadoEm);
          boolean ativo = "ATIVO".equalsIgnoreCase(opcional(row.get("status")));
          String role = maiusculo(row.get("role"), "USER");
          boolean verificado = bool(row.get("verificado")) || bool(row.get("is_verificado"));
          return new UsuarioLegado(
              id,
              IdsMigracaoIntegral.uuid("usuario", id),
              nomePublico,
              minusculo(row.get("email")),
              telefone(row.get("telefone")),
              ativo ? "ATIVO" : "DESATIVADO",
              Set.of("ADMIN", "MODERADOR").contains(role) ? "STAFF" : "ANUNCIANTE",
              opcional(row.get("nome_completo")),
              cpfValido(row.get("cpf")),
              localDate(row.get("data_nascimento")),
              verificado ? criadoEm : null,
              null,
              criadoEm,
              criadoEm,
              ativo ? null : criadoEm,
              Set.of(Set.of("ADMIN", "MODERADOR").contains(role) ? role : "USUARIO"));
        })
        .toList();
  }

  private List<IdentidadeUsuario> identidades(String tabela) {
    if (!Set.of("usuarios", "usuarios_staging").contains(tabela)) {
      throw new IllegalArgumentException("tabela de identidade fora da politica");
    }
    return jdbc.queryForList(
            "SELECT id, email, cpf, telefone, username FROM " + tabela + " ORDER BY id")
        .stream()
        .map(row -> new IdentidadeUsuario(
            texto(row, "id"),
            opcional(row.get("email")),
            opcional(row.get("cpf")),
            opcional(row.get("telefone")),
            opcional(row.get("username"))))
        .toList();
  }

  private List<DocumentoKycLegado> documentosKyc(
      ManifestoMidiaFaseCinco manifesto,
      OffsetDateTime capturadoEm) {
    return manifesto.itens().stream()
        .filter(item -> item.entidadeTipo() == EntidadeTipo.KYC)
        .filter(item -> item.proprietarioOrigemId() != null)
        .map(item -> new DocumentoKycLegado(
            item.idOrigem(),
            "envio:" + item.proprietarioOrigemId(),
            item.proprietarioOrigemId(),
            item.idOrigem(),
            item.finalidade() == Finalidade.KYC_IDADE ? "VERIFICACAO_IDADE" : "IDENTIDADE",
            parte(item),
            statusKyc(item),
            capturadoEm,
            capturadoEm,
            null,
            item.estadoModeracao() == EstadoModeracao.PENDENTE ? null : capturadoEm,
            item.motivo()))
        .toList();
  }

  private List<OrfaoLegado> orfaos() {
    List<OrfaoLegado> resultado = new ArrayList<>();
    jdbc.queryForList(
            "SELECT c.id, c.saldo FROM creditos_usuario c LEFT JOIN usuarios u "
                + "ON u.id=c.usuario_id WHERE u.id IS NULL ORDER BY c.id")
        .forEach(row -> resultado.add(new OrfaoLegado(
            TipoOrfao.CARTEIRA,
            texto(row, "id"),
            1,
            numero(row, "saldo").longValue())));
    jdbc.queryForList(
            "SELECT h.id, h.quantidade FROM historico_creditos h LEFT JOIN usuarios u "
                + "ON u.id=h.usuario_id WHERE u.id IS NULL ORDER BY h.id")
        .forEach(row -> resultado.add(new OrfaoLegado(
            TipoOrfao.HISTORICO_CREDITO,
            texto(row, "id"),
            1,
            numero(row, "quantidade").longValue())));
    jdbc.queryForList(
            "SELECT p.id, p.valor FROM pagamentos_mp p LEFT JOIN usuarios u "
                + "ON u.id=p.usuario_id WHERE u.id IS NULL ORDER BY p.id")
        .forEach(row -> resultado.add(new OrfaoLegado(
            TipoOrfao.PAGAMENTO,
            texto(row, "id"),
            1,
            centavos(row.get("valor")))));
    if (tabelaExiste("suporte_mensagens")) {
      jdbc.queryForList(
              "SELECT m.id FROM suporte_mensagens m LEFT JOIN usuarios u "
                  + "ON u.id=m.enviado_por_id WHERE u.id IS NULL ORDER BY m.id")
          .forEach(row -> resultado.add(new OrfaoLegado(
              TipoOrfao.SUPORTE,
              texto(row, "id"),
              1,
              0)));
    }
    return List.copyOf(resultado);
  }

  private List<FavoritoLegado> favoritos() {
    return jdbc.queryForList(
            "SELECT f.usuario_id, f.anuncio_id FROM usuario_favoritos f "
                + "JOIN usuarios u ON u.id=f.usuario_id JOIN anuncios a ON a.id=f.anuncio_id "
                + "ORDER BY f.usuario_id, f.anuncio_id")
        .stream()
        .map(row -> new FavoritoLegado(
            texto(row, "usuario_id") + ":" + texto(row, "anuncio_id"),
            texto(row, "usuario_id"),
            texto(row, "anuncio_id"),
            OffsetDateTime.parse("2000-01-01T00:00:00Z")))
        .toList();
  }

  private List<MetricaAnuncioLegada> metricas(OffsetDateTime capturadoEm) {
    return jdbc.queryForList("SELECT id, visualizacoes FROM anuncios ORDER BY id").stream()
        .map(row -> new MetricaAnuncioLegada(
            texto(row, "id"),
            texto(row, "id"),
            numero(row, "visualizacoes").longValue(),
            capturadoEm))
        .toList();
  }

  private SnapshotAnunciosFaseUm.Snapshot faseUm(
      Parametros parametros,
      ManifestoMidiaFaseCinco manifesto,
      List<UsuarioLegado> usuarios) {
    Map<String, List<String>> servicos = agruparValores(
        "SELECT anuncio_id, servico valor FROM anuncio_servicos ORDER BY anuncio_id, servico");
    Map<String, List<String>> locais = agruparValores(
        "SELECT anuncio_id, local_atendimento valor FROM anuncio_local_atendimento "
            + "ORDER BY anuncio_id, local_atendimento");
    Map<String, String> telefones = usuarios.stream().collect(Collectors.toMap(
        UsuarioLegado::idOrigem,
        usuario -> usuario.telefoneNormalizado() == null ? "" : usuario.telefoneNormalizado()));
    List<SnapshotAnunciosFaseUm.AnuncioLegado> anuncios = jdbc.queryForList(
            "SELECT id, usuario_id, slug, titulo, descricao, status, categoria, preco, "
                + "cidade_id, bairro_id, content_classification, criado_em, "
                + "(removido_logicamente_em IS NOT NULL OR "
                + "nullif(btrim(removido_logicamente_motivo), '') IS NOT NULL) "
                + "removido_logicamente, removido_logicamente_em, removido_logicamente_motivo "
                + "FROM anuncios ORDER BY id")
        .stream()
        .map(row -> {
          String id = texto(row, "id");
          String status = maiusculo(row.get("status"), "PENDENTE");
          String categoria = maiusculo(row.get("categoria"), "DESCONHECIDA");
          OffsetDateTime criadoEm = data(row.get("criado_em"), parametros.capturadoEm());
          boolean removido = bool(row.get("removido_logicamente"));
          String usuarioId = texto(row, "usuario_id");
          return new SnapshotAnunciosFaseUm.AnuncioLegado(
              id,
              usuarioId,
              slug(row, "slug", texto(row, "titulo"), id),
              texto(row, "titulo"),
              opcional(row.get("descricao")),
              removido ? "REMOVIDO" : status,
              categoria,
              "VENDA_DE_CONTEUDO".equals(categoria),
              decimal(row.get("preco")),
              nuloSeVazio(telefones.get(usuarioId)),
              localizacao(row),
              new SnapshotAnunciosFaseUm.ModeracaoLegada(
                  statusModeracao(status),
                  removido ? "REMOCAO_LEGADA_PRESERVADA" : null,
                  null,
                  null,
                  removido ? data(row.get("removido_logicamente_em"), criadoEm) : criadoEm),
              opcional(row.get("content_classification")),
              new SnapshotAnunciosFaseUm.BloqueioJuridicoLegado(
                  false, null, null, null, null, null),
              servicos.getOrDefault(id, List.of()),
              locais.getOrDefault(id, List.of()),
              criadoEm,
              "ATIVO".equals(status) ? criadoEm : null,
              removido ? data(row.get("removido_logicamente_em"), criadoEm) : null,
              List.of());
        })
        .toList();
    return new SnapshotAnunciosFaseUm.Snapshot(
        parametros.snapshotSha256(),
        parametros.capturadoEm(),
        atorSistema(usuarios),
        Map.of(),
        Map.of(),
        Map.of(),
        anuncios,
        new ManifestoMidiaAnuncios(List.of()),
        List.of());
  }

  private SnapshotConteudoSeoFaseDois.Snapshot faseDois(
      Parametros parametros,
      ManifestoMidiaFaseCinco manifesto,
      UUID atorSistema) {
    List<SnapshotConteudoSeoFaseDois.FaqLegada> faqs = jdbc.queryForList(
            "SELECT id, pergunta, resposta, categoria FROM faq ORDER BY id")
        .stream()
        .map(row -> new SnapshotConteudoSeoFaseDois.FaqLegada(
            texto(row, "id"),
            texto(row, "pergunta"),
            texto(row, "resposta"),
            texto(row, "categoria"),
            "PUBLICADO",
            true,
            numero(row, "id").intValue(),
            parametros.capturadoEm(),
            parametros.capturadoEm(),
            parametros.capturadoEm()))
        .toList();
    List<SnapshotConteudoSeoFaseDois.AvisoLegado> avisos = jdbc.queryForList(
            "SELECT id, titulo, descricao, local_exibicao, frequencia_exibicao, status, "
                + "permite_dispensar, ativo_de, ativo_ate, criado_por_id, criado_em, atualizado_em "
                + "FROM avisos ORDER BY id")
        .stream()
        .map(row -> new SnapshotConteudoSeoFaseDois.AvisoLegado(
            texto(row, "id"),
            texto(row, "titulo"),
            texto(row, "descricao"),
            opcional(row.get("local_exibicao")),
            opcional(row.get("frequencia_exibicao")),
            maiusculo(row.get("status"), "RASCUNHO"),
            bool(row.get("permite_dispensar")),
            dataOpcional(row.get("ativo_de")),
            dataOpcional(row.get("ativo_ate")),
            opcional(row.get("criado_por_id")),
            data(row.get("criado_em"), parametros.capturadoEm()),
            data(row.get("atualizado_em"), parametros.capturadoEm()),
            "PUBLICADO".equalsIgnoreCase(opcional(row.get("status")))
                ? data(row.get("ativo_de"), parametros.capturadoEm()) : null))
        .toList();
    List<SnapshotConteudoSeoFaseDois.BlogCategoriaLegada> categorias =
        jdbc.queryForList(
                "SELECT id, nome, slug, sort_order, ativo, created_at, updated_at "
                    + "FROM blog_categorias ORDER BY id")
            .stream()
            .map(row -> new SnapshotConteudoSeoFaseDois.BlogCategoriaLegada(
                texto(row, "id"),
                texto(row, "nome"),
                slug(row, "slug", texto(row, "nome"), texto(row, "id")),
                inteiro(row.get("sort_order")),
                bool(row.get("ativo")),
                data(row.get("created_at"), parametros.capturadoEm()),
                data(row.get("updated_at"), parametros.capturadoEm())))
            .toList();
    List<SnapshotConteudoSeoFaseDois.BlogPostLegado> posts = jdbc.queryForList(
            "SELECT id, blog_categoria_id, titulo, slug, resumo, conteudo, autor_nome, status, "
                + "seo_title, seo_description, created_by_user_id, created_at, updated_at, "
                + "published_at FROM blog_posts ORDER BY id")
        .stream()
        .filter(row -> row.get("blog_categoria_id") != null)
        .map(row -> new SnapshotConteudoSeoFaseDois.BlogPostLegado(
            texto(row, "id"),
            texto(row, "blog_categoria_id"),
            texto(row, "titulo"),
            slug(row, "slug", texto(row, "titulo"), texto(row, "id")),
            texto(row, "resumo"),
            texto(row, "conteudo"),
            opcional(row.get("autor_nome")),
            maiusculo(row.get("status"), "RASCUNHO"),
            opcional(row.get("seo_title")),
            opcional(row.get("seo_description")),
            null,
            null,
            opcional(row.get("created_by_user_id")),
            data(row.get("created_at"), parametros.capturadoEm()),
            data(row.get("updated_at"), parametros.capturadoEm()),
            dataOpcional(row.get("published_at")),
            null))
        .toList();
    List<SnapshotConteudoSeoFaseDois.ConteudoInstitucionalLegado> institucionais =
        jdbc.queryForList(
                "SELECT id, content_key, titulo, corpo, updated_by, created_at, updated_at "
                    + "FROM site_content_entries ORDER BY id")
            .stream()
            .map(row -> new SnapshotConteudoSeoFaseDois.ConteudoInstitucionalLegado(
                texto(row, "id"),
                texto(row, "content_key"),
                texto(row, "titulo"),
                texto(row, "corpo"),
                "PUBLICADO",
                opcional(row.get("updated_by")),
                data(row.get("created_at"), parametros.capturadoEm()),
                data(row.get("updated_at"), parametros.capturadoEm()),
                data(row.get("updated_at"), parametros.capturadoEm())))
            .toList();
    List<SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada> localidades =
        localidadesSeo(parametros.capturadoEm());
    Set<String> anunciosComMidia = manifesto.itens().stream()
        .filter(item -> item.decisao() == Decisao.IMPORTAR)
        .filter(item -> item.entidadeTipo() == EntidadeTipo.ANUNCIO)
        .map(ManifestoMidiaFaseCinco.Item::entidadeOrigemId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    List<SnapshotConteudoSeoFaseDois.ReferenciaAnuncioSeo> anunciosSeo =
        jdbc.queryForList("SELECT id, slug, status, criado_em FROM anuncios ORDER BY id")
            .stream()
            .map(row -> {
              String id = texto(row, "id");
              return new SnapshotConteudoSeoFaseDois.ReferenciaAnuncioSeo(
                  id,
                  IdsMigracaoIntegral.uuid("anuncio", id),
                  slug(row, "slug", "anuncio", id),
                  "ATIVO".equalsIgnoreCase(opcional(row.get("status")))
                      && anunciosComMidia.contains(id),
                  data(row.get("criado_em"), parametros.capturadoEm()));
            })
            .toList();
    return new SnapshotConteudoSeoFaseDois.Snapshot(
        parametros.snapshotSha256(),
        parametros.capturadoEm(),
        atorSistema,
        Map.of(),
        faqs,
        avisos,
        categorias,
        posts,
        institucionais,
        localidades,
        anunciosSeo,
        List.of());
  }

  private List<SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada> localidadesSeo(
      OffsetDateTime capturadoEm) {
    List<SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada> resultado = new ArrayList<>();
    jdbc.queryForList(
            "SELECT e.id, e.uf, e.nome, e.slug, count(a.id) anuncios FROM estado e "
                + "LEFT JOIN cidade c ON c.estado_id=e.id LEFT JOIN anuncios a "
                + "ON a.cidade_id=c.id AND a.status='ATIVO' GROUP BY e.id,e.uf,e.nome,e.slug "
                + "ORDER BY e.id")
        .forEach(row -> resultado.add(localidadeSeo(
            "estado:" + texto(row, "id"),
            SnapshotConteudoSeoFaseDois.TipoLocalidade.ESTADO,
            "/acompanhantes/" + texto(row, "uf").toLowerCase(Locale.ROOT),
            texto(row, "nome"),
            null,
            texto(row, "uf"),
            numero(row, "anuncios").longValue(),
            capturadoEm)));
    jdbc.queryForList(
            "SELECT c.id, c.nome, c.slug, e.uf, count(a.id) anuncios FROM cidade c "
                + "JOIN estado e ON e.id=c.estado_id LEFT JOIN anuncios a "
                + "ON a.cidade_id=c.id AND a.status='ATIVO' GROUP BY c.id,c.nome,c.slug,e.uf "
                + "ORDER BY c.id")
        .forEach(row -> resultado.add(localidadeSeo(
            "cidade:" + texto(row, "id"),
            SnapshotConteudoSeoFaseDois.TipoLocalidade.CIDADE,
            "/acompanhantes/" + texto(row, "uf").toLowerCase(Locale.ROOT)
                + "/" + slug(row, "slug", texto(row, "nome"), texto(row, "id")),
            texto(row, "nome"),
            texto(row, "nome"),
            texto(row, "uf"),
            numero(row, "anuncios").longValue(),
            capturadoEm)));
    jdbc.queryForList(
            "SELECT b.id, b.nome, b.slug, c.nome cidade, c.slug cidade_slug, e.uf, "
                + "count(a.id) anuncios FROM bairro b JOIN cidade c ON c.id=b.cidade_id "
                + "JOIN estado e ON e.id=c.estado_id LEFT JOIN anuncios a "
                + "ON a.bairro_id=b.id AND a.status='ATIVO' "
                + "GROUP BY b.id,b.nome,b.slug,c.nome,c.slug,e.uf ORDER BY b.id")
        .forEach(row -> resultado.add(localidadeSeo(
            "bairro:" + texto(row, "id"),
            SnapshotConteudoSeoFaseDois.TipoLocalidade.BAIRRO,
            "/acompanhantes/" + texto(row, "uf").toLowerCase(Locale.ROOT)
                + "/" + slug(row, "cidade_slug", texto(row, "cidade"), "cidade")
                + "/" + slug(row, "slug", texto(row, "nome"), texto(row, "id")),
            texto(row, "nome"),
            texto(row, "cidade"),
            texto(row, "uf"),
            numero(row, "anuncios").longValue(),
            capturadoEm)));
    return List.copyOf(resultado);
  }

  private SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada localidadeSeo(
      String id,
      SnapshotConteudoSeoFaseDois.TipoLocalidade tipo,
      String caminho,
      String nome,
      String cidade,
      String uf,
      long anuncios,
      OffsetDateTime capturadoEm) {
    boolean indexavel = anuncios > 0;
    return new SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada(
        id,
        tipo,
        caminho,
        nome,
        cidade,
        uf,
        true,
        true,
        true,
        indexavel,
        indexavel,
        false,
        !indexavel,
        false,
        anuncios,
        null,
        List.of(),
        capturadoEm);
  }

  private SnapshotConfiguracaoComercialFaseTres.Snapshot faseTres(
      Parametros parametros,
      UUID atorSistema) {
    List<SnapshotConfiguracaoComercialFaseTres.BeneficioLegado> beneficios =
        jdbc.queryForList(
                "SELECT id, codigo, nome, descricao, escopo, ativo FROM feature_catalogo ORDER BY id")
            .stream()
            .map(row -> new SnapshotConfiguracaoComercialFaseTres.BeneficioLegado(
                texto(row, "id"),
                texto(row, "codigo"),
                opcional(row.get("nome")),
                opcional(row.get("descricao")),
                opcional(row.get("escopo")),
                bool(row.get("ativo")),
                numero(row, "id").intValue()))
            .toList();
    List<SnapshotConfiguracaoComercialFaseTres.OpcaoBeneficioLegada> opcoes =
        jdbc.queryForList(
                "SELECT d.id, d.feature_catalogo_id, d.dias, d.custo_creditos, d.ativo, "
                    + "d.sort_order FROM feature_catalogo_duracoes d JOIN feature_catalogo f "
                    + "ON f.id=d.feature_catalogo_id WHERE f.codigo <> 'STORIES' ORDER BY d.id")
            .stream()
            .map(row -> new SnapshotConfiguracaoComercialFaseTres.OpcaoBeneficioLegada(
                texto(row, "id"),
                texto(row, "feature_catalogo_id"),
                inteiro(row.get("dias")),
                inteiro(row.get("custo_creditos")),
                null,
                bool(row.get("ativo")),
                inteiro(row.get("sort_order"))))
            .toList();
    List<SnapshotConfiguracaoComercialFaseTres.PacoteCreditoLegado> pacotes =
        jdbc.queryForList(
                "SELECT id, nome, descricao, creditos, valor, ativo FROM planos_credito ORDER BY id")
            .stream()
            .map(row -> new SnapshotConfiguracaoComercialFaseTres.PacoteCreditoLegado(
                texto(row, "id"),
                codigoPacote(inteiro(row.get("creditos"))),
                opcional(row.get("nome")),
                opcional(row.get("descricao")),
                inteiro(row.get("creditos")),
                0,
                decimal(row.get("valor")),
                "BRL",
                bool(row.get("ativo")),
                numero(row, "id").intValue() * 10,
                null))
            .toList();
    List<SnapshotConfiguracaoComercialFaseTres.ConfiguracaoStoryLegada> stories =
        jdbc.queryForList(
                "SELECT id, nome, descricao, custo_creditos, ativo, duracao_horas "
                    + "FROM feature_catalogo WHERE codigo='STORIES' ORDER BY id")
            .stream()
            .map(row -> new SnapshotConfiguracaoComercialFaseTres.ConfiguracaoStoryLegada(
                texto(row, "id"),
                opcional(row.get("nome")),
                opcional(row.get("descricao")),
                inteiro(row.get("custo_creditos")),
                bool(row.get("ativo")),
                numero(row, "id").intValue(),
                inteiro(row.get("duracao_horas")),
                List.of("ANUNCIO", "MIDIA_UPLOAD")))
            .toList();
    return new SnapshotConfiguracaoComercialFaseTres.Snapshot(
        parametros.snapshotSha256(),
        parametros.capturadoEm(),
        atorSistema,
        beneficios,
        opcoes,
        pacotes,
        stories);
  }

  private SnapshotFinanceiroFaseQuatro.Snapshot faseQuatro(
      Parametros parametros,
      UUID atorSistema,
      List<UsuarioLegado> usuarios) {
    Set<String> usuariosExistentes = usuarios.stream()
        .map(UsuarioLegado::idOrigem)
        .collect(Collectors.toSet());
    Set<String> anunciosExistentes = jdbc.queryForList("SELECT id FROM anuncios", String.class)
        .stream().collect(Collectors.toSet());
    List<SnapshotFinanceiroFaseQuatro.PagamentoLegado> pagamentos =
        jdbc.queryForList(
                "SELECT id, usuario_id, plano_id, provider, metodo_pagamento, status, "
                    + "status_efetivo, provider_payment_id, mp_payment_id, txid, valor, creditos, "
                    + "creditado, expiracao, criado_em, atualizado_em FROM pagamentos_mp ORDER BY id")
            .stream()
            .filter(row -> usuariosExistentes.contains(texto(row, "usuario_id")))
            .map(row -> new SnapshotFinanceiroFaseQuatro.PagamentoLegado(
                texto(row, "id"),
                texto(row, "usuario_id"),
                opcional(row.get("plano_id")),
                primeiro(
                    opcional(row.get("provider")),
                    opcional(row.get("metodo_pagamento")),
                    "MERCADO_PAGO"),
                primeiro(
                    opcional(row.get("status_efetivo")),
                    opcional(row.get("status")),
                    "CREATED"),
                identificadorProvedor(row),
                decimal(row.get("valor")),
                inteiro(row.get("creditos")),
                "BRL",
                bool(row.get("creditado")),
                dataOpcional(row.get("expiracao")),
                dataOpcional(row.get("criado_em")),
                dataOpcional(row.get("atualizado_em")),
                decimal(row.get("valor")),
                inteiro(row.get("creditos"))))
            .toList();
    List<SnapshotFinanceiroFaseQuatro.GrupoAtivacaoLegado> grupos =
        jdbc.queryForList(
                "SELECT id, usuario_id, anuncio_id, codigo, creditos_cobrados, status, "
                    + "ativado_em, expira_em FROM feature_ativacao ORDER BY id")
            .stream()
            .filter(row -> usuariosExistentes.contains(texto(row, "usuario_id")))
            .filter(row -> row.get("anuncio_id") == null
                || anunciosExistentes.contains(texto(row, "anuncio_id")))
            .map(row -> {
              String id = texto(row, "id");
              OffsetDateTime inicio = data(row.get("ativado_em"), parametros.capturadoEm());
              OffsetDateTime fim = dataOpcional(row.get("expira_em"));
              return new SnapshotFinanceiroFaseQuatro.GrupoAtivacaoLegado(
                  id,
                  texto(row, "usuario_id"),
                  opcional(row.get("anuncio_id")),
                  "IMPORTACAO",
                  null,
                  maiusculo(row.get("status"), "CANCELADO"),
                  inicio,
                  fim,
                  List.of(new SnapshotFinanceiroFaseQuatro.AtivacaoLegada(
                      id,
                      texto(row, "codigo"),
                      inteiro(row.get("creditos_cobrados")),
                      null,
                      inicio,
                      fim)));
            })
            .toList();
    List<SnapshotFinanceiroFaseQuatro.CarteiraLegada> carteiras =
        jdbc.queryForList(
                "SELECT c.id, c.usuario_id, c.saldo, u.status FROM creditos_usuario c "
                    + "JOIN usuarios u ON u.id=c.usuario_id ORDER BY c.id")
            .stream()
            .map(row -> new SnapshotFinanceiroFaseQuatro.CarteiraLegada(
                texto(row, "id"),
                texto(row, "usuario_id"),
                numero(row, "saldo").intValue(),
                false,
                opcional(row.get("status"))))
            .toList();
    return new SnapshotFinanceiroFaseQuatro.Snapshot(
        parametros.snapshotSha256(),
        parametros.capturadoEm(),
        atorSistema,
        Map.of(),
        Map.of(),
        Map.of(),
        pagamentos,
        grupos,
        carteiras);
  }

  private ManifestoCarregado carregarManifesto(Parametros parametros) {
    Path diretorio = parametros.diretorioManifestos().toAbsolutePath().normalize();
    Path arquivo = parametros.manifestoFaseCinco().toAbsolutePath().normalize();
    if (!arquivo.startsWith(diretorio)
        || !Files.isRegularFile(arquivo, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(arquivo)) {
      throw new IllegalArgumentException("manifest da Fase 5 fora do diretorio autorizado");
    }
    String relativo = diretorio.relativize(arquivo).toString().replace('\\', '/');
    ArquivoManifestoMidiaFaseCinco artefato =
        new RepositorioManifestoMidiaFaseCinco(mapper).carregar(arquivo);
    new ValidadorManifestoMidiaFaseCinco(mapper, destinoProperties.r2()).validar(
        artefato, parametros.origemId(), parametros.snapshotSha256());
    return new ManifestoCarregado(relativo, artefato.manifesto());
  }

  private void validarBancoRestauradoLocal() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new IllegalStateException("produtor exige transacao local somente leitura");
    }
    try (Connection connection = dataSource.getConnection()) {
      String url = connection.getMetaData().getURL();
      if (!jdbcLoopback(url)) {
        throw new IllegalStateException(
            "produtor aceita somente PostgreSQL restaurado em loopback");
      }
      int versao = Integer.parseInt(jdbc.queryForObject(
          "SELECT current_setting('server_version_num')", String.class));
      if (versao < 170000 || versao >= 180000) {
        throw new IllegalStateException("produtor exige PostgreSQL 17");
      }
      Boolean somenteLeitura = jdbc.queryForObject(
          "SELECT current_setting('transaction_read_only') = 'on'",
          Boolean.class);
      if (!Boolean.TRUE.equals(somenteLeitura)) {
        throw new IllegalStateException("fotografia PostgreSQL nao esta somente leitura");
      }
    } catch (Exception exception) {
      if (exception instanceof IllegalStateException estado) {
        throw estado;
      }
      throw new IllegalStateException("banco restaurado local nao pode ser validado", exception);
    }
  }

  private void validarEstruturaLegada() {
    List<String> ausentes = TABELAS_OBRIGATORIAS.stream()
        .filter(tabela -> !tabelaExiste(tabela))
        .toList();
    if (!ausentes.isEmpty()) {
      throw new IllegalStateException(
          "snapshot restaurado esta incompleto; tabelas obrigatorias ausentes: "
              + String.join(",", ausentes));
    }
  }

  private boolean tabelaExiste(String tabela) {
    Boolean existe = jdbc.queryForObject(
        "SELECT to_regclass('public.' || ?) IS NOT NULL",
        Boolean.class,
        tabela);
    return Boolean.TRUE.equals(existe);
  }

  private Map<String, Long> resumoStaging(SnapshotBaseMigracaoIntegral.Snapshot base) {
    Map<String, Long> resumo = new LinkedHashMap<>();
    for (var classificacao : SnapshotBaseMigracaoIntegral.ClassificacaoUsuarioStaging.values()) {
      resumo.put(
          classificacao.name(),
          base.usuariosStaging().stream()
              .filter(item -> item.classificacao() == classificacao)
              .count());
    }
    resumo.put("TOTAL", (long) base.usuariosStaging().size());
    return Map.copyOf(resumo);
  }

  private Map<String, List<String>> agruparValores(String sql) {
    Map<String, List<String>> mutavel = new LinkedHashMap<>();
    jdbc.queryForList(sql).forEach(row -> {
      String valor = opcional(row.get("valor"));
      if (valor != null) {
        mutavel.computeIfAbsent(texto(row, "anuncio_id"), ignored -> new ArrayList<>())
            .add(valor);
      }
    });
    Map<String, List<String>> resultado = new LinkedHashMap<>();
    mutavel.forEach((chave, valores) -> resultado.put(chave, List.copyOf(valores)));
    return Map.copyOf(resultado);
  }

  private SnapshotAnunciosFaseUm.LocalizacaoLegada localizacao(Map<String, Object> row) {
    if (row.get("bairro_id") != null) {
      return new SnapshotAnunciosFaseUm.LocalizacaoLegada(
          "bairro:" + texto(row, "bairro_id"));
    }
    if (row.get("cidade_id") != null) {
      return new SnapshotAnunciosFaseUm.LocalizacaoLegada(
          "cidade:" + texto(row, "cidade_id"));
    }
    return null;
  }

  private static String statusModeracao(String status) {
    return switch (status) {
      case "ATIVO", "APROVADO", "PUBLICADO", "PAUSADO" -> "APROVADO";
      case "REJEITADO" -> "REJEITADO";
      default -> "PENDENTE";
    };
  }

  private static String statusKyc(ManifestoMidiaFaseCinco.Item item) {
    return switch (item.estadoModeracao()) {
      case APROVADA -> "VALIDADO";
      case REJEITADA -> "REJEITADO";
      case PENDENTE, NAO_APLICAVEL -> "PENDENTE";
    };
  }

  private static String parte(ManifestoMidiaFaseCinco.Item item) {
    String valor = (item.referenciaOrigemId() + " " + item.idOrigem())
        .toLowerCase(Locale.ROOT);
    if (valor.contains("verso")) {
      return "VERSO";
    }
    if (valor.contains("frente")) {
      return "FRENTE";
    }
    return "UNICO";
  }

  private static String codigoPacote(Integer creditos) {
    if (creditos == null) {
      return "PACOTE_DESCONHECIDO";
    }
    return switch (creditos) {
      case 50 -> "PACOTE_PRATA";
      case 150 -> "PACOTE_OURO";
      case 400 -> "PACOTE_DIAMANTE";
      default -> "PACOTE_LEGADO_" + creditos;
    };
  }

  private static String identificadorProvedor(Map<String, Object> row) {
    String bruto = primeiro(
        opcional(row.get("provider_payment_id")),
        opcional(row.get("mp_payment_id")),
        opcional(row.get("txid")),
        texto(row, "id"));
    return "legado-" + FingerprintMigracaoIntegral.sha256(
        bruto.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 24);
  }

  private static UUID atorSistema(List<UsuarioLegado> usuarios) {
    return usuarios.stream()
        .filter(usuario -> usuario.papeis().contains("ADMIN"))
        .map(UsuarioLegado::idV3)
        .sorted()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "snapshot nao possui ator administrativo deterministico"));
  }

  private static boolean jdbcLoopback(String url) {
    if (url == null || !url.startsWith("jdbc:postgresql://")) {
      return false;
    }
    try {
      URI uri = URI.create(url.substring("jdbc:".length()));
      return Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost());
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static String slug(
      Map<String, Object> row,
      String coluna,
      String fallback,
      String sufixo) {
    String existente = opcional(row.get(coluna));
    if (existente != null) {
      return existente;
    }
    String base = Normalizer.normalize(fallback, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
    return (base.isBlank() ? "item" : base) + "-" + sufixo;
  }

  private static String normalizado(
      Map<String, Object> row,
      String coluna,
      String fallback) {
    String valor = opcional(row.get(coluna));
    if (valor != null) {
      return valor.toLowerCase(Locale.ROOT);
    }
    return Normalizer.normalize(fallback, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT);
  }

  private static OffsetDateTime data(Object valor, OffsetDateTime fallback) {
    OffsetDateTime convertido = dataOpcional(valor);
    return convertido == null ? fallback : convertido;
  }

  private static OffsetDateTime dataOpcional(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime offset) {
      return offset;
    }
    if (valor instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime().atZone(ZONA_LEGADO).toOffsetDateTime();
    }
    if (valor instanceof LocalDateTime local) {
      return local.atZone(ZONA_LEGADO).toOffsetDateTime();
    }
    return OffsetDateTime.parse(valor.toString());
  }

  private static LocalDate localDate(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof LocalDate date) {
      return date;
    }
    if (valor instanceof java.sql.Date date) {
      return date.toLocalDate();
    }
    return LocalDate.parse(valor.toString());
  }

  private static String cpfValido(Object valor) {
    String cpf = digitos(valor);
    if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
      return null;
    }
    int primeiro = digitoCpf(cpf, 9, 10);
    int segundo = digitoCpf(cpf, 10, 11);
    return primeiro == Character.digit(cpf.charAt(9), 10)
        && segundo == Character.digit(cpf.charAt(10), 10) ? cpf : null;
  }

  private static int digitoCpf(String cpf, int quantidade, int pesoInicial) {
    int soma = 0;
    for (int indice = 0; indice < quantidade; indice++) {
      soma += Character.digit(cpf.charAt(indice), 10) * (pesoInicial - indice);
    }
    int resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  }

  private static String telefone(Object valor) {
    String digitos = digitos(valor);
    if (digitos == null) {
      return null;
    }
    if (digitos.length() == 10 || digitos.length() == 11) {
      return "+55" + digitos;
    }
    return digitos.length() >= 8 && digitos.length() <= 15 ? "+" + digitos : null;
  }

  private static String digitos(Object valor) {
    String texto = opcional(valor);
    if (texto == null) {
      return null;
    }
    String resultado = texto.replaceAll("\\D", "");
    return resultado.isBlank() ? null : resultado;
  }

  private static String minusculo(Object valor) {
    String texto = opcional(valor);
    return texto == null ? null : texto.toLowerCase(Locale.ROOT);
  }

  private static String maiusculo(Object valor, String fallback) {
    String texto = opcional(valor);
    return texto == null ? fallback : texto.toUpperCase(Locale.ROOT);
  }

  private static String texto(Map<String, Object> row, String coluna) {
    Object valor = row.get(coluna);
    if (valor == null || valor.toString().isBlank()) {
      throw new IllegalArgumentException("campo legado obrigatorio ausente: " + coluna);
    }
    return valor.toString().trim();
  }

  private static String opcional(Object valor) {
    return valor == null || valor.toString().isBlank() ? null : valor.toString().trim();
  }

  private static String nuloSeVazio(String valor) {
    return valor == null || valor.isBlank() ? null : valor;
  }

  private static Number numero(Map<String, Object> row, String coluna) {
    Object valor = row.get(coluna);
    return valor instanceof Number number ? number : new BigDecimal(valor.toString());
  }

  private static Integer inteiro(Object valor) {
    return valor == null ? null : valor instanceof Number number
        ? number.intValue() : Integer.valueOf(valor.toString());
  }

  private static BigDecimal decimal(Object valor) {
    return valor == null ? null : valor instanceof BigDecimal decimal
        ? decimal : new BigDecimal(valor.toString());
  }

  private static long centavos(Object valor) {
    BigDecimal decimal = decimal(valor);
    return decimal == null ? 0 : decimal.movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static boolean bool(Object valor) {
    if (valor instanceof Boolean booleano) {
      return booleano;
    }
    return valor != null && Set.of("true", "t", "1", "sim")
        .contains(valor.toString().toLowerCase(Locale.ROOT));
  }

  private static String primeiro(String... valores) {
    for (String valor : valores) {
      if (valor != null && !valor.isBlank()) {
        return valor;
      }
    }
    return null;
  }

  public record Parametros(
      String execucaoId,
      String origemId,
      String snapshotSha256,
      OffsetDateTime capturadoEm,
      Path diretorioManifestos,
      Path manifestoFaseCinco,
      Path saida) {

    public Parametros {
      execucaoId = obrigatorio(execucaoId, "execucaoId");
      origemId = obrigatorio(origemId, "origemId");
      snapshotSha256 = obrigatorio(snapshotSha256, "snapshotSha256")
          .toLowerCase(Locale.ROOT);
      if (!snapshotSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("snapshotSha256 invalido");
      }
      if (capturadoEm == null
          || diretorioManifestos == null
          || manifestoFaseCinco == null
          || saida == null) {
        throw new IllegalArgumentException("parametros de arquivo e captura sao obrigatorios");
      }
      Path diretorioNormalizado = diretorioManifestos.toAbsolutePath().normalize();
      if (saida.toAbsolutePath().normalize().startsWith(diretorioNormalizado)) {
        throw new IllegalArgumentException(
            "pacote nao pode pertencer ao diretorio de manifests");
      }
    }
  }

  public record ResultadoProducao(
      RepositorioPacoteMigracaoIntegral.EstadoEscrita estado,
      String fingerprint,
      String arquivoSha256,
      Map<String, Long> contagens,
      Map<String, Long> usuariosStaging) {
  }

  private record ManifestoCarregado(String relativo, ManifestoMidiaFaseCinco manifesto) {
  }

  private static String obrigatorio(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalArgumentException(campo + " deve ser informado");
    }
    return valor.trim();
  }
}
