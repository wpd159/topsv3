package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.documento.DocumentoUsuarioAcessoEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoUsuarioAcessoRepository
    extends JpaRepository<DocumentoUsuarioAcessoEntity, UUID> {
}
