package br.com.topsdojob.v3.application.admin.auth.dto;

import java.util.List;

public record AdminPermissionsDto(
        List<AdminPermissionDto> permissoes) {
}
