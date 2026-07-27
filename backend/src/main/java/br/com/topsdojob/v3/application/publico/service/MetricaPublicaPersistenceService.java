package br.com.topsdojob.v3.application.publico.service;

import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.MetricaPublicaWriteRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricaPublicaPersistenceService {

    private static final String ORIGEM_DESCONHECIDA = "DESCONHECIDA";
    private static final ZoneId DIA_LOCAL = ZoneId.of("America/Sao_Paulo");

    private final MetricaPublicaWriteRepository repository;

    public MetricaPublicaPersistenceService(MetricaPublicaWriteRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarVisualizacao(EventoVisualizacaoEntity evento) {
        if (repository.inserirVisualizacaoSeAusente(evento) == 0) {
            return false;
        }
        ChaveAgregado chave = chave(evento.getOrigemUf(), evento.getOrigemCidade());
        LocalDate dataReferencia = evento.getCriadoEm().atZoneSameInstant(DIA_LOCAL).toLocalDate();
        repository.incrementarVisualizacaoDiaria(
                agregadoId("visualizacao", evento.getAnuncioId(), dataReferencia, chave),
                evento.getAnuncioId(),
                dataReferencia,
                evento.getOrigemUf(),
                evento.getOrigemCidade(),
                chave.uf(),
                chave.cidade(),
                evento.getCriadoEm());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarClique(CliqueWhatsappEntity clique) {
        if (repository.inserirCliqueSeAusente(clique) == 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(clique.getPermitido())) {
            return true;
        }
        ChaveAgregado chave = chave(clique.getOrigemUf(), clique.getOrigemCidade());
        LocalDate dataReferencia = clique.getCriadoEm().atZoneSameInstant(DIA_LOCAL).toLocalDate();
        repository.incrementarCliqueDiario(
                agregadoId("clique-whatsapp", clique.getAnuncioId(), dataReferencia, chave),
                clique.getAnuncioId(),
                dataReferencia,
                clique.getOrigemUf(),
                clique.getOrigemCidade(),
                chave.uf(),
                chave.cidade(),
                clique.getCriadoEm());
        return true;
    }

    private ChaveAgregado chave(String origemUf, String origemCidade) {
        return new ChaveAgregado(
                origemUf == null || origemUf.isBlank()
                        ? ORIGEM_DESCONHECIDA
                        : origemUf.trim().toUpperCase(Locale.ROOT),
                origemCidade == null || origemCidade.isBlank()
                        ? ORIGEM_DESCONHECIDA
                        : origemCidade.trim().toUpperCase(Locale.ROOT));
    }

    private UUID agregadoId(String tipo, UUID anuncioId, LocalDate data, ChaveAgregado chave) {
        return UUID.nameUUIDFromBytes(
                ("metrica-publica-v1:agregado:" + tipo + ":" + anuncioId + ":" + data
                        + ":" + chave.uf() + ":" + chave.cidade())
                        .getBytes(StandardCharsets.UTF_8));
    }

    private record ChaveAgregado(String uf, String cidade) {
    }
}
