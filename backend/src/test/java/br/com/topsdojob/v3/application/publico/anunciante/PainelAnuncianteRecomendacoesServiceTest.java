package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAcoesDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioBeneficioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioLocalizacaoDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioMidiaDto;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class PainelAnuncianteRecomendacoesServiceTest {

    private static final UUID USUARIO_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ANUNCIO_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Test
    void calculaSomenteCondicoesReaisDosAnunciosDaSessao() {
        MeusAnunciosConsultaService consulta = mock(MeusAnunciosConsultaService.class);
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consulta.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(consulta.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(
                anuncio("PUBLICADO", "curta", null, List.of(foto(1)), List.of())));

        var resposta = new PainelAnuncianteRecomendacoesService(consulta).consultar(authentication);

        assertThat(resposta.habilitado()).isTrue();
        assertThat(resposta.itens()).extracting(item -> item.tipo())
                .containsExactly("PERFIL", "FOTOS", "VIDEO", "IMPULSIONAMENTO");
        assertThat(resposta.itens()).allSatisfy(item -> {
            assertThat(item.anuncioId()).isEqualTo(ANUNCIO_ID);
            assertThat(item.acaoHref()).startsWith("/meus-anuncios/anuncio-qa/");
        });
        verify(consulta).listarDoUsuario(USUARIO_ID);
    }

    @Test
    void respostaVaziaEhLegitimaQuandoNenhumaCondicaoSeAplica() {
        MeusAnunciosConsultaService consulta = mock(MeusAnunciosConsultaService.class);
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consulta.usuarioAutenticado(authentication)).thenReturn(usuario);
        List<MeuAnuncioMidiaDto> fotos = IntStream.rangeClosed(1, 8).mapToObj(this::foto).toList();
        when(consulta.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(anuncio(
                "PUBLICADO",
                "x".repeat(220),
                new MeuAnuncioLocalizacaoDto("GO", "Goiania", "goiania", "Centro", "centro", null),
                fotos,
                List.of(beneficio("VIDEO_1"), beneficio("CARROSSEL_FOTOS"), beneficio("ANUNCIO_TOPO")))));

        var resposta = new PainelAnuncianteRecomendacoesService(consulta).consultar(authentication);

        assertThat(resposta.habilitado()).isTrue();
        assertThat(resposta.itens()).isEmpty();
    }

    @Test
    void anuncioBloqueadoNaoRecebeAcaoImpossivel() {
        MeusAnunciosConsultaService consulta = mock(MeusAnunciosConsultaService.class);
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consulta.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(consulta.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(
                anuncio("BLOQUEADO", "", null, List.of(), List.of())));

        assertThat(new PainelAnuncianteRecomendacoesService(consulta)
                .consultar(authentication).itens()).isEmpty();
    }

    @Test
    void beneficioExpiradoNaoEhTratadoComoAtivo() {
        MeusAnunciosConsultaService consulta = mock(MeusAnunciosConsultaService.class);
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consulta.usuarioAutenticado(authentication)).thenReturn(usuario);
        List<MeuAnuncioMidiaDto> fotos = IntStream.rangeClosed(1, 8).mapToObj(this::foto).toList();
        when(consulta.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(anuncio(
                "PUBLICADO",
                "x".repeat(220),
                new MeuAnuncioLocalizacaoDto("GO", "Goiania", "goiania", "Centro", "centro", null),
                fotos,
                List.of(
                        beneficio("VIDEO_1"),
                        beneficio("CARROSSEL_FOTOS"),
                        beneficio("ANUNCIO_TOPO", "EXPIRADO")))));

        var resposta = new PainelAnuncianteRecomendacoesService(consulta).consultar(authentication);

        assertThat(resposta.itens()).extracting(item -> item.tipo())
                .containsExactly("IMPULSIONAMENTO");
    }

    @Test
    void aplicaAsSeisRegrasEUsaFimEmParaPriorizarRenovacao() {
        MeusAnunciosConsultaService consulta = mock(MeusAnunciosConsultaService.class);
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consulta.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(consulta.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(anuncio(
                "PUBLICADO",
                "curta",
                null,
                List.of(foto(1), foto(2)),
                List.of(beneficio(
                        "WHATSAPP_CARD",
                        "ATIVO",
                        OffsetDateTime.now().plusHours(24))))));

        var resposta = new PainelAnuncianteRecomendacoesService(consulta).consultar(authentication);

        assertThat(resposta.itens()).hasSize(6);
        assertThat(resposta.itens()).extracting(item -> item.tipo())
                .containsExactly("RENOVACAO", "PERFIL", "FOTOS", "VIDEO", "CARROSSEL", "IMPULSIONAMENTO");
    }

    @Test
    void beneficioForaDaJanelaDeQuarentaEOitoHorasNaoRecomendaRenovacao() {
        MeusAnunciosConsultaService consulta = mock(MeusAnunciosConsultaService.class);
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(consulta.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(consulta.listarDoUsuario(USUARIO_ID)).thenReturn(List.of(anuncio(
                "PUBLICADO",
                "x".repeat(220),
                new MeuAnuncioLocalizacaoDto("GO", "Goiania", "goiania", "Centro", "centro", null),
                IntStream.rangeClosed(1, 8).mapToObj(this::foto).toList(),
                List.of(
                        beneficio("VIDEO_1"),
                        beneficio("CARROSSEL_FOTOS"),
                        beneficio("ANUNCIO_TOPO", "ATIVO", OffsetDateTime.now().plusHours(49))))));

        var resposta = new PainelAnuncianteRecomendacoesService(consulta).consultar(authentication);

        assertThat(resposta.itens()).isEmpty();
    }

    private MeuAnuncioDto anuncio(
            String status,
            String descricao,
            MeuAnuncioLocalizacaoDto localizacao,
            List<MeuAnuncioMidiaDto> midias,
            List<MeuAnuncioBeneficioDto> beneficios) {
        return new MeuAnuncioDto(
                ANUNCIO_ID,
                "anuncio-qa",
                "Anuncio QA",
                descricao,
                "ACOMPANHANTE_FEMININA",
                new BigDecimal("100.00"),
                null,
                null,
                List.of(),
                List.of(),
                false,
                status,
                "APROVADO",
                localizacao,
                null,
                midias,
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                new MeuAnuncioAcoesDto(true, false, true, false),
                VisualizacoesCanonicasDto.total(0),
                null,
                beneficios);
    }

    private MeuAnuncioMidiaDto foto(int ordem) {
        return new MeuAnuncioMidiaDto(
                UUID.nameUUIDFromBytes(("foto-" + ordem).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "FOTO",
                ordem == 1 ? "CAPA" : "GALERIA",
                ordem,
                "VALIDADO",
                "LIVRE",
                null,
                false);
    }

    private MeuAnuncioBeneficioDto beneficio(String codigo) {
        return beneficio(codigo, "ATIVO");
    }

    private MeuAnuncioBeneficioDto beneficio(String codigo, String status) {
        return beneficio(codigo, status, null);
    }

    private MeuAnuncioBeneficioDto beneficio(String codigo, String status, OffsetDateTime fimEm) {
        return new MeuAnuncioBeneficioDto(codigo, codigo, status, null, fimEm, null, null, null, "QA");
    }
}
