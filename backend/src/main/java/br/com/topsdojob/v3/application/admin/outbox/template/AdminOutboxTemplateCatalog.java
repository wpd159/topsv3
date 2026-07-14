package br.com.topsdojob.v3.application.admin.outbox.template;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminOutboxTemplateCatalog {

    private static final String CANAL_LOCAL_PREVIEW = "CANAL_LOCAL_PREVIEW";

    private final Map<String, AdminOutboxTemplateDto> templates;

    public AdminOutboxTemplateCatalog() {
        Map<String, AdminOutboxTemplateDto> values = new LinkedHashMap<>();
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.MODERACAO_SOLICITAR_AJUSTE,
                "MODERACAO_SOLICITAR_AJUSTE",
                CANAL_LOCAL_PREVIEW,
                "Ajuste necessario no anuncio [anuncio]",
                "Ola [anunciante]. Identificamos uma pendencia no anuncio [anuncio]. "
                        + "Motivo: [motivo]. Acao necessaria: [acao_necessaria]. "
                        + "Suporte: [suporte]. Painel futuro: [link_painel_futuro]. "
                        + "Esta e uma previa local; nenhuma comunicacao foi enviada."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.MODERACAO_REPROVADA,
                "MODERACAO_REPROVADA",
                CANAL_LOCAL_PREVIEW,
                "Revisao nao aprovada para [anuncio]",
                "Ola [anunciante]. A revisao do anuncio [anuncio] nao foi aprovada nesta previa local. "
                        + "Motivo: [motivo]. Acao necessaria: [acao_necessaria]. "
                        + "Suporte: [suporte]. Nesta previa local, nenhuma comunicacao foi enviada."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.ANUNCIO_REMETIDO_REVISAO,
                "ANUNCIO_REMETIDO_REVISAO",
                CANAL_LOCAL_PREVIEW,
                "Anuncio remetido para revisao [anuncio]",
                "Equipe local de moderacao: o anuncio [anuncio] foi remetido para nova revisao. "
                        + "Motivo: [motivo]. Acao necessaria: [acao_necessaria]. "
                        + "Painel futuro: [link_painel_futuro]. Nenhum envio externo foi executado."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.MODERACAO_MIDIA_REPROVADA,
                "MODERACAO_MIDIA_REPROVADA",
                CANAL_LOCAL_PREVIEW,
                "Midia nao aprovada no anuncio [anuncio]",
                "Ola [anunciante]. Uma midia vinculada ao anuncio [anuncio] nao foi aprovada. "
                        + "Motivo: [motivo]. Acao necessaria: [acao_necessaria]. "
                        + "Suporte: [suporte]. Nesta previa local, nenhuma comunicacao foi enviada."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.GENERICO_OUTBOX_MODERACAO,
                "GENERICO_OUTBOX_MODERACAO",
                CANAL_LOCAL_PREVIEW,
                "Comunicacao local pendente",
                "Existe uma comunicacao local pendente para [anuncio]. Motivo: [motivo]. "
                        + "Acao necessaria: [acao_necessaria]. Suporte: [suporte]. "
                        + "Nenhuma comunicacao real foi enviada."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.AUTH_CONFIRMACAO_CONTA_SOLICITADA,
                "AUTH_CONFIRMACAO_CONTA_SOLICITADA", CANAL_LOCAL_PREVIEW,
                "Confirmacao de conta", "Uma confirmacao de conta esta pendente. O codigo nao e exibido nesta previa sanitizada. Nenhum e-mail externo foi enviado."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.AUTH_CONFIRMACAO_CONTA_REENVIADA,
                "AUTH_CONFIRMACAO_CONTA_REENVIADA", CANAL_LOCAL_PREVIEW,
                "Nova confirmacao de conta", "Uma nova confirmacao de conta esta pendente. O codigo nao e exibido nesta previa sanitizada. Nenhum e-mail externo foi enviado."));
        register(values, new AdminOutboxTemplateDto(
                AdminOutboxTemplateTipo.AUTH_RECUPERACAO_SENHA_SOLICITADA,
                "AUTH_RECUPERACAO_SENHA_SOLICITADA", CANAL_LOCAL_PREVIEW,
                "Recuperacao de senha", "Uma recuperacao de senha esta pendente. O codigo nao e exibido nesta previa sanitizada. Nenhum e-mail externo foi enviado."));
        this.templates = Map.copyOf(values);
    }

    public AdminOutboxTemplateDto resolver(String tipoEvento) {
        AdminOutboxTemplateDto template = templates.get(tipoEvento);
        if (template != null) {
            return template;
        }
        return templates.get("GENERICO_OUTBOX_MODERACAO");
    }

    public Collection<AdminOutboxTemplateDto> todos() {
        return templates.values();
    }

    private void register(Map<String, AdminOutboxTemplateDto> values, AdminOutboxTemplateDto template) {
        values.put(template.tipoEvento(), template);
    }
}
