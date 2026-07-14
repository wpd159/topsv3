package br.com.topsdojob.v3.application.publico.favorito;

import br.com.topsdojob.v3.persistence.entity.anuncio.FavoritoAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.FavoritoAnuncioRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoritoGravacaoService {

    private final FavoritoAnuncioRepository favoritoRepository;

    public FavoritoGravacaoService(FavoritoAnuncioRepository favoritoRepository) {
        this.favoritoRepository = favoritoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incluirSeAusente(UUID usuarioId, UUID anuncioId, OffsetDateTime criadoEm) {
        if (favoritoRepository.existsByUsuarioIdAndAnuncioId(usuarioId, anuncioId)) {
            return;
        }
        favoritoRepository.saveAndFlush(FavoritoAnuncioEntity.criar(usuarioId, anuncioId, criadoEm));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removerSeExistente(UUID usuarioId, UUID anuncioId) {
        favoritoRepository.findByUsuarioIdAndAnuncioId(usuarioId, anuncioId)
                .ifPresent(favoritoRepository::delete);
    }
}
