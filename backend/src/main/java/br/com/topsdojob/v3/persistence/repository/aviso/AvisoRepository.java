package br.com.topsdojob.v3.persistence.repository.aviso;

import br.com.topsdojob.v3.persistence.entity.aviso.AvisoEntity;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvisoRepository extends JpaRepository<AvisoEntity, UUID> {

  @Query("""
      select a from AvisoEntity a
      where (cast(:status as string) is null or a.status = :status)
        and (cast(:local as string) is null or a.localExibicao = :local)
        and (cast(:termo as string) is null
          or lower(a.titulo) like lower(concat('%', cast(:termo as string), '%'))
          or lower(a.descricao) like lower(concat('%', cast(:termo as string), '%'))
          or lower(a.criadoPorNome) like lower(concat('%', cast(:termo as string), '%')))
      order by a.atualizadoEm desc, a.id
      """)
  Page<AvisoEntity> buscarAdmin(
      @Param("termo") String termo,
      @Param("status") String status,
      @Param("local") String local,
      Pageable pageable);

  @Query("""
      select a from AvisoEntity a
      where a.status = 'PUBLICADO'
        and a.localExibicao = :local
        and (a.ativoDe is null or a.ativoDe <= :agora)
        and (a.ativoAte is null or a.ativoAte > :agora)
      order by a.publicadoEm desc, a.id
      """)
  Page<AvisoEntity> buscarVigentes(
      @Param("local") String local,
      @Param("agora") OffsetDateTime agora,
      Pageable pageable);

  Optional<AvisoEntity> findByCriadoPorUsuarioIdAndCriadoRequestId(
      UUID criadoPorUsuarioId,
      String criadoRequestId);

  long countByStatus(String status);

  @Query("""
      select count(a) from AvisoEntity a
      where a.status = 'PUBLICADO'
        and (a.ativoDe is null or a.ativoDe <= :agora)
        and (a.ativoAte is null or a.ativoAte > :agora)
      """)
  long countVigentes(@Param("agora") OffsetDateTime agora);

  @Query("""
      select count(a) from AvisoEntity a
      where a.status = 'PUBLICADO' and a.ativoDe > :agora
      """)
  long countAgendados(@Param("agora") OffsetDateTime agora);

  @Query("""
      select count(a) from AvisoEntity a
      where a.status = 'PUBLICADO' and a.ativoAte <= :agora
      """)
  long countExpirados(@Param("agora") OffsetDateTime agora);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AvisoEntity a where a.id = :id")
  Optional<AvisoEntity> findByIdForUpdate(@Param("id") UUID id);
}
