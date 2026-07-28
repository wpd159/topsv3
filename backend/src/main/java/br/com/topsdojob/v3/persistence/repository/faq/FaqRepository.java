package br.com.topsdojob.v3.persistence.repository.faq;

import br.com.topsdojob.v3.persistence.entity.faq.FaqEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqRepository extends JpaRepository<FaqEntity, UUID> {

  @Query("""
      select f from FaqEntity f
      where (:status is null or f.status = :status)
        and (:categoria is null or f.categoria = :categoria)
        and (:termo is null
          or lower(f.pergunta) like lower(concat('%', :termo, '%'))
          or lower(f.resposta) like lower(concat('%', :termo, '%')))
      order by f.ordem, f.atualizadoEm desc, f.id
      """)
  List<FaqEntity> buscarAdmin(
      @Param("termo") String termo,
      @Param("status") String status,
      @Param("categoria") String categoria);

  List<FaqEntity> findAllByStatusOrderByOrdemAscAtualizadoEmDescIdAsc(String status);

  Optional<FaqEntity> findByCriadoPorUsuarioIdAndCriadoRequestId(
      UUID criadoPorUsuarioId,
      String criadoRequestId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FaqEntity f where f.id = :id")
  Optional<FaqEntity> findByIdForUpdate(@Param("id") UUID id);
}
