package br.com.topsdojob.v3.application.publico.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.MetricaPublicaWriteRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DispositivoMetrica;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetricaPublicaPersistenceServiceTest {

    @Test
    void insereVisualizacaoEIncrementaAgregadoNoDiaLocalUmaVez() {
        MetricaPublicaWriteRepository repository = mock(MetricaPublicaWriteRepository.class);
        EventoVisualizacaoEntity evento = visualizacao();
        when(repository.inserirVisualizacaoSeAusente(evento)).thenReturn(1);
        MetricaPublicaPersistenceService service = new MetricaPublicaPersistenceService(repository);

        service.registrarVisualizacao(evento);

        verify(repository).incrementarVisualizacaoDiaria(
                any(UUID.class),
                eq(evento.getAnuncioId()),
                eq(LocalDate.of(2026, 7, 25)),
                eq("SP"),
                eq("Sao Paulo"),
                eq("SP"),
                eq("SAO PAULO"),
                eq(evento.getCriadoEm()));
    }

    @Test
    void retryDaVisualizacaoNaoIncrementaAgregadoNovamente() {
        MetricaPublicaWriteRepository repository = mock(MetricaPublicaWriteRepository.class);
        EventoVisualizacaoEntity evento = visualizacao();
        when(repository.inserirVisualizacaoSeAusente(evento)).thenReturn(0);
        MetricaPublicaPersistenceService service = new MetricaPublicaPersistenceService(repository);

        service.registrarVisualizacao(evento);

        verify(repository, never()).incrementarVisualizacaoDiaria(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cliquePermitidoIncrementaAgregadoMasTentativaBloqueadaNao() {
        MetricaPublicaWriteRepository repository = mock(MetricaPublicaWriteRepository.class);
        CliqueWhatsappEntity permitido = clique(true);
        CliqueWhatsappEntity bloqueado = clique(false);
        when(repository.inserirCliqueSeAusente(permitido)).thenReturn(1);
        when(repository.inserirCliqueSeAusente(bloqueado)).thenReturn(1);
        MetricaPublicaPersistenceService service = new MetricaPublicaPersistenceService(repository);

        service.registrarClique(permitido);
        service.registrarClique(bloqueado);

        verify(repository).incrementarCliqueDiario(
                any(UUID.class),
                eq(permitido.getAnuncioId()),
                eq(LocalDate.of(2026, 7, 25)),
                eq("SP"),
                eq("Sao Paulo"),
                eq("SP"),
                eq("SAO PAULO"),
                eq(permitido.getCriadoEm()));
        verify(repository, times(1)).incrementarCliqueDiario(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private EventoVisualizacaoEntity visualizacao() {
        return EventoVisualizacaoEntity.registrar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "visitante",
                "ip",
                "ua",
                "referer",
                "BR",
                "SP",
                "Sao Paulo",
                DispositivoMetrica.MOBILE,
                "request-view",
                OffsetDateTime.parse("2026-07-26T02:30:00Z"));
    }

    private CliqueWhatsappEntity clique(boolean permitido) {
        return CliqueWhatsappEntity.registrar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "visitante",
                "ip",
                "ua",
                "BR",
                "SP",
                "Sao Paulo",
                DispositivoMetrica.MOBILE,
                permitido,
                permitido ? null : "bloqueado",
                "request-click",
                OffsetDateTime.parse("2026-07-26T02:30:00Z"));
    }
}
