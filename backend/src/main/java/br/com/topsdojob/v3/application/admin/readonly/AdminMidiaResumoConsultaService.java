package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminResumoMidiasDto;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMidiaResumoConsultaService {

    private final ArquivoMidiaRepository arquivoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final StoryAnuncioRepository storyRepository;

    public AdminMidiaResumoConsultaService(
            ArquivoMidiaRepository arquivoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            StoryAnuncioRepository storyRepository) {
        this.arquivoRepository = arquivoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.storyRepository = storyRepository;
    }

    @Transactional(readOnly = true)
    public AdminResumoMidiasDto consultar() {
        return new AdminResumoMidiasDto(
                arquivoRepository.count(),
                arquivoRepository.countByStatusArquivo(StatusArquivoMidia.PENDENTE),
                arquivoRepository.countByStatusArquivo(StatusArquivoMidia.VALIDADO),
                anuncioMidiaRepository.countByStatus(StatusAnuncioMidia.PUBLICAVEL),
                anuncioMidiaRepository.countByStatus(StatusAnuncioMidia.PENDENTE),
                anuncioMidiaRepository.countByClassificacaoConteudo(ClassificacaoConteudo.BLOQUEADO),
                storyRepository.countByStatus(StatusStoryAnuncio.PUBLICADO),
                storyRepository.countByStatus(StatusStoryAnuncio.PENDENTE));
    }
}
