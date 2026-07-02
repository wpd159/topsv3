package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusDocumentoUsuario;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoUsuarioRepository extends JpaRepository<DocumentoUsuarioEntity, UUID> {
    long countByStatus(StatusDocumentoUsuario status);

    long countByUsuarioIdAndStatusInAndRemovidoEmIsNullAndExpurgadoEmIsNull(
            UUID usuarioId,
            Collection<StatusDocumentoUsuario> statuses);

    boolean existsByArquivoMidiaIdAndRemovidoEmIsNullAndExpurgadoEmIsNull(UUID arquivoMidiaId);
}
