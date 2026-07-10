package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MidiaPublicaSeguraPolicy {

    public List<MidiaPublicaDto> paraCard(List<MidiaPublicaDto> midias) {
        if (midias == null || midias.isEmpty()) {
            return List.of();
        }
        return midias.stream()
                .filter(midia -> "FOTO".equals(midia.tipo()))
                .min(Comparator
                        .comparingInt(this::prioridade)
                        .thenComparing(MidiaPublicaDto::ordem, Comparator.nullsLast(Integer::compareTo)))
                .map(List::of)
                .orElseGet(List::of);
    }

    private int prioridade(MidiaPublicaDto midia) {
        if ("LIVRE".equals(midia.visibilidadeMidia()) && midia.autorizada()) {
            return 0;
        }
        return 1;
    }
}
