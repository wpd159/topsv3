package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoModeracaoDto;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminModeracaoResumoConsultaService {

    private final AnuncioRepository anuncioRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final DocumentoUsuarioRepository documentoRepository;

    public AdminModeracaoResumoConsultaService(
            AnuncioRepository anuncioRepository,
            RevisaoAnuncioRepository revisaoRepository,
            DocumentoUsuarioRepository documentoRepository) {
        this.anuncioRepository = anuncioRepository;
        this.revisaoRepository = revisaoRepository;
        this.documentoRepository = documentoRepository;
    }

    @Transactional(readOnly = true)
    public AdminResumoModeracaoDto consultar() {
        long documentosPendentes = documentoRepository.countByStatus(StatusDocumentoUsuario.PENDENTE)
                + documentoRepository.countByStatus(StatusDocumentoUsuario.EM_ANALISE);
        return new AdminResumoModeracaoDto(
                revisaoRepository.countByStatus(StatusRevisaoAnuncio.ABERTA),
                revisaoRepository.countByStatus(StatusRevisaoAnuncio.EM_ANALISE),
                anuncioRepository.countByStatusModeracaoAndRemovidoEmIsNull(StatusModeracaoAnuncio.PENDENTE),
                anuncioRepository.countByStatusModeracaoAndRemovidoEmIsNull(StatusModeracaoAnuncio.BLOQUEADO),
                documentosPendentes);
    }
}
