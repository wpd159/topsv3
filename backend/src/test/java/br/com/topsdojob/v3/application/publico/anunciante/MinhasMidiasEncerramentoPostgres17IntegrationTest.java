package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoAcaoService;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioJuridicoService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminBloqueioJuridicoRequest;
import br.com.topsdojob.v3.application.anuncio.FotoElegivelAnuncioPolicy;
import br.com.topsdojob.v3.application.publico.service.SolicitarAnuncioPublicoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiasResponseDto;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.application.wizard.WizardProgressDtos.SyncRequest;
import br.com.topsdojob.v3.application.wizard.WizardProgressSyncService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.CategoriaBloqueioJuridico;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "app.env=homologacao", "app.event.hash-salt=hash-fixture",
        "app.age-gate.signing-value=age-gate-runtime-test-value", "app.outbox.email.enabled=false",
        "app.storage.r2.enabled=true", "app.storage.r2.private-media-bucket=privadas",
        "app.storage.r2.public-media-bucket=publicas", "app.storage.r2.private-media-prefix=hml/midias-pendentes/",
        "app.storage.r2.public-media-prefix=hml/midias-aprovadas/", "efi.pix.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false", "spring.datasource.hikari.maximum-pool-size=8"
})
@EnabledIfEnvironmentVariable(named = "FOTO_ELEGIVEL_POSTGRES17_ENABLED", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = MinhasMidiasEncerramentoPostgres17IntegrationTest.ContextCapture.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class MinhasMidiasEncerramentoPostgres17IntegrationTest {

    private static final UltimaFotoPostgres17Fixture POSTGRES = UltimaFotoPostgres17Fixture.start();
    private static TestContext testContext;
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(900000000);

    @Autowired private MinhasMidiasService midias;
    @Autowired private MeuAnuncioAtualizacaoService edicao;
    @SpyBean private MeuAnuncioCicloVidaService ciclo;
    @Autowired private MeusAnunciosConsultaService consulta;
    @Autowired private WizardProgressSyncService progresso;
    @Autowired private AdminModeracaoAcaoService moderacao;
    @Autowired private AdminAnuncioJuridicoService juridico;
    @Autowired private SolicitarAnuncioPublicoService criacao;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private AnuncioRepository anuncios;
    @Autowired private AnuncioMidiaRepository vinculos;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private ObjectStorage storage;
    @MockBean private ObjectStorageInventory previewInventory;
    @MockBean private R2VerificacaoAgrupadaPreviews verificacaoRemota;
    @MockBean private KycPublicoService kyc;
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::username);
        registry.add("spring.datasource.password", POSTGRES::credential);
    }

    public static final class ContextCapture extends AbstractTestExecutionListener {
        @Override public void beforeTestClass(TestContext context) { testContext = context; }
    }

    @AfterAll
    static void fecharContextoAntesDoPostgres() throws Exception {
        if (testContext == null) throw new IllegalStateException("contexto da fixture nao foi capturado");
        // Invalidar o cache Spring existente evita reabrir contexto durante a finalizacao.
        testContext.markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
        POSTGRES.close();
        POSTGRES.close();
    }

    @BeforeEach
    void storageSintetico() {
        objects.clear();
        when(storage.putIfAbsent(any(), anyString(), any(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0) + ":" + invocation.getArgument(1);
            StoredObject object = new StoredObject(invocation.getArgument(2), invocation.getArgument(3));
            return objects.putIfAbsent(key, object) == null ? ObjectWriteResult.CREATED : ObjectWriteResult.ALREADY_EXISTS;
        });
        when(storage.exists(any(), anyString())).thenAnswer(invocation ->
                objects.containsKey(invocation.getArgument(0) + ":" + invocation.getArgument(1)));
        when(storage.get(any(), anyString())).thenAnswer(invocation ->
                objects.get(invocation.getArgument(0) + ":" + invocation.getArgument(1)));
        when(storage.temporaryGetUrl(any(), anyString(), any())).thenReturn(URI.create("https://media.example.invalid/foto"));
        when(storage.publicUrl(any(), anyString())).thenReturn(Optional.of(URI.create("https://media.example.invalid/foto")));
        when(verificacaoRemota.verificar(any())).thenReturn(Set.of());
    }

    @AfterEach
    void cicloDeVidaNaoConsultaInventarioRemoto() {
        verifyNoInteractions(previewInventory, verificacaoRemota);
    }

    @Test
    void ultimaPendenteEncerraPreservaHistoricoERecusaPatchProgressoERetomada() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        UUID revision = revision(ad);
        MeuAnuncioMidiasResponseDto response = remove(ad, photo);
        assertThat(response.anuncio().status()).isEqualTo("REMOVIDO");
        assertThat(response.fotosValidasAtivasTotal()).isZero();
        assertClosed(ad, photo);
        assertThat(value("select status from revisao_anuncio where id = ?", revision)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("select finalizado_em is not null from revisao_anuncio where id = ?",
                Boolean.class, revision)).isTrue();
        assertHttp(404, () -> edicao.atualizar(ad.slug(), request(), ad.auth()));
        assertHttp(404, () -> ciclo.reativar(ad.slug(), ad.auth(), "retomada-encerrado"));
        assertThatThrownBy(() -> progresso.sincronizar(
                new SyncRequest("focal-" + UUID.randomUUID(), "EDIT", "CONCLUIDO", "AGUARDANDO_MODERACAO", ad.id().toString()),
                ad.auth())).isInstanceOf(ResponseStatusException.class);
        assertClosed(ad, photo);
        assertThat(count("select count(*) from wizard_progresso where anuncio_id = ? and ultimo_step = 'CONCLUIDO'", ad.id())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLICADO", "APROVADO", "PAUSADO"})
    void ultimaAprovadaEncerraEstadoProtegidoESomeDasLeiturasPublicas(String status) {
        Ad ad = ad(status, "APROVADO");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        remove(ad, photo);
        assertClosed(ad, photo);
        assertThat(anuncios.findPublicosPublicadosComProprietarioAtivoPorIds(List.of(ad.id()))).isEmpty();
        assertThat(anuncios.findBySlugAndStatusAndStatusModeracaoAndPublicadoEmIsNotNullAndRemovidoEmIsNull(
                ad.slug(), StatusAnuncio.PUBLICADO, StatusModeracaoAnuncio.APROVADO)).isEmpty();
        assertHttpReason(409, "estado do anuncio impede decisao de moderacao",
                () -> moderacao.aprovarEPublicarAnuncio(ad.id(), administrator, "aprovar-encerrado"));
        assertHttp(404, () -> ciclo.reativar(ad.slug(), ad.auth(), "reativar-encerrado"));
        assertClosed(ad, photo);
    }

    @ParameterizedTest
    @CsvSource({"PENDENTE,PENDENTE,image/jpeg", "AJUSTE_SOLICITADO,PENDENTE,image/png",
            "PUBLICAVEL,VALIDADO,image/webp"})
    void outraFotoValidaPermaneceSemEncerramento(String status, String fileStatus, String mime) {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media removed = photo(ad, 0, "PENDENTE", "PENDENTE");
        Media remaining = media(ad, 1, "FOTO", "GALERIA", status, fileStatus, mime, 1024);
        MeuAnuncioMidiasResponseDto response = remove(ad, removed);
        assertThat(response.fotosValidasAtivasTotal()).isEqualTo(1);
        assertThat(response.anuncio().status()).isEqualTo("PENDENTE_REVISAO");
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).containsExactly(remaining.id());
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isZero();
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ?", ad.id())).isZero();
        if (status.equals("AJUSTE_SOLICITADO")) {
            AdminUserPrincipal administrator = admin(ad);
            kycAprovado(ad, administrator);
            assertHttpReason(409, FotoElegivelAnuncioPolicy.MENSAGEM_FOTO_AGUARDANDO_DECISAO,
                    () -> moderacao.aprovarEPublicarAnuncio(ad.id(), administrator, "ajuste-nao-aprova"));
        }
    }

    @ParameterizedTest
    @CsvSource({"VIDEO,GALERIA,PUBLICAVEL,VALIDADO,video/mp4,1024", "STORY,STORY,PUBLICAVEL,VALIDADO,image/jpeg,1024",
            "FOTO,GALERIA,REMOVIDA,VALIDADO,image/jpeg,1024", "FOTO,GALERIA,REJEITADA,VALIDADO,image/jpeg,1024",
            "FOTO,GALERIA,PENDENTE,REJEITADO,image/jpeg,1024", "FOTO,GALERIA,PENDENTE,REMOVIDO,image/jpeg,1024",
            "FOTO,GALERIA,PENDENTE,PENDENTE,application/pdf,1024", "FOTO,GALERIA,PENDENTE,PENDENTE,image/gif,1024"})
    void vinculosSemPresencaValidaNaoImpedemEncerramento(String type, String purpose, String status,
            String fileStatus, String mime, long bytes) {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        media(ad, 1, type, purpose, status, fileStatus, mime, bytes);
        remove(ad, photo);
        assertClosed(ad, photo);
    }

    @Test
    void documentoHistoricoMesmoRemovidoNaoContaComoFoto() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        Media document = photo(ad, 1, "PUBLICAVEL", "VALIDADO");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into documento_usuario(id,usuario_id,arquivo_midia_id,envio_id,parte,tipo,status,politica_retencao,
                  criado_em,atualizado_em,removido_em)
                values(?,?,?,?,'UNICO','IDENTIDADE','REMOVIDO','MANUAL',?,?,?)
                """, id, ad.owner(), document.fileId(), id, AGORA, AGORA, AGORA);
        remove(ad, photo);
        assertClosed(ad, photo);
        assertThat(value("select status from documento_usuario where id = ?", id)).isEqualTo("REMOVIDO");
        assertThat(count("select count(*) from arquivo_midia where id = ?", document.fileId())).isEqualTo(1);
    }

    @Test
    void ultimaAprovadaComOutraApenasPendenteMantemProtecaoAnterior() {
        Ad ad = ad("PUBLICADO", "APROVADO");
        Media approved = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        Media pending = photo(ad, 1, "PENDENTE", "PENDENTE");
        assertHttp(409, () -> remove(ad, approved));
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("PUBLICADO");
        assertThat(vinculos.findFotosAprovadasElegiveisIds(ad.id())).containsExactly(approved.id());
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).containsExactlyInAnyOrder(approved.id(), pending.id());
    }

    @Test
    void falhaNoEncerramentoDesfazRemocaoETodosOsEfeitosERetryNaoDuplica() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        UUID revision = revision(ad);
        IllegalStateException original = new IllegalStateException("falha sintetica apos efeitos reais de encerramento");
        // Configurar a instrumentacao no spy alvo, sem invocar fora de transacao o proxy MANDATORY.
        // A operacao sob teste continua entrando por DELETE -> lifecycle com os proxies Spring reais.
        MeuAnuncioCicloVidaService target = AopTestUtils.getUltimateTargetObject(ciclo);
        assertThat(mockingDetails(target).isSpy()).isTrue();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            entityManager.flush();
            assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("REMOVIDO");
            assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo(1);
            throw original;
        }).when(target).encerrarPorUltimaFoto(any(), any(), any(), any());
        try {
            assertThatThrownBy(() -> remove(ad, photo)).isSameAs(original);
        } finally {
            reset(target);
        }
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("PENDENTE_REVISAO");
        assertThat(value("select status from anuncio_midia where id = ?", photo.id())).isEqualTo("PENDENTE");
        assertThat(value("select status from revisao_anuncio where id = ?", revision)).isEqualTo("ABERTA");
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isZero();
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ?", ad.id())).isZero();
        remove(ad, photo);
        assertClosed(ad, photo);
        assertHttp(409, () -> remove(ad, photo));
        assertClosed(ad, photo);
    }

    @Test
    void progressoAtrasadoSemAnuncioNoPayloadNaoConcluiSessaoJaVinculadaAoEncerrado() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        String session = "focal-edit-" + UUID.randomUUID();
        progresso.sincronizar(new SyncRequest(session, "EDIT", "FOTOS", "EM_PREENCHIMENTO", ad.id().toString()), ad.auth());
        remove(ad, photo);
        assertThatThrownBy(() -> progresso.sincronizar(
                new SyncRequest(session, "EDIT", "CONCLUIDO", "AGUARDANDO_MODERACAO", null), ad.auth()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(value("select ultimo_step from wizard_progresso where anuncio_id = ?", ad.id())).isEqualTo("FOTOS");
        assertClosed(ad, photo);
    }

    @Test
    void retryDoDeleteNaoReaplicaEncerramentoSobreEstadoJuridicoPosterior() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        remove(ad, photo);
        assertHttp(409, () -> remove(ad, photo));
        assertClosed(ad, photo);
        // Estado posterior sintetico: o retry antigo nao pode desfazer uma protecao juridica.
        jdbc.update("update anuncio set status = 'BLOQUEADO' where id = ?", ad.id());
        assertHttp(409, () -> remove(ad, photo));
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("BLOQUEADO");
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ? and acao = 'ANUNCIO_ENCERRADO_SEM_FOTOS'", ad.id())).isEqualTo(1);
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo(1);
    }

    @Test
    void outroProprietarioNaoRemoveNemEncerra() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        Authentication other = auth(user());
        assertHttp(403, () -> midias.remover(ad.slug(), photo.id(), other, "owner-incorreto"));
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("PENDENTE_REVISAO");
        assertThat(value("select status from anuncio_midia where id = ?", photo.id())).isEqualTo("PENDENTE");
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isZero();
    }

    @Test
    void estadoJuridicamenteBloqueadoNaoGanhaRetomadaPorDelete() {
        Ad ad = ad("BLOQUEADO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        assertThatThrownBy(() -> remove(ad, photo)).isInstanceOf(ResponseStatusException.class);
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("BLOQUEADO");
        assertThat(value("select status from anuncio_midia where id = ?", photo.id())).isEqualTo("PENDENTE");
    }

    @Test
    void rascunhoInicialVazioAceitaSalvamentoEPrimeiroUploadSemEncerramentoAutomatico() throws Exception {
        Ad ad = ad("RASCUNHO", "NAO_ENVIADO");
        edicao.atualizar(ad.slug(), request(), ad.auth());
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("PENDENTE_REVISAO");
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).isEmpty();
        MeuAnuncioMidiasResponseDto response = upload(ad);
        assertThat(response.fotosValidasAtivasTotal()).isEqualTo(1);
        assertThat(response.anuncio().status()).isEqualTo("PENDENTE_REVISAO");
    }

    @Test
    void criacaoRealPersisteAntesDoUploadEAceitaSalvamentoIntermediario() throws Exception {
        locality();
        UUID owner = user();
        Authentication authentication = auth(owner);
        var created = criacao.solicitar(objectMapper.readTree("""
                {"uf":"GO","cidade":"Goiania","titulo":"Anuncio sintetico novo",
                 "descricao":"Descricao sintetica completa para solicitar anuncio novo.",
                 "preco":100,"categoria":"ACOMPANHANTE_FEMININA","servicos":["ORAL"],
                 "atendimentoExclusivamenteVirtual":false,"aceiteTermos":true,"confirmacaoIdade":true}
                """), authentication);
        assertThat(created.criado()).isTrue();
        assertThat(created.uploadRealExecutado()).isFalse();
        Ad ad = new Ad(created.anuncioId(), owner, created.slugLocal(), authentication);
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).isEmpty();
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("PENDENTE_REVISAO");
        edicao.atualizar(ad.slug(), request(), authentication);
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).isEmpty();
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isZero();
        assertThat(upload(ad).fotosValidasAtivasTotal()).isEqualTo(1);
    }

    @Test
    void doisDeletesOrdenadosEncerramSemSeConsideraremMutuamenteRemanescentes() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media first = photo(ad, 0, "PENDENTE", "PENDENTE");
        Media second = photo(ad, 1, "PENDENTE", "PENDENTE");
        Ordered result = ordered(ad, () -> remove(ad, first), () -> remove(ad, second));
        assertThat(result.failure()).isNull();
        assertThat(((MeuAnuncioMidiasResponseDto) result.first()).anuncio().status()).isEqualTo("PENDENTE_REVISAO");
        assertThat(((MeuAnuncioMidiasResponseDto) result.second()).anuncio().status()).isEqualTo("REMOVIDO");
        assertClosed(ad, first);
        assertThat(value("select status from anuncio_midia where id = ?", second.id())).isEqualTo("REMOVIDA");
    }

    @Test
    void uploadEfetivadoAntesDoDeletePreservaSubstitutaPersistida() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media original = photo(ad, 0, "PENDENTE", "PENDENTE");
        Ordered result = ordered(ad, () -> upload(ad), () -> remove(ad, original));
        assertThat(result.failure()).isNull();
        assertThat(((MeuAnuncioMidiasResponseDto) result.second()).anuncio().status()).isEqualTo("PENDENTE_REVISAO");
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).hasSize(1).doesNotContain(original.id());
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isZero();
    }

    @Test
    void uploadPosteriorAoDeleteNaoReabreEncerrado() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        Ordered result = ordered(ad, () -> remove(ad, photo), () -> upload(ad));
        assertThat(result.failure()).isInstanceOf(ResponseStatusException.class);
        assertClosed(ad, photo);
        assertThat(objects).isEmpty();
    }

    @Test
    void patchConcorrentePosteriorAoDeleteNaoReabreNemCriaRevisao() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        Ordered result = ordered(ad, () -> remove(ad, photo), () -> edicao.atualizar(ad.slug(), request(), ad.auth()));
        assertThat(result.failure()).isInstanceOf(ResponseStatusException.class);
        assertClosed(ad, photo);
        assertThat(count("select count(*) from revisao_anuncio where anuncio_id = ? and status = 'ABERTA'", ad.id())).isZero();
    }

    @Test
    void patchAnteriorAoDeleteNaoImpedeEncerramentoSubsequente() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PENDENTE", "PENDENTE");
        Ordered result = ordered(ad, () -> edicao.atualizar(ad.slug(), request(), ad.auth()), () -> remove(ad, photo));
        assertThat(result.failure()).isNull();
        assertClosed(ad, photo);
        assertThat(count("select count(*) from revisao_anuncio where anuncio_id = ? and status = 'ABERTA'", ad.id())).isZero();
    }

    @Test
    void aprovacaoConcorrentePosteriorAoDeleteNaoPublicaEncerrado() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        Ordered result = ordered(ad, () -> remove(ad, photo),
                () -> moderacao.aprovarEPublicarAnuncio(ad.id(), administrator, "aprovar-concorrente"));
        assertThat(result.failure()).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(409);
            assertThat(exception.getReason()).isEqualTo("estado do anuncio impede decisao de moderacao");
        });
        assertClosed(ad, photo);
    }

    @Test
    void aprovacaoAnteriorAoDeleteNaoImpedeEncerramentoPosterior() throws Exception {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        Ordered result = ordered(ad,
                () -> moderacao.aprovarEPublicarAnuncio(ad.id(), administrator, "aprovar-primeiro"),
                () -> remove(ad, photo));
        assertThat(result.failure()).isNull();
        assertClosed(ad, photo);
    }

    @Test
    void reativacaoAdministrativaConcorrentePosteriorAoDeleteNaoReabreEncerrado() throws Exception {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        Ordered result = ordered(ad, () -> remove(ad, photo),
                () -> juridico.reativar(ad.id(), administrator, "reativar-admin-concorrente"));
        assertThat(result.failure()).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(409);
            assertThat(exception.getReason()).isEqualTo("anuncio nao pode ser reativado");
        });
        assertClosed(ad, photo);
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ? and acao = 'ANUNCIO_REATIVADO_ADMINISTRATIVAMENTE'", ad.id())).isZero();
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo(1);
    }

    @Test
    void reativacaoAdministrativaAnteriorAoDeleteNaoImpedeEncerramentoPosterior() throws Exception {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        List<String> mediaBefore = mediaSnapshot(ad);
        Ordered result = ordered(ad, () -> {
            Object response = juridico.reativar(ad.id(), administrator, "reativar-admin-primeiro");
            entityManager.flush();
            assertReactivated(ad, administrator, true, "reativar-admin-primeiro");
            assertThat(mediaSnapshot(ad)).isEqualTo(mediaBefore);
            return response;
        }, () -> remove(ad, photo));
        assertThat(result.failure()).isNull();
        assertClosed(ad, photo);
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ? and acao = 'ANUNCIO_REATIVADO_ADMINISTRATIVAMENTE'", ad.id())).isEqualTo(1);
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo(2);
    }

    @Test
    void reativacaoConcorrentePosteriorAoDeleteNaoReabreEncerrado() throws Exception {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        kycAprovado(ad, admin(ad));
        Ordered result = ordered(ad, () -> remove(ad, photo), () -> ciclo.reativar(ad.slug(), ad.auth(), "reativar-concorrente"));
        assertThat(result.failure()).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(404);
            assertThat(exception.getReason()).isEqualTo("anuncio nao encontrado");
        });
        assertClosed(ad, photo);
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ? and acao = 'ANUNCIO_REATIVADO_PELO_USUARIO'", ad.id())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "AJUSTE_SOLICITADO"})
    void reativacaoProprietariaComAprovadaEPendenciaPreservaClassificacao(String pendingStatus) {
        reativacaoComPendenciaPreservaClassificacao(false, pendingStatus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "AJUSTE_SOLICITADO"})
    void reativacaoAdministrativaComAprovadaEPendenciaPreservaClassificacao(String pendingStatus) {
        reativacaoComPendenciaPreservaClassificacao(true, pendingStatus);
    }

    private void reativacaoComPendenciaPreservaClassificacao(boolean administrative, String pendingStatus) {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media approved = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        Media pending = photo(ad, 1, pendingStatus, "PENDENTE");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        List<String> mediaBefore = mediaSnapshot(ad);
        String requestId = "reativar-com-" + pendingStatus.toLowerCase();

        reactivate(ad, administrator, administrative, requestId);

        assertReactivated(ad, administrator, administrative, requestId);
        assertThat(mediaSnapshot(ad)).isEqualTo(mediaBefore);
        assertThat(vinculos.findFotosAprovadasElegiveisIds(ad.id())).containsExactly(approved.id());
        assertThat(vinculos.findFotosAguardandoDecisaoIds(ad.id())).containsExactly(pending.id());
        assertThat(value("select status from anuncio_midia where id = ?", pending.id())).isEqualTo(pendingStatus);
        // Reativacao nao aprova a pendencia, nem libera o DELETE da unica aprovada remanescente.
        Map<String, Object> beforeRejectedDelete = persistedSnapshot(ad);
        assertHttpReason(409, FotoElegivelAnuncioPolicy.MENSAGEM_ULTIMA_FOTO_APROVADA,
                () -> remove(ad, approved));
        assertThat(persistedSnapshot(ad)).isEqualTo(beforeRejectedDelete);
    }

    @ParameterizedTest
    @CsvSource({"false,CAPA,LIVRE", "true,CAPA,LIVRE", "false,GALERIA,RESTRITA_18", "true,GALERIA,RESTRITA_18"})
    void reativacaoComUnicaFotoAprovadaRespeitaFinalidadeEVisibilidadeCanonicas(
            boolean administrative, String purpose, String visibility) {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media approved = media(ad, 0, "FOTO", purpose, "PUBLICAVEL", "VALIDADO", "image/jpeg", 1024);
        jdbc.update("update anuncio_midia set visibilidade_midia = ? where id = ?", visibility, approved.id());
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        List<String> mediaBefore = mediaSnapshot(ad);

        reactivate(ad, administrator, administrative, "reativar-unica-aprovada");

        assertReactivated(ad, administrator, administrative, "reativar-unica-aprovada");
        assertThat(mediaSnapshot(ad)).isEqualTo(mediaBefore);
        assertThat(vinculos.findFotosAprovadasElegiveisIds(ad.id())).containsExactly(approved.id());
    }

    @ParameterizedTest
    @CsvSource({"false,NENHUMA", "true,NENHUMA", "false,PENDENTE", "true,PENDENTE",
            "false,AJUSTE_SOLICITADO", "true,AJUSTE_SOLICITADO"})
    void reativacaoSemAprovadaRecusaSemEfeitos(boolean administrative, String mediaStatus) {
        Ad ad = ad("PAUSADO", "APROVADO");
        if (!mediaStatus.equals("NENHUMA")) photo(ad, 0, mediaStatus, "PENDENTE");
        assertReactivationWithoutEligiblePhoto(ad, administrative);
    }

    @ParameterizedTest
    @CsvSource({"false,ARQUIVO_PENDENTE", "true,ARQUIVO_PENDENTE",
            "false,ARQUIVO_REJEITADO", "true,ARQUIVO_REJEITADO",
            "false,ARQUIVO_REMOVIDO", "true,ARQUIVO_REMOVIDO",
            "false,FINALIDADE_STORY", "true,FINALIDADE_STORY",
            "false,VIDEO", "true,VIDEO", "false,STORY", "true,STORY",
            "false,FOTO_REJEITADA", "true,FOTO_REJEITADA", "false,FOTO_REMOVIDA", "true,FOTO_REMOVIDA",
            "false,DOCUMENTO_HISTORICO", "true,DOCUMENTO_HISTORICO"})
    void reativacaoNaoConfundeClassificacaoAparenteComFotoElegivel(boolean administrative, String scenario) {
        Ad ad = ad("PAUSADO", "APROVADO");
        String type = scenario.equals("VIDEO") ? "VIDEO" : scenario.endsWith("STORY") ? "STORY" : "FOTO";
        String purpose = scenario.endsWith("STORY") ? "STORY" : "GALERIA";
        String status = scenario.equals("FOTO_REJEITADA") ? "REJEITADA"
                : scenario.equals("FOTO_REMOVIDA") ? "REMOVIDA" : "PUBLICAVEL";
        String fileStatus = scenario.startsWith("ARQUIVO_") ? scenario.substring("ARQUIVO_".length()) : "VALIDADO";
        Media candidate = media(ad, 0, type, purpose, status, fileStatus,
                type.equals("VIDEO") ? "video/mp4" : "image/jpeg", 1024);
        if (scenario.equals("FINALIDADE_STORY")) {
            List<String> beforeInvalidPurpose = mediaSnapshot(ad);
            // V005 impede FOTO/STORY; conservar a STORY valida em vez de contornar a constraint.
            assertThatThrownBy(() -> jdbc.update("update anuncio_midia set tipo = 'FOTO' where id = ?", candidate.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("anuncio_midia_story_consistencia_chk");
            assertThat(mediaSnapshot(ad)).isEqualTo(beforeInvalidPurpose);
        }
        if (scenario.equals("DOCUMENTO_HISTORICO")) historicalDocument(ad, candidate);
        assertReactivationWithoutEligiblePhoto(ad, administrative);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void visibilidadeAusenteNaoPodeSerPersistidaComoPublicavelNemSatisfazerReativacao(boolean administrative) {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media candidate = photo(ad, 0, "PENDENTE", "VALIDADO");
        List<String> beforeInvalidClassification = mediaSnapshot(ad);
        // V018 impede esse estado em PostgreSQL; nao desabilitar a constraint para fabricar a prova.
        assertThatThrownBy(() -> jdbc.update("update anuncio_midia set status = 'PUBLICAVEL' where id = ?", candidate.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("anuncio_midia_publicavel_visibilidade_chk");
        assertThat(mediaSnapshot(ad)).isEqualTo(beforeInvalidClassification);
        assertReactivationWithoutEligiblePhoto(ad, administrative);
    }

    @ParameterizedTest
    @CsvSource({"false,REMOVIDO", "true,REMOVIDO", "false,DATA_REMOCAO", "true,DATA_REMOCAO"})
    void fotoAdicionadaPosteriormenteNaoReabreEstadoTerminal(boolean administrative, String terminal) {
        Ad ad = ad(terminal.equals("REMOVIDO") ? "REMOVIDO" : "PAUSADO", "APROVADO");
        if (terminal.equals("DATA_REMOCAO")) jdbc.update("update anuncio set removido_em = ? where id = ?", AGORA, ad.id());
        Media addedLater = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        assertThat(vinculos.findFotosAprovadasElegiveisIds(ad.id())).containsExactly(addedLater.id());
        Map<String, Object> before = persistedSnapshot(ad);

        assertHttpReason(administrative ? 409 : 404,
                administrative ? "anuncio nao pode ser reativado" : "anuncio nao encontrado",
                () -> reactivate(ad, administrator, administrative, "reativar-terminal-com-foto"));

        assertThat(persistedSnapshot(ad)).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"false,SUSPENSO", "true,SUSPENSO", "false,EXCLUIDO", "true,EXCLUIDO",
            "false,MODERACAO", "true,MODERACAO"})
    void reativacaoComFotoNaoContornaUsuarioOuModeracaoInvalidos(boolean administrative, String restriction) {
        Ad ad = ad("PAUSADO", restriction.equals("MODERACAO") ? "PENDENTE" : "APROVADO");
        photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        if (restriction.equals("EXCLUIDO")) {
            // Preparar o estado completo exigido por V044 pelo metodo canonico, sem excluir o anuncio.
            transaction().executeWithoutResult(ignored -> {
                entityManager.find(UsuarioEntity.class, ad.owner()).anonimizarDefinitivamente(administrator.usuarioId(), AGORA);
                entityManager.flush();
            });
        } else if (!restriction.equals("MODERACAO")) {
            jdbc.update("update usuario set status = ? where id = ?", restriction, ad.owner());
        }
        Map<String, Object> before = persistedSnapshot(ad);

        assertHttpReason(administrative || restriction.equals("MODERACAO") ? 409 : 401,
                administrative ? "anuncio nao pode ser reativado"
                        : restriction.equals("MODERACAO") ? "transicao de anuncio invalida" : "sessao publica invalida",
                () -> reactivate(ad, administrator, administrative, "reativar-restricao-independente"));

        assertThat(persistedSnapshot(ad)).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void reativacaoNaoContornaBloqueioJuridicoPersistido(boolean administrative, boolean blockUser) {
        Ad ad = ad("PAUSADO", "APROVADO");
        photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        AdminBloqueioJuridicoRequest request = new AdminBloqueioJuridicoRequest(
                CategoriaBloqueioJuridico.FRAUDE, "Bloqueio sintetico de teste focal", null);
        if (blockUser) juridico.bloquearAnuncioEUsuario(ad.id(), request, administrator, "bloquear-antes-reativar");
        else juridico.bloquearAnuncio(ad.id(), request, administrator, "bloquear-antes-reativar");
        Map<String, Object> before = persistedSnapshot(ad);

        assertHttpReason(administrative || !blockUser ? 409 : 401,
                administrative ? "anuncio nao pode ser reativado"
                        : blockUser ? "sessao publica invalida" : "bloqueio juridico impede alteracao do anuncio",
                () -> reactivate(ad, administrator, administrative, "reativar-juridico-bloqueado"));

        assertThat(persistedSnapshot(ad)).isEqualTo(before);
    }

    @Test
    void reativacaoExigeProprietarioCorretoESessaoAdministrativaAutorizada() {
        Ad ad = ad("PAUSADO", "APROVADO");
        photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        Authentication other = auth(user());
        AdminUserPrincipal disabled = new AdminUserPrincipal(administrator.usuarioId(), "Admin desabilitado",
                "admin-disabled@example.invalid", null, List.of(PapelUsuario.ADMIN), List.of(), List.of(), false);
        Map<String, Object> before = persistedSnapshot(ad);

        assertHttpReason(403, "anuncio pertence a outro usuario",
                () -> ciclo.reativar(ad.slug(), other, "reativar-owner-incorreto"));
        assertHttpReason(401, "sessao publica obrigatoria",
                () -> ciclo.reativar(ad.slug(), null, "reativar-sem-sessao"));
        assertHttpReason(403, "administrador obrigatorio",
                () -> juridico.reativar(ad.id(), disabled, "reativar-admin-desabilitado"));
        assertThat(persistedSnapshot(ad)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "AJUSTE_SOLICITADO"})
    void aprovacaoVerdadeiraContinuaExigindoDecisaoDaPendenciaMesmoComOutraAprovada(String pendingStatus) {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        Media approved = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        Media pending = photo(ad, 1, pendingStatus, "PENDENTE");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        revision(ad);
        Map<String, Object> before = persistedSnapshot(ad);

        assertHttpReason(409, FotoElegivelAnuncioPolicy.MENSAGEM_FOTO_AGUARDANDO_DECISAO,
                () -> moderacao.aprovarEPublicarAnuncio(ad.id(), administrator, "aprovacao-exige-todas-decisoes"));

        assertThat(persistedSnapshot(ad)).isEqualTo(before);
        assertThat(vinculos.findFotosAprovadasElegiveisIds(ad.id())).containsExactly(approved.id());
        assertThat(vinculos.findFotosAguardandoDecisaoIds(ad.id())).containsExactly(pending.id());
    }

    @Test
    void aprovacaoVerdadeiraMantemRecusaIndependenteDeKycMesmoComFotoAprovada() {
        Ad ad = ad("PENDENTE_REVISAO", "PENDENTE");
        photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        revision(ad);
        Map<String, Object> before = persistedSnapshot(ad);

        assertHttpReason(409, "documentacao KYC ainda nao foi enviada",
                () -> moderacao.aprovarEPublicarAnuncio(ad.id(), administrator, "aprovacao-kyc-ausente"));

        assertThat(persistedSnapshot(ad)).isEqualTo(before);
    }

    @Test
    void reativacaoProprietariaAnteriorAoDeleteNaoImpedeEncerramentoPosterior() throws Exception {
        Ad ad = ad("PAUSADO", "APROVADO");
        Media photo = photo(ad, 0, "PUBLICAVEL", "VALIDADO");
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        List<String> mediaBefore = mediaSnapshot(ad);
        Ordered result = ordered(ad, () -> {
            Object response = ciclo.reativar(ad.slug(), ad.auth(), "reativar-owner-primeiro");
            entityManager.flush();
            assertReactivated(ad, administrator, false, "reativar-owner-primeiro");
            assertThat(mediaSnapshot(ad)).isEqualTo(mediaBefore);
            return response;
        }, () -> remove(ad, photo));

        assertThat(result.failure()).isNull();
        assertClosed(ad, photo);
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ? and acao = 'ANUNCIO_REATIVADO_PELO_USUARIO'", ad.id())).isEqualTo(1);
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo(1);
    }

    private Object reactivate(Ad ad, AdminUserPrincipal administrator, boolean administrative, String requestId) {
        return administrative ? juridico.reativar(ad.id(), administrator, requestId)
                : ciclo.reativar(ad.slug(), ad.auth(), requestId);
    }

    private void assertReactivationWithoutEligiblePhoto(Ad ad, boolean administrative) {
        AdminUserPrincipal administrator = admin(ad);
        kycAprovado(ad, administrator);
        assertThat(vinculos.findFotosAprovadasElegiveisIds(ad.id())).isEmpty();
        Map<String, Object> before = persistedSnapshot(ad);
        assertHttpReason(409, "O anúncio precisa manter ao menos uma foto aprovada para ser reativado.",
                () -> reactivate(ad, administrator, administrative, "reativar-sem-foto-elegivel"));
        assertThat(persistedSnapshot(ad)).isEqualTo(before);
    }

    private void assertReactivated(Ad ad, AdminUserPrincipal administrator, boolean administrative, String requestId) {
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("PUBLICADO");
        assertThat(value("select status_moderacao from anuncio where id = ?", ad.id())).isEqualTo("APROVADO");
        assertThat(jdbc.queryForObject("select removido_em is null from anuncio where id = ?", Boolean.class, ad.id())).isTrue();
        String action = administrative ? "ANUNCIO_REATIVADO_ADMINISTRATIVAMENTE" : "ANUNCIO_REATIVADO_PELO_USUARIO";
        UUID actor = administrative ? administrator.usuarioId() : ad.owner();
        assertThat(jdbc.queryForObject("""
                select count(*) from auditoria_evento where recurso_id = ? and acao = ?
                  and ator_usuario_id = ? and request_id = ? and resultado = 'SUCESSO'
                """, Long.class, ad.id(), action, actor, requestId)).isEqualTo(1);
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ?", ad.id())).isEqualTo(1);
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo(administrative ? 1 : 0);
        if (administrative) {
            assertThat(value("select motivo from anuncio_status_historico where anuncio_id = ?", ad.id())).isEqualTo("REATIVACAO_ADMINISTRATIVA");
            assertThat(jdbc.queryForObject("""
                    select count(*) from anuncio_status_historico where anuncio_id = ?
                      and status_anterior = 'PAUSADO' and status_novo = 'PUBLICADO' and ator_usuario_id = ?
                    """, Long.class, ad.id(), administrator.usuarioId())).isEqualTo(1);
        }
        assertThat(count("select count(*) from revisao_anuncio where anuncio_id = ?", ad.id())).isZero();
    }

    private List<String> mediaSnapshot(Ad ad) {
        return jdbc.queryForList("""
                select jsonb_build_object('midia', to_jsonb(m), 'arquivo', to_jsonb(a))::text
                from anuncio_midia m join arquivo_midia a on a.id = m.arquivo_midia_id
                where m.anuncio_id = ? order by m.id
                """, String.class, ad.id());
    }

    private Map<String, Object> persistedSnapshot(Ad ad) {
        return Map.of(
                "anuncio", value("select to_jsonb(a)::text from anuncio a where id = ?", ad.id()),
                "usuario", value("select to_jsonb(u)::text from usuario u where id = ?", ad.owner()),
                "midias", mediaSnapshot(ad),
                "historico", jdbc.queryForList("select to_jsonb(h)::text from anuncio_status_historico h where anuncio_id = ? order by id", String.class, ad.id()),
                "auditoria", jdbc.queryForList("select to_jsonb(e)::text from auditoria_evento e where recurso_id = ? order by id", String.class, ad.id()),
                "revisoes", jdbc.queryForList("select to_jsonb(r)::text from revisao_anuncio r where anuncio_id = ? order by id", String.class, ad.id()),
                "busca", jdbc.queryForList("select to_jsonb(b)::text from documento_busca_anuncio b where anuncio_id = ?", String.class, ad.id()),
                "documentos", jdbc.queryForList("select to_jsonb(d)::text from documento_usuario d where usuario_id = ? order by id", String.class, ad.owner()),
                "bloqueios", jdbc.queryForList("select to_jsonb(b)::text from anuncio_bloqueio_juridico b where usuario_id = ? order by id", String.class, ad.owner()));
    }

    private void historicalDocument(Ad ad, Media media) {
        UUID document = UUID.randomUUID();
        jdbc.update("""
                insert into documento_usuario(id,usuario_id,arquivo_midia_id,envio_id,parte,tipo,status,politica_retencao,
                  criado_em,atualizado_em,removido_em)
                values(?,?,?,?,'UNICO','IDENTIDADE','REMOVIDO','MANUAL',?,?,?)
                """, document, ad.owner(), media.fileId(), document, AGORA, AGORA, AGORA);
    }

    private Ordered ordered(Ad ad, Callable<?> firstOperation, Callable<?> secondOperation) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();
        try {
            var first = executor.submit(() -> transaction().execute(status -> {
                jdbc.execute("set local lock_timeout = '8s'");
                firstPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                // Barreira de teste na mesma ordem do protocolo real, sem simular seus efeitos.
                jdbc.queryForObject("select id from usuario where id = ? for update", UUID.class, ad.owner());
                jdbc.queryForObject("select id from anuncio where id = ? for update", UUID.class, ad.id());
                locked.countDown();
                await(release);
                return call(firstOperation);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                try {
                    return new Ordered(null, transaction().execute(status -> {
                        jdbc.execute("set local lock_timeout = '8s'");
                        secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                        secondStarted.countDown();
                        return call(secondOperation);
                    }), null);
                } catch (Throwable exception) {
                    return new Ordered(null, null, exception);
                }
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            boolean blocked = false;
            while (System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists(select 1 from pg_stat_activity
                          where pid = ? and wait_event_type = 'Lock' and ? = any(pg_blocking_pids(pid)))
                        """, Boolean.class, secondPid.get(), firstPid.get()));
                if (blocked) break;
                if (second.isDone()) break;
                Thread.sleep(20);
            }
            assertThat(blocked).as("servico concorrente real deve aguardar o PID da transacao detentora").isTrue();
            release.countDown();
            Object firstValue = first.get(15, TimeUnit.SECONDS);
            Ordered secondValue = second.get(15, TimeUnit.SECONDS);
            return new Ordered(firstValue, secondValue.second(), secondValue.failure());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).as("threads proprias encerradas").isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("barreira focal expirou");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("barreira focal interrompida", exception);
        }
    }

    private static Object call(Callable<?> operation) {
        try { return operation.call(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private MeuAnuncioMidiasResponseDto remove(Ad ad, Media media) {
        return midias.remover(ad.slug(), media.id(), ad.auth(), "ultima-foto-" + media.id());
    }

    private MeuAnuncioMidiasResponseDto upload(Ad ad) throws Exception {
        BufferedImage image = new BufferedImage(120, 160, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "jpeg", bytes)).isTrue();
        return midias.enviarLote(ad.slug(), List.of(new MockMultipartFile(
                "arquivos", "foto-sintetica.jpg", "image/jpeg", bytes.toByteArray())),
                "ultima-foto-upload-" + UUID.randomUUID(), ad.auth());
    }

    private Ad ad(String status, String moderation) {
        UUID owner = user();
        UUID id = UUID.randomUUID();
        String slug = "ultima-foto-" + id;
        UUID[] location = locality();
        UUID state = location[0];
        UUID city = location[1];
        jdbc.update("""
                insert into anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,preco,
                  whatsapp_normalizado,criado_em,atualizado_em,versao,publicado_em,ultima_publicacao_em)
                values(?,?,?,'Anuncio sintetico ultima foto','Descricao sintetica suficientemente longa para o teste',
                  ?,?,'ACOMPANHANTE_FEMININA',100,'+5562999999999',?,?,0,?,?)
                """, id, owner, slug, status, moderation, AGORA, AGORA,
                moderation.equals("APROVADO") ? AGORA : null, moderation.equals("APROVADO") ? AGORA : null);
        jdbc.update("insert into anuncio_localizacao(anuncio_id,estado_id,cidade_id,criado_em,atualizado_em) values(?,?,?,?,?)",
                id, state, city, AGORA, AGORA);
        jdbc.update("""
                insert into documento_busca_anuncio(anuncio_id,texto_busca,estado_id,cidade_id,categoria,status_publicacao,
                  tem_midia_valida,atualizado_em) values(?,'fixture ultima foto',?,?,'ACOMPANHANTE_FEMININA',?,?,?)
                """, id, state, city, moderation.equals("APROVADO") ? "PUBLICAVEL" : "NAO_PUBLICAVEL",
                moderation.equals("APROVADO"), AGORA);
        return new Ad(id, owner, slug, auth(owner));
    }

    private UUID[] locality() {
        UUID state = UUID.randomUUID();
        jdbc.update("insert into estado(id,uf,nome,nome_normalizado,criado_em) values(?,'GO','Goias','goias',?) on conflict(uf) do nothing", state, AGORA);
        state = jdbc.queryForObject("select id from estado where uf = 'GO'", UUID.class);
        UUID city = UUID.randomUUID();
        jdbc.update("insert into cidade(id,estado_id,nome,nome_normalizado,slug,criado_em) values(?,?,'Goiania','goiania','goiania',?) on conflict(estado_id,slug) do nothing", city, state, AGORA);
        city = jdbc.queryForObject("select id from cidade where estado_id = ? and slug = 'goiania'", UUID.class, state);
        return new UUID[]{state, city};
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into usuario(id,nome,email_normalizado,telefone_normalizado,status,tipo_conta,criado_em,atualizado_em,versao)
                values(?,?,?,?,'ATIVO','ANUNCIANTE',?,?,0)
                """, id, "focal-" + id, id + "@example.invalid", "+5562" + PHONE_SEQUENCE.incrementAndGet(), AGORA, AGORA);
        return id;
    }

    private Authentication auth(UUID user) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new PublicUserPrincipal(user, "focal-" + user, user + "@example.invalid"), null, List.of());
    }

    private AdminUserPrincipal admin(Ad ad) {
        UUID administrator = user();
        jdbc.update("update usuario set tipo_conta = 'STAFF' where id = ?", administrator);
        return new AdminUserPrincipal(administrator, "Admin sintetico", administrator + "@example.invalid", null,
                List.of(PapelUsuario.ADMIN), List.of(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true);
    }

    private Media photo(Ad ad, int order, String status, String fileStatus) {
        return media(ad, order, "FOTO", "GALERIA", status, fileStatus, "image/jpeg", 1024);
    }

    private void kycAprovado(Ad ad, AdminUserPrincipal administrator) {
        UUID file = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        jdbc.update("""
                insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                values(?,'R2','privadas',?,'image/jpeg',1024,'VALIDADO',?)
                """, file, "hml/documentos/" + file, AGORA);
        jdbc.update("""
                insert into documento_usuario(id,usuario_id,arquivo_midia_id,envio_id,parte,tipo,status,
                  politica_retencao,criado_em,atualizado_em,validado_por,validado_em)
                values(?,?,?,?,'UNICO','IDENTIDADE','VALIDADO','MANUAL',?,?,?,?)
                """, document, ad.owner(), file, document, AGORA, AGORA, administrator.usuarioId(), AGORA);
    }

    private Media media(Ad ad, int order, String type, String purpose, String status, String fileStatus, String mime, long bytes) {
        UUID id = UUID.randomUUID();
        UUID file = UUID.randomUUID();
        jdbc.update("""
                insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                values(?,'R2','privadas',?,?,?,?,?)
                """, file, "hml/midias-pendentes/" + file, mime, bytes, fileStatus, AGORA);
        jdbc.update("""
                insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,visibilidade_midia,criado_em,atualizado_em)
                values(?,?,?,?,?,?,?,?,?,?)
                """, id, ad.id(), file, type, purpose, order, status,
                !type.equals("FOTO") ? "RESTRITA_18" : status.equals("PUBLICAVEL") ? "LIVRE" : null, AGORA, AGORA);
        return new Media(id, file);
    }

    private UUID revision(Ad ad) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into revisao_anuncio(id,anuncio_id,tipo,status,criado_por,criado_em) values(?,?,'CRIACAO','ABERTA',?,?)",
                id, ad.id(), ad.owner(), AGORA);
        return id;
    }

    private MeuAnuncioAtualizacaoRequestDto request() {
        return new MeuAnuncioAtualizacaoRequestDto("Titulo sintetico atualizado", "Descricao sintetica completa para atualizar o anuncio.",
                "ACOMPANHANTE_FEMININA", new BigDecimal("100.00"), "GO", "Goiania", null, null,
                List.of("MEU_LOCAL"), List.of("ORAL"), false, null);
    }

    private void assertClosed(Ad ad, Media photo) {
        assertThat(value("select status from anuncio where id = ?", ad.id())).isEqualTo("REMOVIDO");
        assertThat(jdbc.queryForObject("select removido_em is not null from anuncio where id = ?", Boolean.class, ad.id())).isTrue();
        assertThat(value("select status from anuncio_midia where id = ?", photo.id())).isEqualTo("REMOVIDA");
        assertThat(count("select count(*) from arquivo_midia where id = ?", photo.fileId())).isEqualTo(1);
        assertThat(vinculos.findFotosValidasAtivasIds(ad.id())).isEmpty();
        assertThat(count("select count(*) from auditoria_evento where recurso_id = ? and acao = 'ANUNCIO_ENCERRADO_SEM_FOTOS'", ad.id())).isEqualTo(1);
        assertThat(count("select count(*) from anuncio_status_historico where anuncio_id = ? and motivo = 'ULTIMA_FOTO_REMOVIDA'", ad.id())).isEqualTo(1);
        assertThat(value("select status_publicacao from documento_busca_anuncio where anuncio_id = ?", ad.id())).isEqualTo("REMOVIDO");
    }

    private String value(String sql, UUID id) { return jdbc.queryForObject(sql, String.class, id); }
    private long count(String sql, UUID id) { return jdbc.queryForObject(sql, Long.class, id); }
    private void assertHttp(int status, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }

    private void assertHttpReason(int status, String reason, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode().value()).isEqualTo(status);
            assertThat(exception.getReason()).isEqualTo(reason);
        });
    }

    private record Ad(UUID id, UUID owner, String slug, Authentication auth) { }
    private record Media(UUID id, UUID fileId) { }
    private record Ordered(Object first, Object second, Throwable failure) { }
}
