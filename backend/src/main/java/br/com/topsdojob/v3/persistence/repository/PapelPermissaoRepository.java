package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.PapelPermissaoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PapelPermissaoRepository extends JpaRepository<PapelPermissaoEntity, PapelPermissaoEntity.PapelPermissaoId> {
  List<PapelPermissaoEntity> findByPapelIn(Collection<PapelUsuario> papeis);
}
