package br.com.topsdojob.v3.application.publico.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.credito.CreditoLancamentoResultado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumItemRequest;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumRequest;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumOpcaoEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumOpcaoRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class MinhaContaPremiumServiceTest {

    private final MeusAnunciosConsultaService meusAnuncios = mock(MeusAnunciosConsultaService.class);
    private final CreditoLedgerOperacaoService ledger = mock(CreditoLedgerOperacaoService.class);
    private final MovimentoCreditoRepository movimentos = mock(MovimentoCreditoRepository.class);
    private final PremiumCatalogoService catalogo = mock(PremiumCatalogoService.class);
    private final BeneficioPremiumRepository beneficios = mock(BeneficioPremiumRepository.class);
    private final BeneficioPremiumOpcaoRepository opcoes = mock(BeneficioPremiumOpcaoRepository.class);
    private final GrupoAtivacaoBeneficioRepository grupos = mock(GrupoAtivacaoBeneficioRepository.class);
    private final AtivacaoBeneficioRepository ativacoes = mock(AtivacaoBeneficioRepository.class);
    private final AnuncioRepository anuncios = mock(AnuncioRepository.class);
    private final BeneficioAnuncioConsultaService consulta = mock(BeneficioAnuncioConsultaService.class);
    private final AuditoriaEventoRepository auditoria = mock(AuditoriaEventoRepository.class);
    private final Authentication authentication = mock(Authentication.class);
    private final MinhaContaPremiumService service = new MinhaContaPremiumService(
            meusAnuncios,
            ledger,
            movimentos,
            catalogo,
            beneficios,
            opcoes,
            grupos,
            ativacoes,
            anuncios,
            consulta,
            auditoria,
            new ObjectMapper());

    private UUID usuarioId;
    private AnuncioEntity anuncio;

    @BeforeEach
    void configurarBase() {
        usuarioId = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = UsuarioEntity.criarSolicitacaoLocal(
                usuarioId,
                "QA Monetizacao",
                "qa-monetizacao@example.invalid",
                "5511999999999",
                agora.minusDays(10));
        usuario.confirmarEmail(agora.minusDays(9));
        anuncio = AnuncioEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                usuarioId,
                "homologacao-monetizacao",
                "HOMOLOGACAO MONETIZACAO",
                "Anuncio sintetico para teste da monetizacao",
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                agora.minusDays(2));
        when(meusAnuncios.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(meusAnuncios.anuncioDoUsuario(anuncio.getSlug(), authentication)).thenReturn(anuncio);
        when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(100);
        when(grupos.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(grupos.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ativacoes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(consulta.consultarCalculados(anuncio.getId())).thenReturn(List.of());
        when(ledger.registrar(
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                anyString(),
                any(),
                anyString(),
                any(),
                anyString(),
                anyString()))
                .thenAnswer(invocation -> {
                    int quantidade = invocation.getArgument(3);
                    int saldoAntes = invocation.getArgument(4);
                    UUID referenciaId = invocation.getArgument(7);
                    String chave = invocation.getArgument(8);
                    MovimentoCreditoEntity movimento = MovimentoCreditoEntity.registrar(
                            UUID.randomUUID(),
                            usuarioId,
                            TipoMovimentoCredito.SAIDA,
                            DirecaoMovimentoCredito.DEBITO,
                            quantidade,
                            saldoAntes,
                            saldoAntes - quantidade,
                            OrigemMovimentoCredito.BENEFICIO,
                            "ATIVACAO_BENEFICIO",
                            referenciaId,
                            chave,
                            usuarioId,
                            invocation.getArgument(10),
                            invocation.getArgument(11),
                            agora);
                    return new CreditoLancamentoResultado(movimento, false);
                });
    }

    @Test
    void compraItensDoCatalogoEmUmaOperacaoComSaldoFinalExato() {
        BeneficioPremiumEntity topo = beneficio("ANUNCIO_TOPO", "Anuncio no topo");
        BeneficioPremiumEntity whatsapp = beneficio("WHATSAPP_CARD", "WhatsApp no card");
        BeneficioPremiumOpcaoEntity topoSete = opcao(topo, 7, 10);
        BeneficioPremiumOpcaoEntity whatsappQuatorze = opcao(whatsapp, 14, 18);
        prepararItem(topo, topoSete);
        prepararItem(whatsapp, whatsappQuatorze);
        when(beneficios.findByIdIn(any())).thenReturn(List.of(topo, whatsapp));
        when(opcoes.findAllById(any())).thenReturn(List.of(topoSete, whatsappQuatorze));
        when(anuncios.findAllById(any())).thenReturn(List.of(anuncio));

        var resultado = service.comprar(
                new MinhaCompraPremiumRequest(
                        anuncio.getSlug(),
                        List.of(
                                new MinhaCompraPremiumItemRequest("ANUNCIO_TOPO", 7),
                                new MinhaCompraPremiumItemRequest("WHATSAPP_CARD", 14))),
                "qa-compra-001",
                authentication,
                "req-qa-compra");

        assertThat(resultado.totalDebitado()).isEqualTo(28);
        assertThat(resultado.saldoAnterior()).isEqualTo(100);
        assertThat(resultado.saldoPosterior()).isEqualTo(72);
        assertThat(resultado.ativacoes())
                .extracting(item -> item.duracaoDias())
                .containsExactlyInAnyOrder(7, 14);
        assertThat(resultado.ativacoes())
                .extracting(item -> item.efeitoPublico())
                .contains("Prioridade nas listagens publicas")
                .contains("WhatsApp no card sujeito a verificacao etaria");
        verify(auditoria).save(any());
    }

    @Test
    void compraDeFotosExtrasReservaUploadSemIniciarPrazo() {
        BeneficioPremiumEntity fotos = beneficio("FOTOS_EXTRA_5", "Mais fotos");
        BeneficioPremiumOpcaoEntity seteDias = opcao(fotos, 7, 10);
        prepararItem(fotos, seteDias);
        when(beneficios.findByIdIn(any())).thenReturn(List.of(fotos));
        when(opcoes.findAllById(any())).thenReturn(List.of(seteDias));
        when(anuncios.findAllById(any())).thenReturn(List.of(anuncio));

        var resultado = service.comprar(
                new MinhaCompraPremiumRequest(
                        anuncio.getSlug(),
                        List.of(new MinhaCompraPremiumItemRequest("FOTOS_EXTRA_5", 7))),
                "qa-compra-fotos-espera",
                authentication,
                "req-fotos-espera");

        ArgumentCaptor<AtivacaoBeneficioEntity> salva =
                ArgumentCaptor.forClass(AtivacaoBeneficioEntity.class);
        verify(ativacoes).save(salva.capture());
        assertThat(salva.getValue().getStatus())
                .isEqualTo(StatusAtivacaoBeneficio.AGUARDANDO_MODERACAO);
        assertThat(salva.getValue().getInicioEm()).isNull();
        assertThat(salva.getValue().getFimEm()).isNull();
        assertThat(resultado.ativacoes()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("AGUARDANDO_MODERACAO");
            assertThat(item.inicioEm()).isNull();
            assertThat(item.fimEm()).isNull();
        });
    }

    @Test
    void compraDeStoriesPeloFluxoPremiumGenericoERecusadaSemPersistencia() {
        assertThatThrownBy(() -> service.comprar(
                new MinhaCompraPremiumRequest(
                        anuncio.getSlug(),
                        List.of(new MinhaCompraPremiumItemRequest("STORIES", 1))),
                "qa-story-fluxo-generico",
                authentication,
                "req-story-fluxo-generico"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Stories utiliza o fluxo proprio de publicacao");

        verify(grupos, never()).save(any());
        verify(ativacoes, never()).save(any());
        verify(auditoria, never()).save(any());
    }
    @Test
    void saldoInsuficienteNaoPersisteGrupoAtivacaoOuAuditoria() {
        BeneficioPremiumEntity topo = beneficio("ANUNCIO_TOPO", "Anuncio no topo");
        BeneficioPremiumOpcaoEntity opcao = opcao(topo, 30, 35);
        prepararItem(topo, opcao);
        when(ledger.bloquearEConsultarSaldo(usuarioId)).thenReturn(20);

        assertThatThrownBy(() -> service.comprar(
                new MinhaCompraPremiumRequest(
                        anuncio.getSlug(),
                        List.of(new MinhaCompraPremiumItemRequest("ANUNCIO_TOPO", 30))),
                "qa-compra-sem-saldo",
                authentication,
                "req-sem-saldo"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("saldo de creditos insuficiente");

        verify(grupos, never()).save(any());
        verify(ativacoes, never()).save(any());
        verify(auditoria, never()).save(any());
    }

    @Test
    void retryComMesmaChaveRetornaOperacaoSemNovoDebito() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        BeneficioPremiumEntity topo = beneficio("ANUNCIO_TOPO", "Anuncio no topo");
        BeneficioPremiumOpcaoEntity opcao = opcao(topo, 7, 10);
        String chaveGrupo = "premium-compra:" + usuarioId + ":qa-retry";
        GrupoAtivacaoBeneficioEntity grupo = GrupoAtivacaoBeneficioEntity.criarCompraComCreditos(
                UUID.randomUUID(),
                usuarioId,
                anuncio.getId(),
                agora.minusMinutes(2),
                agora.plusDays(7),
                chaveGrupo,
                agora.minusMinutes(2));
        AtivacaoBeneficioEntity ativacao = AtivacaoBeneficioEntity.criarCompraComCreditos(
                UUID.randomUUID(),
                topo.getId(),
                opcao.getId(),
                usuarioId,
                anuncio.getId(),
                grupo.getId(),
                agora.minusMinutes(2),
                agora.plusDays(7),
                10,
                chaveGrupo + ":ativacao:0",
                agora.minusMinutes(2));
        MovimentoCreditoEntity debito = MovimentoCreditoEntity.registrar(
                UUID.randomUUID(),
                usuarioId,
                TipoMovimentoCredito.SAIDA,
                DirecaoMovimentoCredito.DEBITO,
                10,
                100,
                90,
                OrigemMovimentoCredito.BENEFICIO,
                "ATIVACAO_BENEFICIO",
                ativacao.getId(),
                chaveGrupo + ":debito:0",
                usuarioId,
                "Compra de Anuncio no topo por 7 dias",
                "req-original",
                agora.minusMinutes(2));
        when(grupos.findByIdempotencyKey(chaveGrupo)).thenReturn(Optional.of(grupo));
        when(ativacoes.findByGrupoAtivacaoId(grupo.getId())).thenReturn(List.of(ativacao));
        when(beneficios.findByIdIn(any())).thenReturn(List.of(topo));
        when(opcoes.findAllById(any())).thenReturn(List.of(opcao));
        when(anuncios.findAllById(any())).thenReturn(List.of(anuncio));
        when(movimentos.findByReferenciaTipoAndReferenciaIdIn(anyString(), any()))
                .thenReturn(List.of(debito));

        var resultado = service.comprar(
                new MinhaCompraPremiumRequest(
                        anuncio.getSlug(),
                        List.of(new MinhaCompraPremiumItemRequest("ANUNCIO_TOPO", 7))),
                "qa-retry",
                authentication,
                "req-retry");

        assertThat(resultado.idempotente()).isTrue();
        assertThat(resultado.totalDebitado()).isEqualTo(10);
        assertThat(resultado.saldoPosterior()).isEqualTo(90);
        verify(grupos, never()).save(any());
        verify(ledger, never()).registrar(
                any(), any(), any(), anyInt(), anyInt(), any(), anyString(),
                any(), anyString(), any(), anyString(), anyString());
    }

    private BeneficioPremiumEntity beneficio(String codigo, String nome) {
        return BeneficioPremiumEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                codigo,
                nome,
                "Beneficio sintetico",
                EscopoBeneficioPremium.ANUNCIO,
                "ANUNCIO_TOPO".equals(codigo),
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    }

    private BeneficioPremiumOpcaoEntity opcao(
            BeneficioPremiumEntity beneficio,
            int duracaoDias,
            int custo) {
        return BeneficioPremiumOpcaoEntity.criar(
                UUID.randomUUID(),
                beneficio.getId(),
                duracaoDias,
                custo,
                true,
                duracaoDias,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    }

    private void prepararItem(
            BeneficioPremiumEntity beneficio,
            BeneficioPremiumOpcaoEntity opcao) {
        when(beneficios.findByCodigo(beneficio.getCodigo())).thenReturn(Optional.of(beneficio));
        when(opcoes.findFirstByBeneficioIdAndDuracaoDiasOrderByVersaoRegraDesc(
                beneficio.getId(),
                opcao.getDuracaoDias()))
                .thenReturn(Optional.of(opcao));
    }
}
