package br.com.topsdojob.v3.application.anuncio;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FotoElegivelAnuncioPolicy {

    public static final String MENSAGEM_SEM_FOTO_APROVADA =
            "Aprove ao menos uma foto antes de aprovar o anúncio.";
    public static final String MENSAGEM_FOTO_AGUARDANDO_DECISAO =
            "Conclua a análise de todas as fotos antes de aprovar o anúncio.";
    public static final String MENSAGEM_ULTIMA_FOTO_APROVADA =
            "O anúncio publicado precisa manter ao menos uma foto aprovada. "
                    + "Adicione e aprove outra foto antes de remover esta.";
    public static final String CODIGO_ULTIMA_FOTO_APROVADA = "ULTIMA_FOTO_APROVADA";

    private static final Set<StatusAnuncio> STATUS_PROTEGIDOS = Set.of(
            StatusAnuncio.PUBLICADO,
            StatusAnuncio.PAUSADO,
            StatusAnuncio.APROVADO);

    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final ArquivoMidiaRepository arquivoMidiaRepository;

    public FotoElegivelAnuncioPolicy(
            AnuncioMidiaRepository anuncioMidiaRepository,
            ArquivoMidiaRepository arquivoMidiaRepository) {
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.arquivoMidiaRepository = arquivoMidiaRepository;
    }

    public Totais consultarTotais(UUID anuncioId) {
        Objects.requireNonNull(anuncioId, "anuncioId");
        return new Totais(
                anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncioId).size(),
                anuncioMidiaRepository.findFotosAguardandoDecisaoIds(anuncioId).size());
    }

    public void validarParaAprovacao(UUID anuncioId) {
        bloquearMidiasEArquivos(anuncioId);
        Totais totais = consultarTotais(anuncioId);
        if (totais.fotosAguardandoDecisaoTotal() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    MENSAGEM_FOTO_AGUARDANDO_DECISAO);
        }
        if (totais.fotosAprovadasTotal() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    MENSAGEM_SEM_FOTO_APROVADA);
        }
    }

    public void validarRemocaoIndividual(AnuncioEntity anuncio, UUID midiaId) {
        Objects.requireNonNull(anuncio, "anuncio");
        Objects.requireNonNull(midiaId, "midiaId");
        if (!exigeFotoAprovada(anuncio)) {
            return;
        }

        bloquearMidiasEArquivos(anuncio.getId());
        List<UUID> fotosAprovadas =
                anuncioMidiaRepository.findFotosAprovadasElegiveisIds(anuncio.getId());
        if (fotosAprovadas.size() == 1 && fotosAprovadas.contains(midiaId)) {
            throw new UltimaFotoAprovadaException();
        }
    }

    public boolean exigeFotoAprovada(AnuncioEntity anuncio) {
        return anuncio != null
                && anuncio.getRemovidoEm() == null
                && anuncio.getStatusModeracao() == StatusModeracaoAnuncio.APROVADO
                && STATUS_PROTEGIDOS.contains(anuncio.getStatus());
    }

    private void bloquearMidiasEArquivos(UUID anuncioId) {
        Objects.requireNonNull(anuncioId, "anuncioId");
        List<UUID> arquivoIds = anuncioMidiaRepository.findByAnuncioIdForUpdate(anuncioId).stream()
                .map(AnuncioMidiaEntity::getArquivoMidiaId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (!arquivoIds.isEmpty()) {
            arquivoMidiaRepository.findByIdInForUpdate(arquivoIds);
        }
    }

    public record Totais(long fotosAprovadasTotal, long fotosAguardandoDecisaoTotal) {
    }

    public static final class UltimaFotoAprovadaException extends ResponseStatusException {
        public UltimaFotoAprovadaException() {
            super(HttpStatus.CONFLICT, MENSAGEM_ULTIMA_FOTO_APROVADA);
        }
    }
}
