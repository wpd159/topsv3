package br.com.topsdojob.v3.application.anuncio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaCleanupService.CleanupException;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaPosCommitCleanupService;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioMidiaUploadService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioMidiaUploadDto;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.anuncio.midia.AnuncioMidiaUploadCoreService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.FotoElegivelAnuncioRepositoryPostgres17IntegrationTest.PostgresSupport;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
        classes = TopsDoJobBackendApplication.class,
        initializers = FotoElegivelAnuncioConcorrenciaPostgres17IntegrationTest.PostgresInitializer.class)
@Import({
        FotoElegivelAnuncioPolicy.class,
        MidiaUploadValidator.class,
        FotoUploadProcessor.class,
        AnuncioMidiaUploadCoreService.class,
        AdminAnuncioMidiaUploadService.class,
        AdminAnuncioMidiaPosCommitCleanupService.class,
        AdminAnuncioMidiaCleanupService.class,
        FotoElegivelAnuncioConcorrenciaPostgres17IntegrationTest.TestBeans.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "FOTO_ELEGIVEL_POSTGRES17_ENABLED", matches = "true")
class FotoElegivelAnuncioConcorrenciaPostgres17IntegrationTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-04T12:00:00Z");

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AnuncioMidiaRepository anuncioMidiaRepository;

    @Autowired
    private ArquivoMidiaRepository arquivoMidiaRepository;

    @Autowired
    private FotoElegivelAnuncioPolicy policy;

    @Autowired
    private AdminAnuncioMidiaUploadService uploadService;

    @Autowired
    private AdminAnuncioMidiaCleanupService cleanupService;

    @Autowired
    private ControlledObjectStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterAll
    static void cleanup() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    void aprovacaoConcorrendoComDecisaoDaFotoNuncaPublicaSemFotoAprovada() throws Exception {
        Fixture fixture = fixture("PENDENTE_REVISAO", "PENDENTE");
        Midia foto = inserirMidia(fixture.anuncioId(), 0, "PENDENTE", null, "PENDENTE");

        List<String> resultados = executarConcorrentes(
                () -> aprovar(fixture.anuncioId()),
                () -> decidirFoto(fixture.anuncioId(), foto));

        assertThat(resultados.get(1)).isEqualTo("OK");
        assertThat(resultados.get(0)).isIn("OK", "CONFLICT");
        assertInvariantePublicacao(fixture.anuncioId());
    }

    @Test
    void aprovacaoConcorrendoComRemocaoNaoProduzFalsoSucesso() throws Exception {
        Fixture fixture = fixture("PENDENTE_REVISAO", "PENDENTE");
        Midia foto = inserirMidia(fixture.anuncioId(), 0, "PUBLICAVEL", "LIVRE", "VALIDADO");

        List<String> resultados = executarConcorrentes(
                () -> aprovar(fixture.anuncioId()),
                () -> remover(fixture.anuncioId(), foto.midiaId()));

        assertThat(resultados).allMatch(item -> item.equals("OK") || item.equals("CONFLICT"));
        assertThat(resultados).contains("OK", "CONFLICT");
        assertInvariantePublicacao(fixture.anuncioId());
    }

    @Test
    void doisDeletesDasDuasUltimasFotosPreservamExatamenteUma() throws Exception {
        Fixture fixture = fixture("PUBLICADO", "APROVADO");
        Midia primeira = inserirMidia(fixture.anuncioId(), 0, "PUBLICAVEL", "LIVRE", "VALIDADO");
        Midia segunda = inserirMidia(fixture.anuncioId(), 1, "PUBLICAVEL", "RESTRITA_18", "VALIDADO");

        List<String> resultados = executarConcorrentes(
                () -> remover(fixture.anuncioId(), primeira.midiaId()),
                () -> remover(fixture.anuncioId(), segunda.midiaId()));

        assertThat(resultados).containsExactlyInAnyOrder("OK", "CONFLICT");
        assertThat(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(fixture.anuncioId()))
                .hasSize(1);
        assertInvariantePublicacao(fixture.anuncioId());
    }

    @Test
    void uploadAdministrativoRealConcorrendoComRemocaoPreservaFotoAprovadaEIdempotencia()
            throws Exception {
        Fixture fixture = fixture("PUBLICADO", "APROVADO");
        Midia aprovada = inserirMidia(fixture.anuncioId(), 0, "PUBLICAVEL", "LIVRE", "VALIDADO");
        byte[] fotoOriginal = imagemPng(Color.RED);
        MockMultipartFile foto = new MockMultipartFile(
                "arquivo", "foto-concorrente.png", "image/png", fotoOriginal);
        String chaveIdempotencia = "admin-concorrencia-fixa";
        AdminUserPrincipal administrador = administrador();
        storage.prepararBloqueioDoProximoPut();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AdminAnuncioMidiaUploadDto upload;
        Throwable falhaRemocao;
        try {
            Future<AdminAnuncioMidiaUploadDto> uploadFuture = executor.submit(() ->
                    uploadService.enviar(
                            fixture.anuncioId(),
                            foto,
                            chaveIdempotencia,
                            administrador,
                            "request-upload-concorrente"));
            boolean uploadEntrouNoStorage = storage.aguardarPut(5, TimeUnit.SECONDS);
            if (!uploadEntrouNoStorage && uploadFuture.isDone()) {
                uploadFuture.get(1, TimeUnit.SECONDS);
            }
            assertThat(uploadEntrouNoStorage)
                    .as("upload real entrou no storage depois de obter os locks JPA")
                    .isTrue();

            CountDownLatch remocaoIniciada = new CountDownLatch(1);
            Future<Throwable> remocaoFuture = executor.submit(() -> {
                try {
                    transacao().executeWithoutResult(status -> {
                        remocaoIniciada.countDown();
                        cleanupService.limparMidia(
                                fixture.anuncioId(), aprovada.midiaId(), AGORA.plusSeconds(1));
                    });
                    return null;
                } catch (Throwable exception) {
                    return exception;
                }
            });
            assertThat(remocaoIniciada.await(5, TimeUnit.SECONDS))
                    .as("remocao real iniciou sua transacao enquanto o upload mantinha os locks")
                    .isTrue();
            assertThatThrownBy(() -> remocaoFuture.get(500, TimeUnit.MILLISECONDS))
                    .as("remocao deve aguardar o lock do upload, provando sobreposicao")
                    .isInstanceOf(TimeoutException.class);

            storage.liberarPut();
            upload = uploadFuture.get(10, TimeUnit.SECONDS);
            falhaRemocao = remocaoFuture.get(10, TimeUnit.SECONDS);
        } finally {
            storage.liberarPut();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(upload.idempotente()).isFalse();
        assertThat(upload.status()).isEqualTo("PENDENTE");
        assertThat(upload.statusArquivo()).isEqualTo("PENDENTE");
        assertThat(falhaRemocao)
                .isInstanceOfSatisfying(CleanupException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.codigo())
                            .isEqualTo(FotoElegivelAnuncioPolicy.CODIGO_ULTIMA_FOTO_APROVADA);
                });
        assertThat(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(fixture.anuncioId()))
                .containsExactly(aprovada.midiaId());
        assertThat(anuncioMidiaRepository.findFotosAguardandoDecisaoIds(fixture.anuncioId()))
                .containsExactly(upload.midiaId());
        assertThat(jdbc.queryForObject(
                "select count(*) from anuncio_midia where anuncio_id = ? and status = 'PENDENTE'",
                Long.class,
                fixture.anuncioId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from arquivo_midia arquivo join anuncio_midia midia "
                        + "on midia.arquivo_midia_id = arquivo.id where midia.anuncio_id = ?",
                Long.class,
                fixture.anuncioId())).isEqualTo(2L);
        UUID novoArquivoId = jdbc.queryForObject(
                "select arquivo_midia_id from anuncio_midia where id = ?",
                UUID.class,
                upload.midiaId());
        assertThat(jdbc.queryForObject(
                "select status_arquivo from arquivo_midia where id = ?",
                String.class,
                novoArquivoId)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject(
                "select count(*) from anuncio_midia where arquivo_midia_id = ?",
                Long.class,
                novoArquivoId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from anuncio_midia where arquivo_midia_id = ? and anuncio_id <> ?",
                Long.class,
                novoArquivoId,
                fixture.anuncioId())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from arquivo_midia arquivo left join anuncio_midia midia "
                        + "on midia.arquivo_midia_id = arquivo.id "
                        + "where arquivo.id = ? and midia.id is null",
                Long.class,
                novoArquivoId)).isZero();
        assertThat(storage.quantidade(StorageArea.PRIVATE_MEDIA)).isEqualTo(1);
        assertThat(storage.quantidade(StorageArea.PUBLIC_MEDIA)).isZero();

        AdminAnuncioMidiaUploadDto retry = uploadService.enviar(
                fixture.anuncioId(),
                new MockMultipartFile(
                        "arquivo", "foto-concorrente.png", "image/png", fotoOriginal),
                chaveIdempotencia,
                administrador,
                "request-upload-retry");
        assertThat(retry.midiaId()).isEqualTo(upload.midiaId());
        assertThat(retry.idempotente()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from anuncio_midia where anuncio_id = ?",
                Long.class,
                fixture.anuncioId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select count(*) from arquivo_midia arquivo join anuncio_midia midia "
                        + "on midia.arquivo_midia_id = arquivo.id where midia.anuncio_id = ?",
                Long.class,
                fixture.anuncioId())).isEqualTo(2L);
        assertThat(storage.quantidade(StorageArea.PRIVATE_MEDIA)).isEqualTo(1);

        MockMultipartFile bytesDiferentes = new MockMultipartFile(
                "arquivo", "foto-concorrente.png", "image/png", imagemPng(Color.BLUE));
        assertThatThrownBy(() -> uploadService.enviar(
                fixture.anuncioId(),
                bytesDiferentes,
                chaveIdempotencia,
                administrador,
                "request-upload-divergente"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject(
                "select count(*) from anuncio_midia where anuncio_id = ?",
                Long.class,
                fixture.anuncioId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select count(*) from arquivo_midia arquivo join anuncio_midia midia "
                        + "on midia.arquivo_midia_id = arquivo.id where midia.anuncio_id = ?",
                Long.class,
                fixture.anuncioId())).isEqualTo(2L);
        assertThat(storage.quantidade(StorageArea.PRIVATE_MEDIA)).isEqualTo(1);
        assertInvariantePublicacao(fixture.anuncioId());
    }

    private void aprovar(UUID anuncioId) {
        transacao().executeWithoutResult(status -> {
            anuncioRepository.findByIdForModeration(anuncioId).orElseThrow();
            policy.validarParaAprovacao(anuncioId);
            jdbc.update("""
                    update anuncio
                    set status = 'PUBLICADO', status_moderacao = 'APROVADO',
                        publicado_em = coalesce(publicado_em, ?), ultima_publicacao_em = ?, atualizado_em = ?
                    where id = ?
                    """, AGORA, AGORA, AGORA, anuncioId);
        });
    }

    private void decidirFoto(UUID anuncioId, Midia foto) {
        transacao().executeWithoutResult(status -> {
            anuncioRepository.findByIdForModeration(anuncioId).orElseThrow();
            anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId);
            arquivoMidiaRepository.findByIdInForUpdate(List.of(foto.arquivoId()));
            jdbc.update(
                    "update arquivo_midia set status_arquivo = 'VALIDADO' where id = ?",
                    foto.arquivoId());
            jdbc.update("""
                    update anuncio_midia
                    set status = 'PUBLICAVEL', visibilidade_midia = 'LIVRE', atualizado_em = ?
                    where id = ?
                    """, AGORA, foto.midiaId());
        });
    }

    private void remover(UUID anuncioId, UUID midiaId) {
        transacao().executeWithoutResult(status -> {
            AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId).orElseThrow();
            List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId);
            AnuncioMidiaEntity alvo = vinculos.stream()
                    .filter(item -> midiaId.equals(item.getId()))
                    .findFirst()
                    .orElseThrow();
            policy.validarRemocaoIndividual(anuncio, midiaId);
            if (alvo.getStatus() == StatusAnuncioMidia.REMOVIDA) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "midia ja removida");
            }
            jdbc.update(
                    "update anuncio_midia set status = 'REMOVIDA', atualizado_em = ? where id = ?",
                    AGORA,
                    midiaId);
        });
    }

    private List<String> executarConcorrentes(Operacao primeira, Operacao segunda) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        try {
            Future<String> a = executor.submit(() -> executar(primeira, prontas, iniciar));
            Future<String> b = executor.submit(() -> executar(segunda, prontas, iniciar));
            assertThat(prontas.await(5, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String executar(
            Operacao operacao,
            CountDownLatch prontas,
            CountDownLatch iniciar) throws InterruptedException {
        prontas.countDown();
        if (!iniciar.await(5, TimeUnit.SECONDS)) {
            return "TIMEOUT_START";
        }
        try {
            operacao.executar();
            return "OK";
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value() == 409 ? "CONFLICT" : "HTTP_" + exception.getStatusCode().value();
        } catch (RuntimeException exception) {
            return "ERROR_" + exception.getClass().getSimpleName();
        }
    }

    private void assertInvariantePublicacao(UUID anuncioId) {
        Map<String, Object> estado = jdbc.queryForMap(
                "select status, status_moderacao from anuncio where id = ?",
                anuncioId);
        if ("PUBLICADO".equals(estado.get("status"))
                && "APROVADO".equals(estado.get("status_moderacao"))) {
            assertThat(anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncioId))
                    .isNotEmpty();
        }
    }

    private Fixture fixture(String status, String moderacao) {
        UUID usuarioId = UUID.randomUUID();
        UUID anuncioId = UUID.randomUUID();
        jdbc.update("""
                insert into usuario (
                  id, nome, status, tipo_conta, criado_em, atualizado_em, versao
                ) values (?, 'Usuario concorrencia', 'ATIVO', 'ANUNCIANTE', ?, ?, 0)
                """, usuarioId, AGORA, AGORA);
        jdbc.update("""
                insert into anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao, categoria,
                  publicado_em, ultima_publicacao_em, criado_em, atualizado_em, versao
                ) values (?, ?, ?, 'Anuncio concorrencia', ?, ?, 'ACOMPANHANTE_FEMININA',
                  ?, ?, ?, ?, 0)
                """, anuncioId, usuarioId, "concorrencia-" + anuncioId, status, moderacao,
                "PUBLICADO".equals(status) ? AGORA : null,
                "PUBLICADO".equals(status) ? AGORA : null,
                AGORA, AGORA);
        return new Fixture(anuncioId);
    }

    private Midia inserirMidia(
            UUID anuncioId,
            int ordem,
            String status,
            String visibilidade,
            String statusArquivo) {
        UUID arquivoId = UUID.randomUUID();
        UUID midiaId = UUID.randomUUID();
        jdbc.update("""
                insert into arquivo_midia (
                  id, storage_provider, bucket, chave_objeto, mime_type, tamanho_bytes,
                  status_arquivo, criado_em
                ) values (?, 'R2', 'foto-concorrencia', ?, 'image/jpeg', 1024, ?, ?)
                """, arquivoId, "foto-concorrencia/" + arquivoId, statusArquivo, AGORA);
        jdbc.update("""
                insert into anuncio_midia (
                  id, anuncio_id, arquivo_midia_id, tipo, finalidade, ordem, status,
                  visibilidade_midia, criado_em, atualizado_em
                ) values (?, ?, ?, 'FOTO', ?, ?, ?, ?, ?, ?)
                """, midiaId, anuncioId, arquivoId, ordem == 0 ? "CAPA" : "GALERIA", ordem,
                status, visibilidade, AGORA, AGORA);
        return new Midia(midiaId, arquivoId);
    }

    private TransactionTemplate transacao() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        template.setTimeout(10);
        return template;
    }

    private byte[] imagemPng(Color cor) throws Exception {
        BufferedImage imagem = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = imagem.createGraphics();
        try {
            graphics.setColor(cor);
            graphics.fillRect(0, 0, imagem.getWidth(), imagem.getHeight());
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(imagem, "png", output)).isTrue();
        return output.toByteArray();
    }

    private AdminUserPrincipal administrador() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin concorrencia",
                "admin-concorrencia@example.invalid",
                "hash",
                List.of(PapelUsuario.ADMIN),
                List.<AdminPermissionDto>of(),
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ANUNCIO_MODERAR"),
                        new SimpleGrantedAuthority("MIDIA_REVISAR")),
                true);
    }

    @FunctionalInterface
    private interface Operacao {
        void executar();
    }

    private record Fixture(UUID anuncioId) {
    }

    private record Midia(UUID midiaId, UUID arquivoId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        MidiaUploadProperties midiaUploadProperties() {
            return new MidiaUploadProperties();
        }

        @Bean
        R2StorageProperties r2StorageProperties() {
            R2StorageProperties properties = new R2StorageProperties();
            properties.setEnabled(true);
            properties.setPublicMediaBucket("publicas-teste");
            properties.setPrivateMediaBucket("privadas-teste");
            properties.setDocumentBucket("documentos-teste");
            properties.setPublicMediaPrefix("hml/teste/midias-aprovadas/");
            properties.setPrivateMediaPrefix("hml/teste/midias-pendentes/");
            properties.setDocumentPrefix("hml/teste/documentos/");
            properties.setPublicBaseUrl("https://public.example.invalid");
            return properties;
        }

        @Bean
        ControlledObjectStorage controlledObjectStorage() {
            return new ControlledObjectStorage();
        }
    }

    static final class ControlledObjectStorage implements ObjectStorage {

        private final Map<StorageArea, ConcurrentMap<String, StoredObject>> objects =
                new EnumMap<>(StorageArea.class);
        private final AtomicBoolean bloquearProximoPut = new AtomicBoolean();
        private volatile CountDownLatch putIniciado = new CountDownLatch(0);
        private volatile CountDownLatch liberarPut = new CountDownLatch(0);

        ControlledObjectStorage() {
            for (StorageArea area : StorageArea.values()) {
                objects.put(area, new ConcurrentHashMap<>());
            }
        }

        void prepararBloqueioDoProximoPut() {
            objects.values().forEach(Map::clear);
            putIniciado = new CountDownLatch(1);
            liberarPut = new CountDownLatch(1);
            bloquearProximoPut.set(true);
        }

        boolean aguardarPut(long timeout, TimeUnit unit) throws InterruptedException {
            return putIniciado.await(timeout, unit);
        }

        void liberarPut() {
            liberarPut.countDown();
        }

        int quantidade(StorageArea area) {
            return objects.get(area).size();
        }

        @Override
        public void put(StorageArea area, String key, byte[] content, String contentType) {
            objects.get(area).put(key, objeto(content, contentType));
        }

        @Override
        public ObjectWriteResult putIfAbsent(
                StorageArea area,
                String key,
                byte[] content,
                String contentType) {
            if (bloquearProximoPut.compareAndSet(true, false)) {
                putIniciado.countDown();
                try {
                    if (!liberarPut.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timeout controlado no storage falso");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("storage falso interrompido", exception);
                }
            }
            StoredObject anterior = objects.get(area).putIfAbsent(
                    key, objeto(content, contentType));
            return anterior == null
                    ? ObjectWriteResult.CREATED
                    : ObjectWriteResult.ALREADY_EXISTS;
        }

        @Override
        public boolean exists(StorageArea area, String key) {
            return objects.get(area).containsKey(key);
        }

        @Override
        public StoredObject get(StorageArea area, String key) {
            StoredObject value = objects.get(area).get(key);
            return value == null ? null : objeto(value.content(), value.contentType());
        }

        @Override
        public void delete(StorageArea area, String key) {
            objects.get(area).remove(key);
        }

        @Override
        public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
            return URI.create("https://private.example.invalid/token");
        }

        @Override
        public Optional<URI> publicUrl(StorageArea area, String key) {
            return Optional.of(URI.create("https://public.example.invalid/" + key));
        }

        private StoredObject objeto(byte[] content, String contentType) {
            return new StoredObject(content.clone(), contentType);
        }
    }

    public static final class PostgresInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                PostgresSupport.start();
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "foto-elegivel-concorrencia-postgres17",
                        Map.ofEntries(
                                Map.entry("spring.datasource.url", PostgresSupport.jdbcUrl()),
                                Map.entry("spring.datasource.username", "topsv3test"),
                                Map.entry("spring.datasource.password", PostgresSupport.credential()),
                                Map.entry("spring.flyway.enabled", "false"),
                                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                                Map.entry("app.storage.r2.enabled", "true"),
                                Map.entry("app.storage.r2.public-media-bucket", "publicas-teste"),
                                Map.entry("app.storage.r2.private-media-bucket", "privadas-teste"),
                                Map.entry("app.storage.r2.document-bucket", "documentos-teste"),
                                Map.entry(
                                        "app.storage.r2.public-media-prefix",
                                        "hml/teste/midias-aprovadas/"),
                                Map.entry(
                                        "app.storage.r2.private-media-prefix",
                                        "hml/teste/midias-pendentes/"),
                                Map.entry(
                                        "app.storage.r2.document-prefix",
                                        "hml/teste/documentos/"),
                                Map.entry(
                                        "app.storage.r2.public-base-url",
                                        "https://public.example.invalid"))));
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "falha ao preparar PostgreSQL 17 para concorrencia de foto elegivel",
                        exception);
            }
        }
    }
}
