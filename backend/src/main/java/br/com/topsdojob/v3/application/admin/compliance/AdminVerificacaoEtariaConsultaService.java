package br.com.topsdojob.v3.application.admin.compliance;

import br.com.topsdojob.v3.application.admin.compliance.dto.AdminVerificacaoEtariaDto;
import br.com.topsdojob.v3.persistence.repository.EventoVerificacaoEtariaRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminVerificacaoEtariaConsultaService {

  private final EventoVerificacaoEtariaRepository repository;

  public AdminVerificacaoEtariaConsultaService(EventoVerificacaoEtariaRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<AdminVerificacaoEtariaDto> listar(int limite) {
    int limiteSeguro = Math.max(1, Math.min(limite, 100));
    return repository.findAllByOrderByCriadoEmDesc(PageRequest.of(0, limiteSeguro))
        .stream()
        .map(item -> new AdminVerificacaoEtariaDto(
            item.getId(),
            item.getResultado().name(),
            item.getMetodo().name(),
            item.getRequestId(),
            item.getCriadoEm()))
        .toList();
  }
}
