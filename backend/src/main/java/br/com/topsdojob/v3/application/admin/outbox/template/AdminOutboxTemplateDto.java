package br.com.topsdojob.v3.application.admin.outbox.template;

public record AdminOutboxTemplateDto(
        AdminOutboxTemplateTipo tipo,
        String tipoEvento,
        String canalPrevisto,
        String assuntoTemplate,
        String corpoTemplate) {
}
