package br.com.topsdojob.v3.application.publico.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MidiaPublicaMapperTest {

    private MidiaPublicaUrlService urlService;
    private MidiaPublicaMapper mapper;

    @BeforeEach
    void setUp() {
        urlService = mock(MidiaPublicaUrlService.class);
        mapper = new MidiaPublicaMapper(urlService);
    }

    @Test
    void fotoLivreEhAutorizadaSemIdade() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(urlService.resolver(midia, arquivo)).thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/foto-livre", null));

        var result = mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo), false);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.visibilidadeMidia()).isEqualTo("LIVRE");
            assertThat(dto.autorizada()).isTrue();
            assertThat(dto.urlPublica()).isEqualTo("/foto-livre");
        });
    }

    @Test
    void fotoRestritaNaoEntregaOriginalSemIdade() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);

        var result = mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo(arquivoId)), false);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.autorizada()).isFalse();
            assertThat(dto.urlPublica()).isNull();
            assertThat(dto.pendenciaMidia()).isEqualTo("MIDIA_RESTRITA_IDADE");
        });
    }

    @Test
    void fotoRestritaEntregaSomenteAposIdadeValida() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity midia = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(urlService.resolver(midia, arquivo)).thenReturn(new MidiaPublicaUrlService.ResultadoUrlPublica("/foto-autorizada", null));

        assertThat(mapper.publicas(List.of(midia), Map.of(arquivoId, arquivo), true))
                .singleElement().extracting(dto -> dto.urlPublica()).isEqualTo("/foto-autorizada");
    }

    @Test
    void midiaPendenteOuRejeitadaNaoEhPublicada() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity pendente = midia(TipoAnuncioMidia.FOTO, null, arquivoId, 0);
        when(pendente.getStatus()).thenReturn(StatusAnuncioMidia.PENDENTE);
        AnuncioMidiaEntity rejeitada = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.RESTRITA_18, arquivoId, 1);
        when(rejeitada.getStatus()).thenReturn(StatusAnuncioMidia.REJEITADA);

        assertThat(mapper.publicas(List.of(pendente, rejeitada), Map.of(arquivoId, arquivo(arquivoId)), true)).isEmpty();
    }

    @Test
    void limitaFotosDepoisDeOrdenarEPreservaAsPrimeiras() {
        UUID arquivoZero = UUID.randomUUID();
        UUID arquivoUm = UUID.randomUUID();
        UUID arquivoDois = UUID.randomUUID();
        AnuncioMidiaEntity zero = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoZero, 0);
        AnuncioMidiaEntity um = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoUm, 1);
        AnuncioMidiaEntity dois = midia(TipoAnuncioMidia.FOTO, VisibilidadeMidia.LIVRE, arquivoDois, 2);
        when(urlService.resolver(any(), any())).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/segura", null));

        var resultado = mapper.publicas(
                List.of(dois, zero, um),
                Map.of(arquivoZero, arquivo(arquivoZero), arquivoUm, arquivo(arquivoUm), arquivoDois, arquivo(arquivoDois)),
                false,
                2);

        assertThat(resultado).extracting(item -> item.ordem()).containsExactly(0, 1);
    }

    @Test
    void videoExpiradoPermaneceArmazenadoMasSaiDaRespostaPublica() {
        UUID arquivoId = UUID.randomUUID();
        AnuncioMidiaEntity video = midia(TipoAnuncioMidia.VIDEO, VisibilidadeMidia.RESTRITA_18, arquivoId, 0);
        ArquivoMidiaEntity arquivo = arquivo(arquivoId);
        when(arquivo.getMimeType()).thenReturn("video/mp4");
        when(urlService.resolver(video, arquivo)).thenReturn(
                new MidiaPublicaUrlService.ResultadoUrlPublica("/video-restrito", null));

        var semBeneficio = mapper.publicas(
                List.of(video),
                Map.of(arquivoId, arquivo),
                true,
                4,
                false);
        var comBeneficio = mapper.publicas(
                List.of(video),
                Map.of(arquivoId, arquivo),
                true,
                4,
                true);

        assertThat(semBeneficio).isEmpty();
        assertThat(comBeneficio).hasSize(1);
    }

    private AnuncioMidiaEntity midia(TipoAnuncioMidia tipo, VisibilidadeMidia visibilidade, UUID arquivoId, int ordem) {
        AnuncioMidiaEntity entity = mock(AnuncioMidiaEntity.class);
        when(entity.getId()).thenReturn(UUID.randomUUID());
        when(entity.getArquivoMidiaId()).thenReturn(arquivoId);
        when(entity.getTipo()).thenReturn(tipo);
        when(entity.getFinalidade()).thenReturn(tipo == TipoAnuncioMidia.STORY ? FinalidadeAnuncioMidia.STORY : FinalidadeAnuncioMidia.GALERIA);
        when(entity.getStatus()).thenReturn(StatusAnuncioMidia.PUBLICAVEL);
        when(entity.getVisibilidadeMidia()).thenReturn(visibilidade);
        when(entity.getOrdem()).thenReturn(ordem);
        return entity;
    }

    private ArquivoMidiaEntity arquivo(UUID id) {
        ArquivoMidiaEntity entity = mock(ArquivoMidiaEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getStatusArquivo()).thenReturn(StatusArquivoMidia.VALIDADO);
        when(entity.getMimeType()).thenReturn("image/webp");
        return entity;
    }
}
