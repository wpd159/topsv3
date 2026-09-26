package br.com.topsdojob.v3.application.admin.anuncio;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioDetalhadoConsultaService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioAtualizacaoRequest;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator.DadosAtualizacao;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminAnuncioAtualizacaoServiceTest {

    private final AnuncioRepository anuncioRepository = mock(AnuncioRepository.class);
    private final AnuncioLocalizacaoRepository localizacaoRepository = mock(AnuncioLocalizacaoRepository.class);
    private final DocumentoBuscaAnuncioRepository documentoBuscaRepository = mock(DocumentoBuscaAnuncioRepository.class);
    private final EstadoRepository estadoRepository = mock(EstadoRepository.class);
    private final CidadeRepository cidadeRepository = mock(CidadeRepository.class);
    private final BairroRepository bairroRepository = mock(BairroRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final AdminAnuncioDetalhadoConsultaService consultaService = mock(AdminAnuncioDetalhadoConsultaService.class);
    private final AnuncioAtualizacaoCanonicaValidator validator = mock(AnuncioAtualizacaoCanonicaValidator.class);
    private final AdminAnuncioAtualizacaoService service = new AdminAnuncioAtualizacaoService(
            anuncioRepository,
            localizacaoRepository,
            documentoBuscaRepository,
            estadoRepository,
            cidadeRepository,
            bairroRepository,
            auditoriaRepository,
            consultaService,
            validator,
            new ObjectMapper(),
            mock(ArquivoPublicidadeRegistroService.class));

    @Test
    void adminEditaCamposCanonicosSemPersonificarProprietarioNemAlterarModeracao() {
        UUID anuncioId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID estadoId = UUID.randomUUID();
        UUID cidadeId = UUID.randomUUID();
        UUID bairroId = UUID.randomUUID();
        AnuncioEntity anuncio = AnuncioEntity.criarSolicitacaoLocal(
                anuncioId,
                usuarioId,
                "slug-preservado",
                "Titulo anterior valido",
                "Descricao anterior suficientemente longa",
                "MASSAGENS",
                BigDecimal.TEN,
                "+5562999999999",
                OffsetDateTime.now().minusDays(2));
        set(anuncio, "status", StatusAnuncio.PUBLICADO);
        set(anuncio, "statusModeracao", StatusModeracaoAnuncio.APROVADO);
        AnuncioLocalizacaoEntity localizacao = entity(AnuncioLocalizacaoEntity.class);
        set(localizacao, "anuncioId", anuncioId);
        EstadoEntity estado = entity(EstadoEntity.class);
        CidadeEntity cidade = entity(CidadeEntity.class);
        BairroEntity bairro = entity(BairroEntity.class);
        set(estado, "id", estadoId);
        set(cidade, "id", cidadeId);
        set(bairro, "id", bairroId);
        DadosAtualizacao dados = new DadosAtualizacao(
                "Titulo administrativo valido",
                "Descricao administrativa completa e valida",
                "ACOMPANHANTE_FEMININA",
                new BigDecimal("250.00"),
                "GO",
                "Goiania",
                "Setor Bueno",
                "Regiao central",
                Set.of(LocalAtendimentoAnuncio.MEU_LOCAL),
                Set.of(ServicoAnuncio.VIDEOCHAMADA),
                true,
                "+5562888888888",
                null);
        AdminAnuncioAtualizacaoRequest request = new AdminAnuncioAtualizacaoRequest(
                dados.titulo(),
                dados.descricao(),
                dados.categoria(),
                dados.preco(),
                dados.uf(),
                dados.cidade(),
                dados.bairro(),
                "Regiao central",
                List.of("MEU_LOCAL"),
                List.of("VIDEOCHAMADA"),
                dados.whatsapp(),
                true);
        when(validator.validar(
                any(MeuAnuncioAtualizacaoRequestDto.class),
                org.mockito.ArgumentMatchers.eq("+5562888888888")))
                .thenReturn(dados);
        when(validator.validarEnderecoResumido("Regiao central")).thenReturn("Regiao central");
        when(validator.slugify("Goiania")).thenReturn("goiania");
        when(validator.slugify("Setor Bueno")).thenReturn("setor-bueno");
        when(validator.textoBusca(dados, "Regiao central")).thenReturn("documento de busca atualizado");
        when(anuncioRepository.findByIdForModeration(anuncioId)).thenReturn(Optional.of(anuncio));
        when(localizacaoRepository.findByAnuncioId(anuncioId)).thenReturn(Optional.of(localizacao));
        when(estadoRepository.findByUfIgnoreCase("GO")).thenReturn(Optional.of(estado));
        when(cidadeRepository.findByEstadoIdAndSlug(estadoId, "goiania")).thenReturn(Optional.of(cidade));
        when(bairroRepository.findByCidadeIdAndSlug(cidadeId, "setor-bueno")).thenReturn(Optional.of(bairro));
        when(documentoBuscaRepository.findById(anuncioId)).thenReturn(Optional.empty());
        when(auditoriaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.atualizar(anuncioId, request, admin(), "req-admin-edit");

        assertThat(anuncio.getTitulo()).isEqualTo("Titulo administrativo valido");
        assertThat(anuncio.getSlug()).isEqualTo("slug-preservado");
        assertThat(anuncio.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(anuncio.getStatusModeracao()).isEqualTo(StatusModeracaoAnuncio.APROVADO);
        assertThat(anuncio.isAtendimentoExclusivamenteVirtual()).isTrue();
        assertThat(localizacao.getEnderecoResumido()).isEqualTo("Regiao central");
        verify(anuncioRepository).findByIdForModeration(anuncioId);
        verify(documentoBuscaRepository).save(any());
        ArgumentCaptor<AuditoriaEventoEntity> auditoria = ArgumentCaptor.forClass(AuditoriaEventoEntity.class);
        verify(auditoriaRepository).save(auditoria.capture());
        assertThat(auditoria.getValue().getAcao()).isEqualTo("ANUNCIO_EDICAO_ADMINISTRATIVA");
        assertThat(auditoria.getValue().getRequestId()).isEqualTo("req-admin-edit");
        assertThat(auditoria.getValue().getDepoisJson()).contains("contatoConfigurado").doesNotContain("+5562888888888");
    }

    private AdminUserPrincipal admin() {
        return new AdminUserPrincipal(
                UUID.randomUUID(),
                "Admin",
                "admin@example.invalid",
                "hash-sintetico",
                List.of(PapelUsuario.ADMIN),
                List.of(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true);
    }
}
