package br.com.topsdojob.v3.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class ModeracaoPessimisticLockContractTest {

    @Test
    void decisoesUsamLockPessimistaNosAlvosCanonicos() throws Exception {
        assertLock(AnuncioRepository.class, "findByIdForModeration");
        assertLock(RevisaoAnuncioRepository.class, "findByIdForUpdate");
        assertLock(AnuncioMidiaRepository.class, "findByIdForUpdate");
        assertLock(ArquivoMidiaRepository.class, "findByIdForUpdate");
    }

    private void assertLock(Class<?> repository, String method) throws Exception {
        Lock annotation = repository.getMethod(method, UUID.class).getAnnotation(Lock.class);
        assertThat(annotation)
                .as("%s.%s deve bloquear decisoes concorrentes", repository.getSimpleName(), method)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
