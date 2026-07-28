package br.com.topsdojob.v3.application.admin.dashboard.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AdminDashboardHojeDto(
        LocalDate dataReferencia,
        String fusoHorario,
        long visualizacoes,
        long cliquesWhatsapp,
        long beneficiosPremiumVigentes,
        OffsetDateTime calculadoEm) {
}
