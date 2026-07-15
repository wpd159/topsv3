package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.application.publico.dto.MidiaPublicaDto;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MidiaPublicaSeguraPolicy {

    public List<MidiaPublicaDto> paraCard(List<MidiaPublicaDto> midias) {
        return paraCard(midias, false);
    }

    public List<MidiaPublicaDto> paraCard(List<MidiaPublicaDto> midias, boolean carrosselAtivo) {
        if (midias == null || midias.isEmpty()) {
            return List.of();
        }
        List<MidiaPublicaDto> fotos = midias.stream()
                .filter(midia -> "FOTO".equals(midia.tipo()))
                .sorted(Comparator
                        .comparingInt(this::prioridade)
                        .thenComparing(MidiaPublicaDto::ordem, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (carrosselAtivo) {
            return fotos;
        }
        return fotos.isEmpty() ? List.of() : List.of(fotos.get(0));
    }

    private int prioridade(MidiaPublicaDto midia) {
        if ("LIVRE".equals(midia.visibilidadeMidia()) && midia.autorizada()) {
            return 0;
        }
        return 1;
    }
}
