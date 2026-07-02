package br.com.topsdojob.v3.application.admin.readonly;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

final class AdminReadOnlyPageRequest {

    static final int MAX_SIZE = 50;

    private AdminReadOnlyPageRequest() {
    }

    static PageRequest of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));
        return PageRequest.of(safePage, safeSize, sort);
    }
}
