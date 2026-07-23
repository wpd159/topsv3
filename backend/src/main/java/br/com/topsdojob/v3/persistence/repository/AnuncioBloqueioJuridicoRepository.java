package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioBloqueioJuridicoEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBloqueioJuridico;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnuncioBloqueioJuridicoRepository
    extends JpaRepository<AnuncioBloqueioJuridicoEntity, UUID> {

  Optional<AnuncioBloqueioJuridicoEntity>
      findFirstByAnuncioIdAndAnuncioDesbloqueadoEmIsNullOrderByBloqueadoEmDesc(UUID anuncioId);

  Optional<AnuncioBloqueioJuridicoEntity>
      findFirstByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNullOrderByBloqueadoEmDesc(
          UUID usuarioId,
          EscopoBloqueioJuridico escopo);

  boolean existsByUsuarioIdAndEscopoAndUsuarioDesbloqueadoEmIsNull(
      UUID usuarioId,
      EscopoBloqueioJuridico escopo);

  List<AnuncioBloqueioJuridicoEntity> findByAnuncioIdOrderByBloqueadoEmDesc(UUID anuncioId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select bloqueio from AnuncioBloqueioJuridicoEntity bloqueio
      where bloqueio.anuncioId = :anuncioId
        and bloqueio.anuncioDesbloqueadoEm is null
      order by bloqueio.bloqueadoEm desc
      """)
  Optional<AnuncioBloqueioJuridicoEntity> findAtivoPorAnuncioForUpdate(
      @Param("anuncioId") UUID anuncioId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select bloqueio from AnuncioBloqueioJuridicoEntity bloqueio
      where bloqueio.usuarioId = :usuarioId
        and bloqueio.escopo = :escopo
        and bloqueio.usuarioDesbloqueadoEm is null
      order by bloqueio.bloqueadoEm desc
      """)
  Optional<AnuncioBloqueioJuridicoEntity> findAtivoPorUsuarioForUpdate(
      @Param("usuarioId") UUID usuarioId,
      @Param("escopo") EscopoBloqueioJuridico escopo);
}
