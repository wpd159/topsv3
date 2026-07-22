package br.com.topsdojob.v3.application.admin.readonly;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioCalculado;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalizacaoSanitizadaDto;
import br.com.topsdojob.v3.application.metrica.VisualizacaoTotalCanonicaService;
import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.CliqueWhatsappRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminAnuncioDetalhadoConsultaServiceTest {

    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioMidiaRepository midiaRepository = mock(AnuncioMidiaRepository.class);
    private final ArquivoMidiaRepository arquivoRepository = mock(ArquivoMidiaRepository.class);
    private final RevisaoAnuncioRepository revisaoRepository = mock(RevisaoAnuncioRepository.class);
    private final DocumentoUsuarioRepository documentoRepository = mock(DocumentoUsuarioRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final AtivacaoBeneficioRepository ativacaoBeneficioRepository = mock(AtivacaoBeneficioRepository.class);
    private final AdminLocalizacaoConsultaSupport localizacaoSupport = mock(AdminLocalizacaoConsultaSupport.class);
    private final AdminMidiaDetalhadaConsultaService midiaService = mock(AdminMidiaDetalhadaConsultaService.class);
    private final VisualizacaoTotalCanonicaService visualizacaoService = mock(VisualizacaoTotalCanonicaService.class);
    private final CliqueWhatsappRepository cliqueRepository = mock(CliqueWhatsappRepository.class);
    private final BeneficioAnuncioConsultaService beneficioService = mock(BeneficioAnuncioConsultaService.class);
    private final MidiaPublicaUrlService urlService = mock(MidiaPublicaUrlService.class);
    private final AdminKycService kycService = mock(AdminKycService.class);
    private final AdminAnuncioDetalhadoConsultaService service = new AdminAnuncioDetalhadoConsultaService(
            anuncioRepository,
            midiaRepository,
            arquivoRepository,
            revisaoRepository,
            documentoRepository,
            usuarioRepository,
            auditoriaRepository,
            ativacaoBeneficioRepository,
            localizacaoSupport,
            midiaService,
            visualizacaoService,
            cliqueRepository,
            beneficioService,
            urlService,
            kycService,
            new ObjectMapper());
    private UUID anuncioId;
    private AnuncioEntity anuncio;
    private UsuarioEntity usuario;

    @BeforeEach
    void prepararDetalhe() {
        anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();
        anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "detalhe-protegido",
                "Titulo administrativo",
                "Descricao integral do anuncio para moderacao",
                "MASSAGENS",
                new BigDecimal("300.00"),
                "+5562999999999",
                agora.minusDays(10));
        usuario = UsuarioEntity.criarCadastroPublico(
                usuarioId,
                "Nome publico",
                "pessoa@example.invalid",
                "+5562888888888",
                LocalDate.of(1990, 5, 10),
                agora.minusYears(2));
        usuario.aplicarDadosKyc("Nome Civil Completo", "12345678909", LocalDate.of(1990, 5, 10), agora);
        usuario.confirmarEmail(agora);
        when(localizacaoSupport.filtrarAnuncioIds(null, null, null)).thenReturn(null);
        when(anuncioRepository.findById(anuncioId)).thenReturn(Optional.of(anuncio));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(localizacaoSupport.carregar(List.of(anuncioId))).thenReturn(Map.of(
                anuncioId, new AdminLocalizacaoSanitizadaDto(
                        "GO", "Goiania", "Setor Bueno", "Regiao central")));
        when(revisaoRepository.countByAnuncioId(anuncioId)).thenReturn(0L);
        when(revisaoRepository.findFirstByAnuncioIdAndStatusInOrderByCriadoEmDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(revisaoRepository.findByAnuncioId(anuncioId)).thenReturn(List.of());
        when(midiaRepository.findByAnuncioId(anuncioId)).thenReturn(List.of());
        when(midiaRepository.countByAnuncioIdAndTipoNot(anuncioId, TipoAnuncioMidia.STORY)).thenReturn(0L);
        when(auditoriaRepository.findByRecursoIdInOrderByCriadoEmDesc(any(), any())).thenReturn(List.of());
        when(ativacaoBeneficioRepository.findByAnuncioId(anuncioId)).thenReturn(List.of());
        when(documentoRepository.countByUsuarioIdAndStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
                org.mockito.ArgumentMatchers.eq(usuarioId),
                org.mockito.ArgumentMatchers.<List<StatusDocumentoUsuario>>any())).thenReturn(0L);
        when(beneficioService.consultarCalculadosPorAnuncio(any())).thenReturn(Map.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void filaCarregaMiniaturaPremiumEMetricasEmLoteSemDadosPrivados() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "id", UUID.randomUUID());
        set(vinculo, "anuncioId", anuncioId);
        set(vinculo, "arquivoMidiaId", arquivoId);
        set(vinculo, "tipo", TipoAnuncioMidia.FOTO);
        set(vinculo, "status", StatusAnuncioMidia.PUBLICAVEL);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.LIVRE);
        set(vinculo, "ordem", 0);
        set(vinculo, "criadoEm", OffsetDateTime.now().minusDays(1));
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "id", arquivoId);
        set(arquivo, "statusArquivo", StatusArquivoMidia.VALIDADO);
        BeneficioPremiumEntity beneficio = BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                "ANUNCIO_TOPO",
                "Anuncio no topo",
                "Beneficio vigente",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                true,
                OffsetDateTime.now().minusDays(1));
        BeneficioPremiumEntity beneficioExpirado = BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                "WHATSAPP_CARD",
                "WhatsApp especial",
                "Beneficio expirado",
                EscopoBeneficioPremium.ANUNCIO,
                true,
                false,
                OffsetDateTime.now().minusDays(30));
        CliqueWhatsappRepository.ContagemPorAnuncioProjection cliques =
                mock(CliqueWhatsappRepository.ContagemPorAnuncioProjection.class);
        when(cliques.getAnuncioId()).thenReturn(anuncioId);
        when(cliques.getTotalCliques()).thenReturn(3L);
        when(anuncioRepository.findFilaAdministrativa(
                any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(anuncio), PageRequest.of(0, 20), 1));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(usuario));
        when(revisaoRepository.findByAnuncioIdInAndStatusInOrderByCriadoEmDesc(any(), any()))
                .thenReturn(List.of());
        when(midiaRepository.countByAnuncioIdInAndTipoNot(any(), any())).thenReturn(List.of());
        when(revisaoRepository.countByAnuncioIdIn(any())).thenReturn(List.of());
        when(documentoRepository.countByUsuarioIdInAndStatusIn(any(), any())).thenReturn(List.of());
        when(cliqueRepository.countPermitidosPorAnuncioIdIn(any())).thenReturn(List.of(cliques));
        when(visualizacaoService.calcularEmLote(List.of(anuncioId)))
                .thenReturn(Map.of(anuncioId, VisualizacoesCanonicasDto.total(12)));
        when(beneficioService.consultarCalculadosPorAnuncio(any())).thenReturn(Map.of(
                anuncioId,
                List.of(new PremiumBeneficioCalculado(
                        null,
                        beneficio,
                        null,
                        PremiumBeneficioStatusCalculado.ATIVO,
                        List.of(),
                        false,
                        false),
                        new PremiumBeneficioCalculado(
                                null,
                                beneficioExpirado,
                                null,
                                PremiumBeneficioStatusCalculado.EXPIRADO,
                                List.of(),
                                false,
                                false))));
        when(midiaRepository.findByAnuncioIdIn(List.of(anuncioId))).thenReturn(List.of(vinculo));
        when(arquivoRepository.findByIdIn(List.of(arquivoId))).thenReturn(List.of(arquivo));
        when(urlService.resolver(vinculo, arquivo))
                .thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("https://media.example.invalid/foto.jpg", null));

        var pagina = service.listar(
                0,
                20,
                null,
                null,
                null,
                null,
                null,
                AdminAnuncioOrdenacao.MAIS_RECENTES,
                false);

        assertThat(pagina.itens()).hasSize(1);
        var item = pagina.itens().get(0);
        assertThat(item.miniaturaUrl()).isEqualTo("https://media.example.invalid/foto.jpg");
        assertThat(item.beneficiosPremiumVigentes()).containsExactly("Anuncio no topo");
        assertThat(item.visualizacoes().total()).isEqualTo(12);
        assertThat(item.cliquesWhatsapp()).isEqualTo(3);
        assertThat(item.anunciante().emailMascarado()).isEqualTo("p***@example.invalid");
        assertThat(item.toString()).doesNotContain("12345678909", "+5562888888888");
        assertThat(item.localizacao().enderecoResumido()).isEqualTo("Regiao central");
        verify(beneficioService).consultarCalculadosPorAnuncio(List.of(anuncioId));
    }

    @ParameterizedTest
    @EnumSource(AdminAnuncioOrdenacao.class)
    void filaEncaminhaTodasAsOrdenacoesParaPaginacaoNoBanco(AdminAnuncioOrdenacao ordenacao) {
        when(anuncioRepository.findFilaAdministrativa(
                any(), anyBoolean(), any(), any(), eq(ordenacao.name()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 30), 0));

        var pagina = service.listar(0, 30, null, null, null, null, null, ordenacao, false);

        assertThat(pagina.itens()).isEmpty();
        verify(anuncioRepository).findFilaAdministrativa(
                any(), anyBoolean(), any(), any(), eq(ordenacao.name()), eq(PageRequest.of(0, 30)));
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 30, 50, 100})
    void filaAceitaSomenteTamanhosCanonicosDaProducao(int size) {
        when(anuncioRepository.findFilaAdministrativa(
                any(), anyBoolean(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, size), 0));

        var pagina = service.listar(
                0, size, null, null, null, null, null, AdminAnuncioOrdenacao.MAIS_RECENTES, false);

        assertThat(pagina.size()).isEqualTo(size);
    }

    @Test
    void filaRecusaTamanhoExcessivo() {
        assertThatThrownBy(() -> service.listar(
                0, 101, null, null, null, null, null, AdminAnuncioOrdenacao.MAIS_RECENTES, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void detalheRetornaProprietarioIntegralEMetricasCanonicas() {
        when(visualizacaoService.calcular(anuncioId)).thenReturn(VisualizacoesCanonicasDto.total(25));
        when(cliqueRepository.countByAnuncioIdAndPermitidoTrue(anuncioId)).thenReturn(5L);

        var detalhe = service.detalhar(anuncioId, false);

        assertThat(detalhe.anunciante().nomeCivil()).isEqualTo("Nome Civil Completo");
        assertThat(detalhe.anunciante().email()).isEqualTo("pessoa@example.invalid");
        assertThat(detalhe.anunciante().cpf()).isEqualTo("123.456.789-09");
        assertThat(detalhe.anunciante().whatsapp()).isEqualTo("+5562888888888");
        assertThat(detalhe.metricas().visualizacoes().total()).isEqualTo(25);
        assertThat(detalhe.metricas().cliquesWhatsapp()).isEqualTo(5);
        assertThat(detalhe.metricas().ctr()).isEqualByComparingTo("20.00");
    }

    @Test
    void historicoPendenteNaoFabricaTotalNemCtr() {
        when(visualizacaoService.calcular(anuncioId)).thenReturn(VisualizacoesCanonicasDto.historicoPendente());
        when(cliqueRepository.countByAnuncioIdAndPermitidoTrue(anuncioId)).thenReturn(5L);

        var detalhe = service.detalhar(anuncioId, false);

        assertThat(detalhe.metricas().visualizacoes().total()).isNull();
        assertThat(detalhe.metricas().visualizacoes().situacao())
                .isEqualTo(VisualizacoesCanonicasDto.Situacao.HISTORICO_PENDENTE);
        assertThat(detalhe.metricas().ctr()).isNull();
    }
}
