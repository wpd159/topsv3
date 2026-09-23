package br.com.topsdojob.v3.application.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator.DadosAtualizacao;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoValidationException;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoValidationException.Campo;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoValidationException.Regra;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.DocumentoBuscaAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.moderacao.RevisaoAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.DocumentoBuscaAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.RevisaoAnuncioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MeuAnuncioAtualizacaoService {

    private final MeusAnunciosConsultaService consultaService;
    private final KycPublicoService kycService;
    private final AnuncioRepository anuncioRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final DocumentoBuscaAnuncioRepository documentoBuscaRepository;
    private final RevisaoAnuncioRepository revisaoRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final BairroRepository bairroRepository;
    private final ObjectMapper objectMapper;
    private final AnuncioAtualizacaoCanonicaValidator validator;

    public MeuAnuncioAtualizacaoService(
            MeusAnunciosConsultaService consultaService,
            KycPublicoService kycService,
            AnuncioRepository anuncioRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            DocumentoBuscaAnuncioRepository documentoBuscaRepository,
            RevisaoAnuncioRepository revisaoRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            BairroRepository bairroRepository,
            ObjectMapper objectMapper,
            AnuncioAtualizacaoCanonicaValidator validator) {
        this.consultaService = consultaService;
        this.kycService = kycService;
        this.anuncioRepository = anuncioRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.documentoBuscaRepository = documentoBuscaRepository;
        this.revisaoRepository = revisaoRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.bairroRepository = bairroRepository;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Transactional
    public MeuAnuncioDto atualizar(
            String slug,
            MeuAnuncioAtualizacaoRequestDto request,
            Authentication authentication) {
        AnuncioEntity anuncio = consultaService.anuncioDoUsuarioParaAtualizacao(slug, authentication);
        UsuarioEntity usuario = consultaService.usuarioAutenticado(authentication);
        kycService.garantirProntoParaAnuncio(usuario.getId());
        DadosAtualizacao validado = validator.validar(request, usuario.getTelefoneNormalizado());
        if (revisaoRepository.existsByAnuncioIdAndStatusIn(
                anuncio.getId(),
                List.of(StatusRevisaoAnuncio.EM_ANALISE))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "anuncio possui revisao em analise e nao pode ser alterado agora");
        }

        EstadoEntity estado = estadoRepository.findByUfIgnoreCase(validado.uf())
                .orElseThrow(() -> badRequest(Campo.UF, "uf nao encontrada"));
        CidadeEntity cidade = cidadeRepository.findByEstadoIdAndSlug(estado.getId(), validator.slugify(validado.cidade()))
                .orElseThrow(() -> badRequest(Campo.CIDADE, "cidade nao encontrada para a uf informada"));
        BairroEntity bairro = validado.bairro() == null
                ? null
                : bairroRepository.findByCidadeIdAndSlug(cidade.getId(), validator.slugify(validado.bairro()))
                        .orElseThrow(() -> badRequest(Campo.BAIRRO,
                                "bairro nao encontrado para a cidade informada"));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        anuncio.atualizarPeloProprietario(
                validado.titulo(),
                validado.descricao(),
                validado.categoria(),
                validado.preco(),
                validado.whatsapp(),
                validado.locaisAtendimento(),
                validado.servicos(),
                validado.atendimentoExclusivamenteVirtual(),
                validado.linkConteudo(),
                agora);
        anuncioRepository.saveAndFlush(anuncio);

        AnuncioLocalizacaoEntity localizacao = localizacaoRepository.findByAnuncioId(anuncio.getId()).orElse(null);
        if (localizacao == null) {
            localizacao = AnuncioLocalizacaoEntity.criarEdicaoProprietario(
                    anuncio.getId(), estado.getId(), cidade.getId(), bairro == null ? null : bairro.getId(),
                    validado.enderecoResumido(), agora);
        } else {
            localizacao.atualizarLocalidadeProprietario(
                    estado.getId(), cidade.getId(), bairro == null ? null : bairro.getId(),
                    validado.enderecoResumido(), agora);
        }
        localizacaoRepository.save(localizacao);

        DocumentoBuscaAnuncioEntity documento = documentoBuscaRepository.findById(anuncio.getId()).orElse(null);
        if (documento == null) {
            documento = DocumentoBuscaAnuncioEntity.criarSolicitacaoLocal(
                    anuncio.getId(),
                    validator.textoBusca(validado, validado.enderecoResumido()),
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    validado.categoria(),
                    validado.preco(),
                    agora);
        } else {
            documento.atualizarAposEdicao(
                    validator.textoBusca(validado, validado.enderecoResumido()),
                    estado.getId(),
                    cidade.getId(),
                    bairro == null ? null : bairro.getId(),
                    validado.categoria(),
                    validado.preco(),
                    agora);
        }
        documentoBuscaRepository.save(documento);

        String payload = payloadRevisao(validado, estado, cidade, bairro);
        RevisaoAnuncioEntity revisaoAberta = revisaoRepository
                .findFirstByAnuncioIdAndStatusOrderByCriadoEmDesc(
                        anuncio.getId(), StatusRevisaoAnuncio.ABERTA)
                .orElse(null);
        if (revisaoAberta == null) {
            revisaoRepository.save(RevisaoAnuncioEntity.abrir(
                    UUID.randomUUID(),
                    anuncio.getId(),
                    TipoRevisaoAnuncio.EDICAO,
                    payload,
                    anuncio.getUsuarioId(),
                    agora));
        } else {
            revisaoAberta.atualizarSolicitacaoAberta(payload);
            revisaoRepository.save(revisaoAberta);
        }

        return consultaService.detalhar(anuncio.getSlug(), authentication);
    }

    private String payloadRevisao(
            DadosAtualizacao request,
            EstadoEntity estado,
            CidadeEntity cidade,
            BairroEntity bairro) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("titulo", request.titulo());
        payload.put("descricao", request.descricao());
        payload.put("categoria", request.categoria());
        payload.put("preco", request.preco());
        payload.put("uf", estado.getUf());
        payload.put("cidade", cidade.getSlug());
        payload.put("bairro", bairro == null ? null : bairro.getSlug());
        payload.put("enderecoResumidoInformado", request.enderecoResumido() != null);
        payload.put("locaisAtendimento", request.locaisAtendimento().stream().map(Enum::name).sorted().toList());
        payload.put("servicos", request.servicos().stream().map(Enum::name).sorted().toList());
        payload.put("atendimentoExclusivamenteVirtual", request.atendimentoExclusivamenteVirtual());
        payload.put("linkConteudoInformado", request.linkConteudo() != null);
        payload.put("contatoCanonicoDaConta", request.whatsapp() != null);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falha ao preparar revisao");
        }
    }

    private AnuncioAtualizacaoValidationException badRequest(Campo campo, String message) {
        return new AnuncioAtualizacaoValidationException(campo, Regra.LOCALIDADE_NAO_ENCONTRADA, message);
    }

}
