package br.com.topsdojob.v3.persistence.repository;

import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoVerificacaoEtariaRepository
    extends JpaRepository<EventoVerificacaoEtariaEntity, UUID> {

  Page<EventoVerificacaoEtariaEntity> findAllByOrderByCriadoEmDesc(Pageable pageable);

  long countByIpHashAndResultadoAndCriadoEmAfter(
      String ipHash,
      ResultadoVerificacaoEtaria resultado,
      OffsetDateTime criadoEm);

  List<EventoVerificacaoEtariaEntity>
      findTop200BySessionHashAndCriadoEmAfterOrderByCriadoEmAsc(
          String sessionHash,
          OffsetDateTime criadoEm);
}
