package br.com.topsdojob.v3.application.publico.mapper;

import br.com.topsdojob.v3.persistence.repository.projection.MidiaVinculoLeitura;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Position selection only: no file validity, URL, storage or eligibility decision. */
public final class SelecaoMidiasPublicas {
    private SelecaoMidiasPublicas() { }

    private static final Comparator<MidiaVinculoLeitura> ORDEM = Comparator
            .comparingInt((MidiaVinculoLeitura midia) -> prioridadeTipo(midia.tipo()))
            .thenComparing(MidiaVinculoLeitura::ordem, Comparator.nullsLast(Integer::compareTo))
            // PostgreSQL UUID ordering is unsigned and cannot replace this comparator.
            .thenComparing(MidiaVinculoLeitura::id, Comparator.nullsLast(UUID::compareTo));

    public static List<MidiaVinculoLeitura> selecionar(
            List<MidiaVinculoLeitura> vinculos, int maxFotos, boolean videoPermitido) {
        var ordenados = vinculos.stream()
                .filter(item -> item != null && item.status() == StatusAnuncioMidia.PUBLICAVEL
                        && item.tipo() != TipoAnuncioMidia.STORY
                        && item.finalidade() != FinalidadeAnuncioMidia.STORY
                        && item.visibilidadeMidia() != null)
                .filter(item -> item.tipo() != TipoAnuncioMidia.VIDEO || videoPermitido)
                .sorted(ORDEM).toList();
        var selecionados = new ArrayList<MidiaVinculoLeitura>();
        int fotos = 0;
        for (var item : ordenados) {
            if (item.tipo() != TipoAnuncioMidia.FOTO || fotos++ < Math.max(0, maxFotos)) {
                selecionados.add(item);
            }
        }
        return List.copyOf(selecionados);
    }

    private static int prioridadeTipo(TipoAnuncioMidia tipo) {
        return tipo == TipoAnuncioMidia.VIDEO ? 0 : tipo == TipoAnuncioMidia.FOTO ? 1 : 2;
    }
}
