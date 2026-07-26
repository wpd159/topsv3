package br.com.topsdojob.v3.application.admin.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.metrica.MetricaTipos.ResultadoVerificacaoEtaria;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVerificacaoEtariaEntity;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class AdminVerificacaoEtariaConsultaServiceTest {

  @Test
  void listaSomenteMetadadosMinimizados() {
    EventoVerificacaoEtariaRepository repository = mock(EventoVerificacaoEtariaRepository.class);
    EventoVerificacaoEtariaEntity evento = EventoVerificacaoEtariaEntity.registrar(
        ResultadoVerificacaoEtaria.PERMITIDO,
        "hash-ip",
        "hash-ua",
        "req-age",
        OffsetDateTime.now(ZoneOffset.UTC));
    when(repository.findAllByOrderByCriadoEmDesc(Pageable.ofSize(50)))
        .thenReturn(new PageImpl<>(java.util.List.of(evento)));

    var resultado = new AdminVerificacaoEtariaConsultaService(repository).listar(50);

    assertThat(resultado).singleElement().satisfies(item -> {
      assertThat(item.resultado()).isEqualTo("PERMITIDO");
      assertThat(item.requestId()).isEqualTo("req-age");
      assertThat(item.toString()).doesNotContain("hash-ip", "hash-ua");
    });
  }
}
