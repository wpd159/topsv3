package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.usuario.TokenSegurancaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenSegurancaRepository extends JpaRepository<TokenSegurancaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenSegurancaEntity t where t.usuarioId = :usuarioId and t.tipo = :tipo and t.consumidoEm is null order by t.criadoEm desc")
    List<TokenSegurancaEntity> findAtivosForUpdate(@Param("usuarioId") UUID usuarioId, @Param("tipo") String tipo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenSegurancaEntity t where t.usuarioId = :usuarioId and t.consumidoEm is null order by t.criadoEm")
    List<TokenSegurancaEntity> findTodosAtivosForUpdate(@Param("usuarioId") UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenSegurancaEntity> findFirstByUsuarioIdAndTipoOrderByCriadoEmDesc(
            UUID usuarioId,
            String tipo);
}
