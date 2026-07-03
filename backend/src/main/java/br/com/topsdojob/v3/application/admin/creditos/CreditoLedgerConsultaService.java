package br.com.topsdojob.v3.application.admin.creditos;

import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoMovimentoDto;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoPaginaDto;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.repository.MovimentoCreditoRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditoLedgerConsultaService {

    private static final int TAMANHO_MAXIMO = 100;

    private final MovimentoCreditoRepository movimentoRepository;
    private final CreditoSanitizer sanitizer;

    public CreditoLedgerConsultaService(
            MovimentoCreditoRepository movimentoRepository,
            CreditoSanitizer sanitizer) {
        this.movimentoRepository = movimentoRepository;
        this.sanitizer = sanitizer;
    }

    @Transactional(readOnly = true)
    public AdminCreditoPaginaDto<AdminCreditoMovimentoDto> consultar(UUID usuarioId, int pagina, int tamanho) {
        int paginaSegura = Math.max(0, pagina);
        int tamanhoSeguro = Math.min(TAMANHO_MAXIMO, Math.max(1, tamanho));
        Page<MovimentoCreditoEntity> page = movimentoRepository
                .findByUsuarioIdOrderByCriadoEmDesc(usuarioId, PageRequest.of(paginaSegura, tamanhoSeguro));
        return new AdminCreditoPaginaDto<>(
                page.getContent().stream().map(this::toDto).toList(),
                page.getTotalElements(),
                paginaSegura,
                tamanhoSeguro,
                true);
    }

    AdminCreditoMovimentoDto toDto(MovimentoCreditoEntity movimento) {
        return new AdminCreditoMovimentoDto(
                movimento.getId(),
                movimento.getUsuarioId(),
                nome(movimento.getTipo()),
                nome(movimento.getDirecao()),
                movimento.getQuantidade(),
                movimento.getSaldoAntes(),
                movimento.getSaldoDepois(),
                nome(movimento.getOrigem()),
                sanitizer.referenciaTipo(movimento.getReferenciaTipo()),
                movimento.getReferenciaId(),
                sanitizer.chaveOperacionalPresente(movimento.getIdempotencyKey()),
                movimento.getCriadoEm(),
                true);
    }

    private String nome(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
