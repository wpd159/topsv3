package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import org.springframework.stereotype.Service;

@Service
public class StoryFalhaTecnicaService {

  private final AnuncioMidiaRepository midiaRepository;
  private final ArquivoMidiaRepository arquivoRepository;

  public StoryFalhaTecnicaService(
      AnuncioMidiaRepository midiaRepository,
      ArquivoMidiaRepository arquivoRepository) {
    this.midiaRepository = midiaRepository;
    this.arquivoRepository = arquivoRepository;
  }

  public boolean comprovada(StoryAnuncioEntity story) {
    if (story == null || story.getModoConteudoEfetivo() != ModoConteudoStory.MIDIA_UPLOAD) {
      return false;
    }
    if (story.getArquivoMidiaId() != null) {
      return arquivoRepository.findById(story.getArquivoMidiaId())
          .map(this::arquivoInvalido)
          .orElse(true);
    }
    if (story.getAnuncioMidiaId() == null) {
      return true;
    }
    AnuncioMidiaEntity midia = midiaRepository.findById(story.getAnuncioMidiaId()).orElse(null);
    if (midia == null || midia.getStatus() != StatusAnuncioMidia.PUBLICAVEL) {
      return true;
    }
    return arquivoRepository.findById(midia.getArquivoMidiaId())
        .map(this::arquivoInvalido)
        .orElse(true);
  }

  private boolean arquivoInvalido(ArquivoMidiaEntity arquivo) {
    return arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO;
  }
}
