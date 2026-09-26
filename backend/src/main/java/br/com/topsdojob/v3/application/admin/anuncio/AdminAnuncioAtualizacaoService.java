package br.com.topsdojob.v3.application.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioDetalhadoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator.DadosAtualizacao;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.DocumentoBuscaAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.auditoria.AuditoriaEventoEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAnuncioAtualizacaoService {

    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final DocumentoBuscaAnuncioRepository documentoBuscaRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final AuditoriaEventoRepository auditoriaRepository;
    private final AdminAnuncioDetalhadoConsultaService consultaService;
    private final AnuncioAtualizacaoCanonicaValidator validator;
    private final ObjectMapper objectMapper;
    private final ArquivoPublicidadeRegistroService arquivoPublicidade;

    public AdminAnuncioAtualizacaoService(
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            DocumentoBuscaAnuncioRepository documentoBuscaRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            AuditoriaEventoRepository auditoriaRepository,
            AdminAnuncioDetalhadoConsultaService consultaService,
            AnuncioAtualizacaoCanonicaValidator validator,
            ObjectMapper objectMapper,
            ArquivoPublicidadeRegistroService arquivoPublicidade) {
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.documentoBuscaRepository = documentoBuscaRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.consultaService = consultaService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.arquivoPublicidade = arquivoPublicidade;
    }

    @Transactional
    public AdminAnuncioDetalheDto atualizar(
            UUID anuncioId,
            AdminAnuncioAtualizacaoRequest request,
            AdminUserPrincipal administrador,
            String requestId) {
        validarAtor(administrador);
        if (request == null) {
            throw badRequest("payload obrigatorio");
        }
        DadosAtualizacao validado = validator.validar(new MeuAnuncioAtualizacaoRequestDto(
                request.titulo(),
                request.descricao(),
                request.categoria(),
                request.preco(),
                request.uf(),
                request.cidade(),
                request.bairro(),
                request.enderecoResumido(),
                request.locaisAtendimento(),
                request.servicos(),
                request.atendimentoExclusivamenteVirtual(),
                null),
                request.whatsapp());
        String enderecoResumido = validado.enderecoResumido();
        AnuncioEntity anuncio = anuncioRepository.findByIdForModeration(anuncioId)
                .filter(item -> item.getRemovidoEm() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "anuncio nao encontrado"));

        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(validado.uf())
                .orElseThrow(() -> badRequest("uf nao encontrada"));
        CidadeEntity cidade = cidadeRepository
                .findByEstadoIdAndSlug(estado.getId(), validator.slugify(validado.cidade()))
                .orElseThrow(() -> badRequest("cidade nao encontrada para a uf informada"));
        BairroEntity bairro = validado.bairro() == null
                ? null
                : bairroRepository.findByCidadeIdAndSlug(cidade.getId(), validator.slugify(validado.bairro()))
                        .orElseThrow(() -> badRequest("bairro nao encontrado para a cidade informada"));

        Map<String, Object> antes = snapshot(anuncio, localizacaoRepository.findByAnuncioId(anuncioId).orElse(null));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        anuncio.atualizarAdministrativamente(
                validado.titulo(),
                validado.descricao(),
                validado.categoria(),
                validado.preco(),
                validado.whatsapp(),
                validado.locaisAtendimento(),
                validado.servicos(),
                validado.atendimentoExclusivamenteVirtual(),
                agora);
        anuncioRepository.save(anuncio);

        AnuncioLocalizacaoEntity localizacao = localizacaoRepository.findByAnuncioId(anuncioId).orElse(null);
        if (localizacao == null) {
            localizacao = AnuncioLocalizacaoEntity.criarEdicaoAdministrativa(
                    anuncioId,
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    enderecoResumido,
                    agora);
        } else {
            localizacao.atualizarLocalidadeAdministrativa(
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    enderecoResumido,
                    agora);
        }
        localizacaoRepository.save(localizacao);

        DocumentoBuscaAnuncioEntity documento = documentoBuscaRepository.findById(anuncioId).orElse(null);
        if (documento == null) {
            documento = DocumentoBuscaAnuncioEntity.criarSolicitacaoLocal(
                    anuncioId,
                    validator.textoBusca(validado, enderecoResumido),
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    validado.categoria(),
                    validado.preco(),
                    agora);
        } else {
            documento.atualizarAposEdicao(
                    validator.textoBusca(validado, enderecoResumido),
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    validado.categoria(),
                    validado.preco(),
                    agora);
        }
        documentoBuscaRepository.save(documento);

        auditoriaRepository.save(AuditoriaEventoEntity.registrar(
                UUID.randomUUID(),
                administrador.usuarioId(),
                "ANUNCIO_EDICAO_ADMINISTRATIVA",
                "ANUNCIO",
                anuncioId,
                json(antes),
                json(snapshot(anuncio, localizacao)),
                requestId,
                agora));
        arquivoPublicidade.registrarEstado(anuncioId, "ANUNCIO_EDITADO_ADMINISTRATIVAMENTE", requestId, agora);
        return consultaService.detalhar(anuncioId, false);
    }

    private Map<String, Object> snapshot(AnuncioEntity anuncio, AnuncioLocalizacaoEntity localizacao) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("titulo", anuncio.getTitulo());
        result.put("descricao", anuncio.getDescricao());
        result.put("categoria", anuncio.getCategoria());
        result.put("preco", anuncio.getPreco());
        result.put("contatoConfigurado", anuncio.getWhatsappNormalizado() != null);
        result.put("servicos", anuncio.getServicos().stream().map(Enum::name).sorted().toList());
        result.put("atendimentoExclusivamenteVirtual", anuncio.isAtendimentoExclusivamenteVirtual());
        result.put("locaisAtendimento", anuncio.getLocaisAtendimento().stream().map(Enum::name).sorted().toList());
        result.put("estadoId", localizacao == null ? null : localizacao.getEstadoId());
        result.put("cidadeId", localizacao == null ? null : localizacao.getCidadeId());
        result.put("bairroId", localizacao == null ? null : localizacao.getBairroId());
        result.put("enderecoResumido", localizacao == null ? null : localizacao.getEnderecoResumido());
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("falha ao serializar auditoria administrativa", exception);
        }
    }

    private void validarAtor(AdminUserPrincipal administrador) {
        if (administrador == null || administrador.usuarioId() == null || !administrador.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sessao administrativa obrigatoria");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
