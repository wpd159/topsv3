package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminContadorDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoAnunciosDto;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnuncioResumoConsultaService {

    private final AnuncioRepository anuncioRepository;

    public AdminAnuncioResumoConsultaService(AnuncioRepository anuncioRepository) {
        this.anuncioRepository = anuncioRepository;
    }

    @Transactional(readOnly = true)
    public AdminResumoAnunciosDto consultar() {
        long publicados = anuncioRepository.countByStatusAndRemovidoEmIsNull(StatusAnuncio.PUBLICADO);
        long pendentes = anuncioRepository.countPendentesModeracaoComProprietarioAtivo();
        long pausados = anuncioRepository.countByStatusAndRemovidoEmIsNull(StatusAnuncio.PAUSADO);
        long contato = anuncioRepository.countByWhatsappNormalizadoIsNotNullAndRemovidoEmIsNull();
        long ativos = anuncioRepository.countByStatusInAndRemovidoEmIsNull(List.of(
                StatusAnuncio.PENDENTE_REVISAO,
                StatusAnuncio.APROVADO,
                StatusAnuncio.PUBLICADO,
                StatusAnuncio.PAUSADO));

        return new AdminResumoAnunciosDto(
                ativos,
                publicados,
                pendentes,
                pausados,
                contato,
                Arrays.stream(StatusAnuncio.values())
                        .map(status -> new AdminContadorDto(
                                status.name(),
                                status.name(),
                                status == StatusAnuncio.PENDENTE_REVISAO
                                        ? pendentes
                                        : anuncioRepository.countByStatusAndRemovidoEmIsNull(status)))
                        .toList());
    }
}
