package br.com.topsdojob.v3.persistence.repository.chat;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ChatConversaResumoProjection {
    UUID getId();

    String getParticipanteUsername();

    String getUltimaMensagem();

    OffsetDateTime getUltimaMensagemEm();

    long getNaoLidas();
}
