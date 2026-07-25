package br.com.topsdojob.v3.application.operacional.midia;

import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService;
import br.com.topsdojob.v3.application.publico.service.MidiaRestritaDerivacaoService.ResultadoGeracao;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("homologacao")
public class MidiaRestritaRegularizacaoService {

  private final AnuncioMidiaRepository anuncioMidiaRepository;
  private final ArquivoMidiaRepository arquivoMidiaRepository;
  private final MidiaRestritaDerivacaoService derivacaoService;

  public MidiaRestritaRegularizacaoService(
      AnuncioMidiaRepository anuncioMidiaRepository,
      ArquivoMidiaRepository arquivoMidiaRepository,
      MidiaRestritaDerivacaoService derivacaoService) {
    this.anuncioMidiaRepository = anuncioMidiaRepository;
    this.arquivoMidiaRepository = arquivoMidiaRepository;
    this.derivacaoService = derivacaoService;
  }

  public Resultado regularizar() {
    List<AnuncioMidiaEntity> vinculos = anuncioMidiaRepository.findFotosRestritasPublicaveis(
        TipoAnuncioMidia.FOTO,
        StatusAnuncioMidia.PUBLICAVEL,
        VisibilidadeMidia.RESTRITA_18);
    Map<UUID, ArquivoMidiaEntity> arquivos = arquivoMidiaRepository.findByIdIn(vinculos.stream()
            .map(AnuncioMidiaEntity::getArquivoMidiaId)
            .distinct()
            .toList()).stream()
        .collect(Collectors.toMap(ArquivoMidiaEntity::getId, Function.identity()));

    int elegiveis = 0;
    int criadas = 0;
    int preservadas = 0;
    for (AnuncioMidiaEntity vinculo : vinculos) {
      ArquivoMidiaEntity arquivo = arquivos.get(vinculo.getArquivoMidiaId());
      if (arquivo == null
          || arquivo.getStatusArquivo() != StatusArquivoMidia.VALIDADO
          || arquivo.getMimeType() == null
          || !arquivo.getMimeType().startsWith("image/")) {
        continue;
      }
      elegiveis++;
      ResultadoGeracao geracao = derivacaoService.garantir(arquivo);
      if (geracao.criada()) {
        criadas++;
      } else {
        preservadas++;
      }
    }
    return new Resultado(vinculos.size(), elegiveis, criadas, preservadas);
  }

  public record Resultado(int vinculos, int elegiveis, int criadas, int preservadas) {
  }
}
