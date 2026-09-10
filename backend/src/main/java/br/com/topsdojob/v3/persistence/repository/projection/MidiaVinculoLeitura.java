package br.com.topsdojob.v3.persistence.repository.projection;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Scalar read, never a managed or partially populated entity. */
public record MidiaVinculoLeitura(UUID id, UUID anuncioId, UUID arquivoMidiaId,
        TipoAnuncioMidia tipo, FinalidadeAnuncioMidia finalidade, Integer ordem,
        StatusAnuncioMidia status, VisibilidadeMidia visibilidadeMidia, OffsetDateTime atualizadoEm) {
    public static MidiaVinculoLeitura de(AnuncioMidiaEntity item) {
        return item == null ? null : new MidiaVinculoLeitura(item.getId(), item.getAnuncioId(),
                item.getArquivoMidiaId(), item.getTipo(), item.getFinalidade(), item.getOrdem(),
                item.getStatus(), item.getVisibilidadeMidia(), item.getAtualizadoEm());
    }
}
