package br.com.topsdojob.v3.application.admin.desempenho;

import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoOrigemDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoCliqueWhatsappDiarioEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.AgregadoVisualizacaoDiariaEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminDesempenhoOrigemService {

    private static final String DESCONHECIDA = "DESCONHECIDA";

    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final BairroRepository bairroRepository;
    private final AdminDesempenhoSanitizer sanitizer;

    public AdminDesempenhoOrigemService(
            AnuncioLocalizacaoRepository localizacaoRepository,
            BairroRepository bairroRepository,
            AdminDesempenhoSanitizer sanitizer) {
        this.localizacaoRepository = localizacaoRepository;
        this.bairroRepository = bairroRepository;
        this.sanitizer = sanitizer;
    }

    public List<AdminDesempenhoOrigemDto> consolidar(
            UUID anuncioId,
            List<AgregadoVisualizacaoDiariaEntity> visualizacoesAgregadas,
            List<AgregadoCliqueWhatsappDiarioEntity> cliquesAgregados,
            List<EventoVisualizacaoEntity> visualizacoesBrutas,
            List<CliqueWhatsappEntity> cliquesBrutos) {
        String bairro = bairroSanitizado(anuncioId);
        Map<String, OrigemAcumulada> acumuladas = new LinkedHashMap<>();
        if (!visualizacoesAgregadas.isEmpty() || !cliquesAgregados.isEmpty()) {
            visualizacoesAgregadas.forEach(item -> origem(acumuladas, item.getOrigemUf(), item.getOrigemCidade(), bairro)
                    .visualizacoes += seguro(item.getTotalVisualizacoes()));
            cliquesAgregados.forEach(item -> origem(acumuladas, item.getOrigemUf(), item.getOrigemCidade(), bairro)
                    .cliques += seguro(item.getTotalCliques()));
        } else {
            visualizacoesBrutas.forEach(item -> origem(acumuladas, item.getOrigemUf(), item.getOrigemCidade(), bairro)
                    .visualizacoes++);
            cliquesBrutos.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getPermitido()))
                    .forEach(item -> origem(acumuladas, item.getOrigemUf(), item.getOrigemCidade(), bairro).cliques++);
        }
        return acumuladas.values().stream()
                .sorted(Comparator
                        .comparingLong(OrigemAcumulada::visualizacoes).reversed()
                        .thenComparing(OrigemAcumulada::uf, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrigemAcumulada::cidade, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(item -> new AdminDesempenhoOrigemDto(
                        item.uf(),
                        item.cidade(),
                        item.bairro(),
                        item.visualizacoes(),
                        item.cliques(),
                        sanitizer.taxaCliqueView(item.cliques(), item.visualizacoes()),
                        true,
                        true))
                .toList();
    }

    private OrigemAcumulada origem(Map<String, OrigemAcumulada> acumuladas, String uf, String cidade, String bairro) {
        String ufSanitizada = sanitizer.uf(uf);
        String cidadeSanitizada = sanitizer.texto(cidade);
        String key = (ufSanitizada == null ? DESCONHECIDA : ufSanitizada)
                + "|"
                + (cidadeSanitizada == null ? DESCONHECIDA : cidadeSanitizada);
        return acumuladas.computeIfAbsent(key, ignored -> new OrigemAcumulada(ufSanitizada, cidadeSanitizada, bairro));
    }

    private String bairroSanitizado(UUID anuncioId) {
        return localizacaoRepository.findByAnuncioId(anuncioId)
                .map(AnuncioLocalizacaoEntity::getBairroId)
                .filter(Objects::nonNull)
                .flatMap(bairroRepository::findById)
                .map(BairroEntity::getNome)
                .map(sanitizer::texto)
                .orElse(null);
    }

    private long seguro(Long value) {
        return value == null || value < 0 ? 0 : value;
    }

    private static final class OrigemAcumulada {
        private final String uf;
        private final String cidade;
        private final String bairro;
        private long visualizacoes;
        private long cliques;

        private OrigemAcumulada(String uf, String cidade, String bairro) {
            this.uf = uf;
            this.cidade = cidade;
            this.bairro = bairro;
        }

        private String uf() {
            return uf;
        }

        private String cidade() {
            return cidade;
        }

        private String bairro() {
            return bairro;
        }

        private long visualizacoes() {
            return visualizacoes;
        }

        private long cliques() {
            return cliques;
        }
    }
}
