package br.com.topsdojob.v3.application.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PremiumExpiracaoPolicyServiceTest {

    private final PremiumExpiracaoPolicyService service = new PremiumExpiracaoPolicyService();
    private final OffsetDateTime agora = OffsetDateTime.parse("2026-07-02T12:00:00Z");

    @Test
    void beneficioAtivoApareceComoAtivo() {
        var result = service.avaliar(
                ativacao(StatusAtivacaoBeneficio.ATIVA, agora.minusDays(1), agora.plusDays(20), null),
                beneficio("DESTAQUE"),
                null,
                agora);

        assertThat(result.status()).isEqualTo(PremiumBeneficioStatusCalculado.ATIVO);
        assertThat(result.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_ATIVO);
        assertThat(result.inconsistente()).isFalse();
    }

    @Test
    void beneficioExpiradoApareceComoExpirado() {
        var result = service.avaliar(
                ativacao(StatusAtivacaoBeneficio.EXPIRADA, agora.minusDays(10), agora.minusDays(1), null),
                beneficio("DESTAQUE"),
                null,
                agora);

        assertThat(result.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(result.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_EXPIRADO);
    }

    @Test
    void beneficioVencendoApareceNaJanela() {
        var result = service.avaliar(
                ativacao(StatusAtivacaoBeneficio.ATIVA, agora.minusDays(1), agora.plusDays(3), null),
                beneficio("FOTOS_EXTRA"),
                null,
                agora);

        assertThat(result.status()).isEqualTo(PremiumBeneficioStatusCalculado.VENCENDO);
        assertThat(result.venceEmBreve()).isTrue();
        assertThat(result.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_VENCE_EM_BREVE);
    }

    @Test
    void grupoExpiradoComBeneficioAtivoGeraInconsistencia() {
        UUID grupoId = UUID.randomUUID();
        var result = service.avaliar(
                ativacao(StatusAtivacaoBeneficio.ATIVA, agora.minusDays(10), agora.plusDays(5), grupoId),
                beneficio("ANUNCIO_TOPO"),
                grupo(grupoId, StatusGrupoAtivacaoBeneficio.EXPIRADO, agora.minusDays(10), agora.minusDays(1)),
                agora);

        assertThat(result.status()).isEqualTo(PremiumBeneficioStatusCalculado.EXPIRADO);
        assertThat(result.inconsistente()).isTrue();
        assertThat(result.codigos()).contains(PremiumConsistenciaCodigo.GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO);
    }

    @Test
    void beneficioExpiradoAntesDoGrupoGeraInconsistencia() {
        UUID grupoId = UUID.randomUUID();
        var result = service.avaliar(
                ativacao(StatusAtivacaoBeneficio.EXPIRADA, agora.minusDays(10), agora.minusDays(1), grupoId),
                beneficio("VIDEO"),
                grupo(grupoId, StatusGrupoAtivacaoBeneficio.ATIVO, agora.minusDays(10), agora.plusDays(10)),
                agora);

        assertThat(result.inconsistente()).isTrue();
        assertThat(result.codigos()).contains(PremiumConsistenciaCodigo.BENEFICIO_EXPIRADO_ANTES_DO_GRUPO);
    }

    private AtivacaoBeneficioEntity ativacao(
            StatusAtivacaoBeneficio status,
            OffsetDateTime inicio,
            OffsetDateTime fim,
            UUID grupoId) {
        AtivacaoBeneficioEntity entity = instantiate(AtivacaoBeneficioEntity.class);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "beneficioId", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "anuncioId", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "grupoAtivacaoId", grupoId);
        ReflectionTestUtils.setField(entity, "origem", OrigemBeneficio.CORTESIA);
        ReflectionTestUtils.setField(entity, "inicioEm", inicio);
        ReflectionTestUtils.setField(entity, "fimEm", fim);
        ReflectionTestUtils.setField(entity, "status", status);
        return entity;
    }

    private GrupoAtivacaoBeneficioEntity grupo(
            UUID id,
            StatusGrupoAtivacaoBeneficio status,
            OffsetDateTime inicio,
            OffsetDateTime fim) {
        GrupoAtivacaoBeneficioEntity entity = instantiate(GrupoAtivacaoBeneficioEntity.class);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "status", status);
        ReflectionTestUtils.setField(entity, "validadeInicioEm", inicio);
        ReflectionTestUtils.setField(entity, "validadeFimEm", fim);
        return entity;
    }

    private BeneficioPremiumEntity beneficio(String codigo) {
        BeneficioPremiumEntity entity = instantiate(BeneficioPremiumEntity.class);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(entity, "codigo", codigo);
        ReflectionTestUtils.setField(entity, "nome", codigo);
        return entity;
    }

    private <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("falha ao instanciar entidade de teste", exception);
        }
    }
}
