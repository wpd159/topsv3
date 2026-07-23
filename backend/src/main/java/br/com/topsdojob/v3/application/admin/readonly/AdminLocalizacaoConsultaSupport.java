package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalizacaoSanitizadaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalidadeFiltroDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class AdminLocalizacaoConsultaSupport {

    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final AnuncioAtualizacaoCanonicaValidator validator;

    AdminLocalizacaoConsultaSupport(
            AnuncioLocalizacaoRepository localizacaoRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AnuncioAtualizacaoCanonicaValidator validator) {
        this.localizacaoRepository = localizacaoRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.validator = validator;
    }

    Map<UUID, AdminLocalizacaoSanitizadaDto> carregar(Collection<UUID> anuncioIds) {
        if (anuncioIds == null || anuncioIds.isEmpty()) {
            return Map.of();
        }
        List<AnuncioLocalizacaoEntity> localizacoes = localizacaoRepository.findAllById(anuncioIds);
        Set<UUID> estadoIds = new HashSet<>();
        Set<UUID> cidadeIds = new HashSet<>();
        Set<UUID> bairroIds = new HashSet<>();
        localizacoes.forEach(localizacao -> {
            if (localizacao.getEstadoId() != null) {
                estadoIds.add(localizacao.getEstadoId());
            }
            if (localizacao.getCidadeId() != null) {
                cidadeIds.add(localizacao.getCidadeId());
            }
            if (localizacao.getBairroId() != null) {
                bairroIds.add(localizacao.getBairroId());
            }
        });
        Map<UUID, EstadoEntity> estados = byId(estadoRepository.findAllById(estadoIds));
        Map<UUID, CidadeEntity> cidades = byId(cidadeRepository.findAllById(cidadeIds));
        Map<UUID, BairroEntity> bairros = byId(bairroRepository.findAllById(bairroIds));

        Map<UUID, AdminLocalizacaoSanitizadaDto> result = new HashMap<>();
        localizacoes.forEach(localizacao -> {
            EstadoEntity estado = estados.get(localizacao.getEstadoId());
            CidadeEntity cidade = cidades.get(localizacao.getCidadeId());
            BairroEntity bairro = bairros.get(localizacao.getBairroId());
            result.put(localizacao.getAnuncioId(), new AdminLocalizacaoSanitizadaDto(
                    estado == null ? null : estado.getUf(),
                    cidade == null ? null : cidade.getNome(),
                    bairro == null ? null : bairro.getNome(),
                    localizacao.getEnderecoResumido()));
        });
        return Collections.unmodifiableMap(result);
    }

    Set<UUID> filtrarAnuncioIds(String uf, String cidadeSlug, String bairroSlug) {
        boolean temUf = hasText(uf);
        boolean temCidade = hasText(cidadeSlug);
        boolean temBairro = hasText(bairroSlug);
        if (!temUf && !temCidade && !temBairro) {
            return null;
        }
        if (temCidade && !temUf) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cidade exige UF");
        }
        if (temBairro && !temCidade) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bairro exige cidade");
        }

        Set<UUID> cidadeIds = new HashSet<>();
        Set<UUID> bairroIds = new HashSet<>();
        UUID estadoId = null;
        if (temUf) {
            Optional<EstadoEntity> estado = estadoRepository.findByUfIgnoreCase(uf.trim());
            if (estado.isEmpty()) {
                return Set.of();
            }
            estadoId = estado.get().getId();
        }

        if (temCidade) {
            String cidadeCanonica = validator.slugify(cidadeSlug.trim());
            if (estadoId != null) {
                cidadeRepository.findByEstadoIdAndSlug(estadoId, cidadeCanonica).map(CidadeEntity::getId)
                        .ifPresent(cidadeIds::add);
            } else {
                cidadeRepository.findBySlug(cidadeCanonica).stream().map(CidadeEntity::getId).forEach(cidadeIds::add);
            }
            if (cidadeIds.isEmpty()) {
                return Set.of();
            }
        }

        if (temBairro) {
            String bairroCanonico = validator.slugify(bairroSlug.trim());
            if (!cidadeIds.isEmpty()) {
                for (UUID cidadeId : cidadeIds) {
                    bairroRepository.findByCidadeIdAndSlug(cidadeId, bairroCanonico).map(BairroEntity::getId)
                            .ifPresent(bairroIds::add);
                }
            } else {
                bairroRepository.findBySlug(bairroCanonico).stream().map(BairroEntity::getId).forEach(bairroIds::add);
            }
            if (bairroIds.isEmpty()) {
                return Set.of();
            }
        }

        List<AnuncioLocalizacaoEntity> localizacoes;
        if (!bairroIds.isEmpty()) {
            localizacoes = bairroIds.stream()
                    .flatMap(bairroId -> localizacaoRepository.findByBairroId(bairroId).stream())
                    .toList();
        } else if (!cidadeIds.isEmpty()) {
            localizacoes = cidadeIds.stream().flatMap(cidadeId -> localizacaoRepository.findByCidadeId(cidadeId).stream())
                    .toList();
        } else if (estadoId != null) {
            localizacoes = localizacaoRepository.findByEstadoId(estadoId);
        } else {
            localizacoes = List.of();
        }
        Set<UUID> ids = new HashSet<>();
        localizacoes.forEach(localizacao -> ids.add(localizacao.getAnuncioId()));
        return ids;
    }

    List<AdminLocalidadeFiltroDto> localidadesFiltro() {
        return localizacaoRepository.findLocalidadesDaFilaAdministrativa().stream()
                .map(item -> new AdminLocalidadeFiltroDto(
                        item.getUf(),
                        item.getEstado(),
                        item.getCidade(),
                        item.getCidadeSlug(),
                        item.getBairro(),
                        item.getBairroSlug()))
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private <T> Map<UUID, T> byId(Iterable<T> entities) {
        Map<UUID, T> result = new HashMap<>();
        for (T entity : entities) {
            UUID id = null;
            if (entity instanceof EstadoEntity estado) {
                id = estado.getId();
            } else if (entity instanceof CidadeEntity cidade) {
                id = cidade.getId();
            } else if (entity instanceof BairroEntity bairro) {
                id = bairro.getId();
            }
            if (id != null) {
                result.put(id, entity);
            }
        }
        return result;
    }
}
