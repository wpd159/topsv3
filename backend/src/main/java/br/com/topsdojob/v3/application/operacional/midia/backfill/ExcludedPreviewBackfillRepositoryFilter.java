package br.com.topsdojob.v3.application.operacional.midia.backfill;

import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public final class ExcludedPreviewBackfillRepositoryFilter implements TypeFilter {

  private static final Set<String> ALLOWED_REPOSITORIES = Set.of(
      AnuncioMidiaRepository.class.getName(),
      ArquivoMidiaRepository.class.getName());

  @Override
  public boolean match(
      MetadataReader metadataReader,
      MetadataReaderFactory metadataReaderFactory) throws IOException {
    return !ALLOWED_REPOSITORIES.contains(
        metadataReader.getClassMetadata().getClassName());
  }
}
