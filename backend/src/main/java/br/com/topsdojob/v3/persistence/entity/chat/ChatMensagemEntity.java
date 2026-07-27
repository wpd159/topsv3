package br.com.topsdojob.v3.persistence.entity.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_mensagem")
public class ChatMensagemEntity {

    protected ChatMensagemEntity() {
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "conversa_id")
    private UUID conversaId;

    @Column(name = "remetente_usuario_id")
    private UUID remetenteUsuarioId;

    @Column(name = "destinatario_usuario_id")
    private UUID destinatarioUsuarioId;

    @Column(name = "corpo")
    private String corpo;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "lido_em")
    private OffsetDateTime lidoEm;

    public UUID getId() {
        return id;
    }

    public UUID getConversaId() {
        return conversaId;
    }

    public UUID getRemetenteUsuarioId() {
        return remetenteUsuarioId;
    }

    public UUID getDestinatarioUsuarioId() {
        return destinatarioUsuarioId;
    }

    public String getCorpo() {
        return corpo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestId() {
        return requestId;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getLidoEm() {
        return lidoEm;
    }
}
