package br.com.topsdojob.v3.application.stories;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryMidiaElegibilidadeService {

    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;

    public StoryMidiaElegibilidadeService(
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository) {
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
    }

    @Transactional(readOnly = true)
    public List<MidiaElegivel> listar(UUID anuncioId) {
        if (anuncioId == null) {
            return List.of();
        }
        return listarPorAnuncios(List.of(anuncioId)).getOrDefault(anuncioId, List.of());
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<MidiaElegivel>> listarPorAnuncios(Collection<UUID> anuncioIds) {
        List<UUID> ids = anuncioIds == null ? List.of() : anuncioIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findByAnuncioIdIn(ids).stream()
                .filter(this::vinculoElegivel)
                .toList();
        if (vinculos.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(vinculos.stream()
                        .map(AnuncioMidiaEntity::getArquivoMidiaId)
                        .distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));
        Map<UUID, List<MidiaElegivel>> porAnuncio = vinculos.stream()
                .map(vinculo -> new MidiaElegivel(vinculo, arquivos.get(vinculo.getArquivoMidiaId())))
                .filter(item -> item.arquivo() != null
                        && item.arquivo().getStatusArquivo() == StatusArquivoMidia.VALIDADO)
                .collect(Collectors.groupingBy(
                        item -> item.vinculo().getAnuncioId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Comparator<MidiaElegivel> ordem = Comparator
                .comparing((MidiaElegivel item) -> item.vinculo().getOrdem(), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(item -> item.vinculo().getId());
        porAnuncio.replaceAll((ignored, itens) -> itens.stream().sorted(ordem).toList());
        return Map.copyOf(porAnuncio);
    }

    private boolean vinculoElegivel(AnuncioMidiaEntity vinculo) {
        return vinculo != null
                && vinculo.getStatus() == StatusAnuncioMidia.PUBLICAVEL
                && (vinculo.getTipo() == TipoAnuncioMidia.FOTO || vinculo.getTipo() == TipoAnuncioMidia.VIDEO);
    }

    public record MidiaElegivel(AnuncioMidiaEntity vinculo, ArquivoMidiaEntity arquivo) {
    }
}
