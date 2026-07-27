package br.com.topsdojob.v3.persistence.entity.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_conversa")
public class ChatConversaEntity {

    protected ChatConversaEntity() {
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "participante_a_id")
    private UUID participanteAId;

    @Column(name = "participante_b_id")
    private UUID participanteBId;

    @Column(name = "criado_request_id")
    private String criadoRequestId;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    @Version
    @Column(name = "versao")
    private Long versao;

    public UUID getId() {
        return id;
    }

    public UUID getParticipanteAId() {
        return participanteAId;
    }

    public UUID getParticipanteBId() {
        return participanteBId;
    }

    public String getCriadoRequestId() {
        return criadoRequestId;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public Long getVersao() {
        return versao;
    }

    public boolean contem(UUID usuarioId) {
        return participanteAId.equals(usuarioId) || participanteBId.equals(usuarioId);
    }

    public UUID outroParticipante(UUID usuarioId) {
        if (participanteAId.equals(usuarioId)) {
            return participanteBId;
        }
        if (participanteBId.equals(usuarioId)) {
            return participanteAId;
        }
        throw new IllegalArgumentException("usuario nao pertence a conversa");
    }

    public void registrarMensagem(OffsetDateTime agora) {
        this.atualizadoEm = agora;
    }
}
