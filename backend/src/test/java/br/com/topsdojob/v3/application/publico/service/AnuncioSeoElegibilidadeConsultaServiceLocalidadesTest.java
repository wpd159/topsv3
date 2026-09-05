package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AnuncioSeoElegibilidadeConsultaServiceLocalidadesTest {
    @Test
    void paraAntesDoSegundoAnuncioMesmoSemPreviewRestrito() {
        AtomicLong clock = new AtomicLong();
        MidiaPublicaMapper mapper = mock(MidiaPublicaMapper.class);
        when(mapper.publicas(any(), any(), anyBoolean(), anyInt(), anyBoolean())).thenAnswer(call -> {
            clock.set(Duration.ofMillis(100).toNanos());
            return List.of();
        });
        var service = service(mapper);
        var orcamento = new LocalidadesConsultaOrcamento(Duration.ofMillis(100), clock::get);
        assertThatThrownBy(() -> orcamento.executar(() -> service.avaliar(anuncios(), Map.of())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(mapper, times(1)).publicas(any(), any(), anyBoolean(), anyInt(), anyBoolean());
        assertThat(LocalidadesConsultaOrcamento.atualOuNulo()).isNull();
    }

    @Test
    void demaisChamadoresSemOrcamentoMantemAvaliacaoCompleta() {
        MidiaPublicaMapper mapper = mock(MidiaPublicaMapper.class);
        when(mapper.publicas(any(), any(), anyBoolean(), anyInt(), anyBoolean())).thenReturn(List.of());
        assertThat(service(mapper).avaliar(anuncios(), Map.of())).hasSize(2);
        verify(mapper, times(2)).publicas(any(), any(), anyBoolean(), anyInt(), anyBoolean());
    }

    private AnuncioSeoElegibilidadeConsultaService service(MidiaPublicaMapper mapper) {
        var vinculos = mock(AnuncioMidiaRepository.class);
        when(vinculos.findByAnuncioIdIn(any())).thenReturn(List.of());
        var premium = mock(PremiumPublicoMapper.class);
        when(premium.flagsPorAnuncios(any())).thenReturn(Map.of());
        return new AnuncioSeoElegibilidadeConsultaService(vinculos, mock(ArquivoMidiaRepository.class),
                mapper, premium, new AnuncioSeoIndexabilidadePolicy());
    }

    private List<AnuncioEntity> anuncios() {
        AnuncioEntity primeiro = entity(AnuncioEntity.class);
        AnuncioEntity segundo = entity(AnuncioEntity.class);
        set(primeiro, "id", UUID.randomUUID());
        set(segundo, "id", UUID.randomUUID());
        return List.of(primeiro, segundo);
    }
}
