package br.com.topsdojob.v3.application.admin.outbox.template;

import br.com.topsdojob.v3.persistence.entity.auditoria.OutboxEventoEntity;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdminOutboxTemplateRenderer {

    private static final int SUBJECT_MAX_LENGTH = 180;
    private static final int BODY_MAX_LENGTH = 5_000;
    private static final String LINK_PLACEHOLDER = "[link-canonico]";

    private final AdminOutboxTemplateCatalog catalog;

    public AdminOutboxTemplateRenderer(AdminOutboxTemplateCatalog catalog) {
        this.catalog = catalog;
    }

    public AdminOutboxPreviewRenderizadaDto renderizar(
            OutboxEventoEntity entity,
            Map<String, Object> dadosSanitizados) {
        AdminOutboxTemplateDto template = catalog.resolver(entity.getTipoEvento());
        AdminOutboxTemplateValores values = AdminOutboxTemplateSanitizer.valores(entity, dadosSanitizados);
        String assunto = renderTemplate(template.assuntoTemplate(), values, SUBJECT_MAX_LENGTH);
        String corpo = renderTemplate(template.corpoTemplate(), values, BODY_MAX_LENGTH);
        return new AdminOutboxPreviewRenderizadaDto(
                entity.getId(),
                entity.getTipoEvento(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                assunto,
                corpo,
                template.canalPrevisto(),
                false,
                true,
                values.camposMascarados(),
                values.pendencias());
    }

    private String renderTemplate(String template, AdminOutboxTemplateValores values, int maxLength) {
        String rendered = template
                .replace("[anunciante]", values.anunciante())
                .replace("[anuncio]", values.anuncio())
                .replace("[motivo]", values.motivo())
                .replace("[acao_necessaria]", values.acaoNecessaria())
                .replace("[suporte]", values.suporte())
                .replace("[link_painel_futuro]", values.linkPainelFuturo())
                .replace("[link_edicao]", LINK_PLACEHOLDER);
        String seguro = AdminOutboxTemplateSanitizer.textoSeguro(rendered, maxLength);
        return seguro == null ? null : seguro.replace(LINK_PLACEHOLDER, values.linkEdicao());
    }
}
