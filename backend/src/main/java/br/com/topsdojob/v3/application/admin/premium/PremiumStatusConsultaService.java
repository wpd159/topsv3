package br.com.topsdojob.v3.application.admin.premium;

import static br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo.ANUNCIO_TOPO;

import br.com.topsdojob.v3.application.admin.premium.dto.AdminBeneficioAnuncioDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAnuncioStatusDto;
import br.com.topsdojob.v3.application.admin.readonly.AdminTextoSanitizer;
import br.com.topsdojob.v3.application.premium.PremiumBeneficioCodigo;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PremiumStatusConsultaService {

    private final AnuncioRepository anuncioRepository;
    private final BeneficioAnuncioConsultaService beneficioService;

    public PremiumStatusConsultaService(
            AnuncioRepository anuncioRepository,
            BeneficioAnuncioConsultaService beneficioService) {
        this.anuncioRepository = anuncioRepository;
        this.beneficioService = beneficioService;
    }

    @Transactional(readOnly = true)
    public AdminPremiumAnuncioStatusDto consultar(UUID anuncioId) {
        AnuncioEntity anuncio = anuncioRepository.findById(anuncioId)
                .filter(entity -> entity.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));
        List<AdminBeneficioAnuncioDto> beneficios = beneficioService.consultar(anuncioId);
        List<AdminBeneficioAnuncioDto> ativos = beneficios.stream()
                .filter(this::ativoOuVencendo)
                .toList();
        List<String> codigos = beneficios.stream()
                .flatMap(item -> item.codigosConsistencia().stream())
                .distinct()
                .sorted()
                .toList();
        return new AdminPremiumAnuncioStatusDto(
                anuncio.getId(),
                anuncio.getSlug(),
                AdminTextoSanitizer.resumo(anuncio.getTitulo(), 120),
                !ativos.isEmpty(),
                ativos.stream().anyMatch(item -> ANUNCIO_TOPO.equals(item.beneficioCodigo())),
                ativos.stream().anyMatch(item -> ANUNCIO_TOPO.equals(item.beneficioCodigo())),
                false,
                ativos.stream().anyMatch(item -> PremiumBeneficioCodigo.MIDIA_EXTRA.contains(item.beneficioCodigo())),
                ativos.size(),
                (int) beneficios.stream().filter(item -> "EXPIRADO".equals(item.statusCalculado())).count(),
                (int) beneficios.stream().filter(item -> "VENCENDO".equals(item.statusCalculado())).count(),
                (int) beneficios.stream().filter(AdminBeneficioAnuncioDto::inconsistente).count(),
                codigos,
                OffsetDateTime.now(),
                true,
                false,
                false,
                false);
    }

    private boolean ativoOuVencendo(AdminBeneficioAnuncioDto item) {
        return "ATIVO".equals(item.statusCalculado()) || "VENCENDO".equals(item.statusCalculado());
    }
}
