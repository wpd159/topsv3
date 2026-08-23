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
        return paraCard(midias, carrosselAtivo, false);
    }

    public List<MidiaPublicaDto> paraCard(
            List<MidiaPublicaDto> midias,
            boolean carrosselAtivo,
            boolean videoAtivo) {
        if (midias == null || midias.isEmpty()) {
            return List.of();
        }
        List<MidiaPublicaDto> ordenadas = midias.stream()
                .filter(midia -> midia != null
                        && ("FOTO".equals(midia.tipo())
                        || (videoAtivo && "VIDEO".equals(midia.tipo()))))
                .sorted(Comparator
                        .comparingInt(this::prioridadeTipo)
                        .thenComparingInt(this::prioridade)
                        .thenComparing(MidiaPublicaDto::ordem, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(MidiaPublicaDto::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (carrosselAtivo) {
            return ordenadas;
        }

        MidiaPublicaDto video = ordenadas.stream()
                .filter(midia -> "VIDEO".equals(midia.tipo()))
                .findFirst()
                .orElse(null);
        MidiaPublicaDto foto = ordenadas.stream()
                .filter(midia -> "FOTO".equals(midia.tipo()))
                .findFirst()
                .orElse(null);
        if (video == null) {
            return foto == null ? List.of() : List.of(foto);
        }
        return foto == null ? List.of(video) : List.of(video, foto);
    }

    private int prioridadeTipo(MidiaPublicaDto midia) {
        return "VIDEO".equals(midia.tipo()) ? 0 : 1;
    }

    private int prioridade(MidiaPublicaDto midia) {
        if ("LIVRE".equals(midia.visibilidadeMidia()) && midia.autorizada()) {
            return 0;
        }
        return 1;
    }
}
