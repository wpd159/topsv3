package br.com.topsdojob.v3.application.operacional.hml;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioLocalizacaoEntity;
import br.com.topsdojob.v3.persistence.entity.conteudo.CategoriaHomeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.BairroEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.CidadeEntity;
import br.com.topsdojob.v3.persistence.entity.localizacao.EstadoEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.AtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.premium.BeneficioPremiumEntity;
import br.com.topsdojob.v3.persistence.entity.premium.GrupoAtivacaoBeneficioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.BeneficioPremiumRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.CategoriaHomeRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.persistence.repository.GrupoAtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.EscopoBeneficioPremium;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.LocalAtendimentoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ServicoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusGrupoAtivacaoBeneficio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoGrupoAtivacaoBeneficio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("homologacao")
public class HmlStoriesFixtureService {

    static final String USUARIO_EMAIL = "usuario.stories.hml@example.invalid";
    static final LocalDate USUARIO_DATA_NASCIMENTO = LocalDate.of(1990, 6, 15);

    static final UUID USUARIO_ID = uuid("f1000000-0000-4000-8000-000000000001");
    private static final UUID ANUNCIO_A_ID = uuid("f1000000-0000-4000-8000-000000000101");
    private static final UUID ANUNCIO_B_ID = uuid("f1000000-0000-4000-8000-000000000102");
    private static final UUID ANUNCIO_INELEGIVEL_ID = uuid("f1000000-0000-4000-8000-000000000103");
    private static final UUID ESTADO_ID = uuid("f1000000-0000-4000-8000-000000000501");
    private static final UUID CIDADE_ID = uuid("f1000000-0000-4000-8000-000000000502");
    private static final UUID BAIRRO_ID = uuid("f1000000-0000-4000-8000-000000000503");
    private static final UUID ARQUIVO_COMPARTILHADO_ID = uuid("f1000000-0000-4000-8000-000000000201");
    private static final UUID ARQUIVO_FOTO_A_ID = uuid("f1000000-0000-4000-8000-000000000202");
    private static final UUID ARQUIVO_VIDEO_A_ID = uuid("f1000000-0000-4000-8000-000000000203");
    private static final UUID ARQUIVO_PENDENTE_ID = uuid("f1000000-0000-4000-8000-000000000204");
    private static final UUID ARQUIVO_FOTO_B_ID = uuid("f1000000-0000-4000-8000-000000000205");
    private static final UUID ARQUIVO_INELEGIVEL_ID = uuid("f1000000-0000-4000-8000-000000000206");
    private static final UUID ARQUIVO_FOTO_A_2_ID = uuid("f1000000-0000-4000-8000-000000000207");
    private static final UUID ARQUIVO_FOTO_A_3_ID = uuid("f1000000-0000-4000-8000-000000000208");
    private static final UUID ARQUIVO_RESTRITO_A_ID = uuid("f1000000-0000-4000-8000-000000000209");
    private static final UUID ARQUIVO_FOTO_B_2_ID = uuid("f1000000-0000-4000-8000-000000000210");
    private static final UUID ARQUIVO_FOTO_B_3_ID = uuid("f1000000-0000-4000-8000-000000000211");
    private static final UUID ARQUIVO_FOTO_B_4_ID = uuid("f1000000-0000-4000-8000-000000000212");
    private static final UUID MIDIA_COMPARTILHADA_ID = uuid("f1000000-0000-4000-8000-000000000301");
    private static final UUID MIDIA_FOTO_A_ID = uuid("f1000000-0000-4000-8000-000000000302");
    private static final UUID MIDIA_VIDEO_A_ID = uuid("f1000000-0000-4000-8000-000000000303");
    private static final UUID MIDIA_PENDENTE_ID = uuid("f1000000-0000-4000-8000-000000000304");
    private static final UUID MIDIA_FOTO_B_ID = uuid("f1000000-0000-4000-8000-000000000305");
    private static final UUID MIDIA_INELEGIVEL_ID = uuid("f1000000-0000-4000-8000-000000000306");
    private static final UUID MIDIA_STORY_USUARIO_ID = uuid("f1000000-0000-4000-8000-000000000307");
    private static final UUID MIDIA_FOTO_A_2_ID = uuid("f1000000-0000-4000-8000-000000000308");
    private static final UUID MIDIA_FOTO_A_3_ID = uuid("f1000000-0000-4000-8000-000000000309");
    private static final UUID MIDIA_RESTRITA_A_ID = uuid("f1000000-0000-4000-8000-000000000310");
    private static final UUID MIDIA_FOTO_B_2_ID = uuid("f1000000-0000-4000-8000-000000000311");
    private static final UUID MIDIA_FOTO_B_3_ID = uuid("f1000000-0000-4000-8000-000000000312");
    private static final UUID MIDIA_FOTO_B_4_ID = uuid("f1000000-0000-4000-8000-000000000313");
    private static final UUID STORY_USUARIO_ID = uuid("f1000000-0000-4000-8000-000000000401");
    private static final UUID BENEFICIO_OCULTAR_IDADE_ID = uuid("f3000000-0000-4000-8000-000000000001");
    private static final UUID BENEFICIO_FOTOS_EXTRA_5_ID = uuid("f3000000-0000-4000-8000-000000000002");
    private static final OffsetDateTime BENEFICIO_ATIVO_INICIO = OffsetDateTime.parse("2025-01-01T00:00:00Z");
    private static final OffsetDateTime BENEFICIO_ATIVO_FIM = OffsetDateTime.parse("2099-01-01T00:00:00Z");
    private static final OffsetDateTime BENEFICIO_EXPIRADO_INICIO = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    private static final OffsetDateTime BENEFICIO_EXPIRADO_FIM = OffsetDateTime.parse("2025-01-01T00:00:00Z");
    private static final List<CategoriaFixture> CATEGORIAS = List.of(
            categoria("f2000000-0000-4000-8000-000000000001", "ACOMPANHANTE_FEMININA", "Acompanhante feminina", "Encontre as melhores acompanhantes femininas.", "/anuncios?categoria=ACOMPANHANTE_FEMININA", "/cards/acompanhante-feminina.jpg", 1, true),
            categoria("f2000000-0000-4000-8000-000000000002", "VENDA_DE_CONTEUDO", "Sexo Virtual", "Videochamadas, conte\u00fado exclusivo e atendimento online.", "/anuncios?categoria=VENDA_DE_CONTEUDO", "/cards/casual.jpg", 2, true),
            categoria("f2000000-0000-4000-8000-000000000003", "ACOMPANHANTE_MASCULINO", "Acompanhante masculino", "Homens elegantes e discretos.", "/anuncios?categoria=ACOMPANHANTE_MASCULINO", "/cards/acompanhante-masculino.jpg", 3, true),
            categoria("f2000000-0000-4000-8000-000000000004", "TRANSEX_TRAVESTIS", "Transex e Travestis", "As mais desejadas transex e travestis.", "/anuncios?categoria=TRANSEX_TRAVESTIS", "/cards/acompanhante-trans.jpg", 4, true),
            categoria("f2000000-0000-4000-8000-000000000005", "MASSAGENS", "Massagens", "Massagistas sensuais e terap\u00eauticas.", "/anuncios?categoria=MASSAGENS", "/cards/massagem.jpg", 5, true),
            categoria("f2000000-0000-4000-8000-000000000006", "ENCONTROS_CASUAIS", "Casual e encontros", "Encontros leves e espont\u00e2neos.", "/anuncios?categoria=ENCONTROS_CASUAIS", "/cards/casual.jpg", 6, false));

    private static final String DESCRICAO_A = "Perfil ficticio de homologacao em Goiania com descricao completa para validar listagens publicas, pagina de detalhe, metadados e indexacao do sitemap sem utilizar dados reais.";
    private static final String DESCRICAO_B = "Segundo perfil ficticio de homologacao no Setor Bueno, preparado exclusivamente para validar paginacao, descoberta de localidades e renderizacao publica com conteudo seguro.";
    private static final String DESCRICAO_INELEGIVEL = "Perfil ficticio pausado e mantido apenas para confirmar que anuncios nao publicaveis ficam fora das listagens e do sitemap publico.";

    private final String appEnv;
    private final UsuarioRepository usuarioRepository;
    private final CredencialUsuarioRepository credencialRepository;
    private final PapelUsuarioRepository papelRepository;
    private final AnuncioRepository anuncioRepository;
    private final EstadoRepository estadoRepository;
    private final CidadeRepository cidadeRepository;
    private final CategoriaHomeRepository categoriaHomeRepository;
    private final BairroRepository bairroRepository;
    private final AnuncioLocalizacaoRepository localizacaoRepository;
    private final ArquivoMidiaRepository arquivoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final StoryAnuncioRepository storyRepository;
    private final BeneficioPremiumRepository beneficioRepository;
    private final GrupoAtivacaoBeneficioRepository grupoBeneficioRepository;
    private final AtivacaoBeneficioRepository ativacaoBeneficioRepository;
    private final PasswordEncoder passwordEncoder;

    public HmlStoriesFixtureService(
            @Value("${app.env:nao_configurado}") String appEnv,
            UsuarioRepository usuarioRepository,
            CredencialUsuarioRepository credencialRepository,
            PapelUsuarioRepository papelRepository,
            AnuncioRepository anuncioRepository,
            EstadoRepository estadoRepository,
            CidadeRepository cidadeRepository,
            CategoriaHomeRepository categoriaHomeRepository,
            BairroRepository bairroRepository,
            AnuncioLocalizacaoRepository localizacaoRepository,
            ArquivoMidiaRepository arquivoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            StoryAnuncioRepository storyRepository,
            BeneficioPremiumRepository beneficioRepository,
            GrupoAtivacaoBeneficioRepository grupoBeneficioRepository,
            AtivacaoBeneficioRepository ativacaoBeneficioRepository,
            PasswordEncoder passwordEncoder) {
        this.appEnv = appEnv;
        this.usuarioRepository = usuarioRepository;
        this.credencialRepository = credencialRepository;
        this.papelRepository = papelRepository;
        this.anuncioRepository = anuncioRepository;
        this.estadoRepository = estadoRepository;
        this.cidadeRepository = cidadeRepository;
        this.categoriaHomeRepository = categoriaHomeRepository;
        this.bairroRepository = bairroRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.arquivoRepository = arquivoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.storyRepository = storyRepository;
        this.beneficioRepository = beneficioRepository;
        this.grupoBeneficioRepository = grupoBeneficioRepository;
        this.ativacaoBeneficioRepository = ativacaoBeneficioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public FixtureResult provisionar() {
        validarAmbiente();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = provisionarUsuario(agora);

        int categoriasCriadas = provisionarCategorias();
        int localidadesCriadas = provisionarLocalidades(agora);

        int anunciosCriados = 0;
        anunciosCriados += sincronizarAnuncio(ANUNCIO_A_ID, usuario.getId(), "fixture-stories-hml-a", "Perfil ficticio Stories A", DESCRICAO_A, StatusAnuncio.PUBLICADO, agora);
        anunciosCriados += sincronizarAnuncio(ANUNCIO_B_ID, usuario.getId(), "fixture-stories-hml-b", "Perfil ficticio Stories B", DESCRICAO_B, StatusAnuncio.PUBLICADO, agora);
        anunciosCriados += sincronizarAnuncio(ANUNCIO_INELEGIVEL_ID, usuario.getId(), "fixture-stories-hml-inelegivel", "Perfil ficticio inelegivel", DESCRICAO_INELEGIVEL, StatusAnuncio.PAUSADO, agora);

        int localizacoesCriadas = 0;
        localizacoesCriadas += sincronizarLocalizacao(ANUNCIO_A_ID, agora);
        localizacoesCriadas += sincronizarLocalizacao(ANUNCIO_B_ID, agora);
        localizacoesCriadas += sincronizarLocalizacao(ANUNCIO_INELEGIVEL_ID, agora);
        int beneficiosCriados = sincronizarBeneficios(usuario.getId(), agora);

        List<ArquivoMidiaEntity> arquivos = List.of(
                arquivo(ARQUIVO_COMPARTILHADO_ID, "fixture/stories/compartilhada.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_A_ID, "fixture/stories/foto-a.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_VIDEO_A_ID, "fixture/stories/video-a.mp4", "video/mp4", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_PENDENTE_ID, "fixture/stories/pendente.webp", "image/webp", StatusArquivoMidia.PENDENTE, agora),
                arquivo(ARQUIVO_FOTO_B_ID, "fixture/stories/foto-b.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_INELEGIVEL_ID, "fixture/stories/inelegivel.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_A_2_ID, "fixture/stories/foto-a-2.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_A_3_ID, "fixture/stories/foto-a-3.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_RESTRITO_A_ID, "fixture/stories/restrita-a.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_B_2_ID, "fixture/stories/foto-b-2.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_B_3_ID, "fixture/stories/foto-b-3.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_B_4_ID, "fixture/stories/foto-b-4.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora));
        int arquivosCriados = salvarAusentes(arquivos);

        List<AnuncioMidiaEntity> vinculos = List.of(
                midia(MIDIA_COMPARTILHADA_ID, ANUNCIO_A_ID, ARQUIVO_COMPARTILHADO_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_FOTO_A_ID, ANUNCIO_A_ID, ARQUIVO_FOTO_A_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 1, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_FOTO_A_2_ID, ANUNCIO_A_ID, ARQUIVO_FOTO_A_2_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 2, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_FOTO_A_3_ID, ANUNCIO_A_ID, ARQUIVO_FOTO_A_3_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 3, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_RESTRITA_A_ID, ANUNCIO_A_ID, ARQUIVO_RESTRITO_A_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 4, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.RESTRITA_18, agora),
                midia(MIDIA_VIDEO_A_ID, ANUNCIO_A_ID, ARQUIVO_VIDEO_A_ID, TipoAnuncioMidia.VIDEO, FinalidadeAnuncioMidia.GALERIA, 5, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.RESTRITA_18, agora),
                midia(MIDIA_PENDENTE_ID, ANUNCIO_A_ID, ARQUIVO_PENDENTE_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 6, StatusAnuncioMidia.PENDENTE, VisibilidadeMidia.RESTRITA_18, agora),
                midia(MIDIA_FOTO_B_ID, ANUNCIO_B_ID, ARQUIVO_FOTO_B_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_FOTO_B_2_ID, ANUNCIO_B_ID, ARQUIVO_FOTO_B_2_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 1, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_FOTO_B_3_ID, ANUNCIO_B_ID, ARQUIVO_FOTO_B_3_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 2, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_FOTO_B_4_ID, ANUNCIO_B_ID, ARQUIVO_FOTO_B_4_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 3, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                midia(MIDIA_INELEGIVEL_ID, ANUNCIO_INELEGIVEL_ID, ARQUIVO_INELEGIVEL_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, 0, StatusAnuncioMidia.PUBLICAVEL, VisibilidadeMidia.LIVRE, agora),
                AnuncioMidiaEntity.criarFixtureHomologacao(
                        MIDIA_STORY_USUARIO_ID,
                        ANUNCIO_A_ID,
                        ARQUIVO_COMPARTILHADO_ID,
                        TipoAnuncioMidia.STORY,
                        FinalidadeAnuncioMidia.STORY,
                        0,
                        StatusAnuncioMidia.PUBLICAVEL,
                        VisibilidadeMidia.RESTRITA_18,
                        agora));
        int vinculosCriados = sincronizarVinculos(vinculos, agora);

        boolean storyCriado = false;
        if (!storyRepository.existsById(STORY_USUARIO_ID)) {
            storyRepository.save(StoryAnuncioEntity.criarFixtureHomologacao(
                    STORY_USUARIO_ID,
                    MIDIA_STORY_USUARIO_ID,
                    agora.minusHours(1),
                    agora.plusDays(7),
                    0,
                    usuario.getId(),
                    agora));
            storyCriado = true;
        }
        return new FixtureResult(
                categoriasCriadas,
                localidadesCriadas,
                localizacoesCriadas,
                anunciosCriados,
                arquivosCriados,
                vinculosCriados,
                beneficiosCriados,
                storyCriado);
    }

    @Transactional
    public FixtureOwnerCredentialResult provisionarCredencialProprietario(String runtimeValue) {
        validarAmbiente();
        validarCredencialRuntime(runtimeValue);

        UsuarioEntity usuario = usuarioRepository.findByEmailNormalizado(USUARIO_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "usuario proprietario da fixture ausente; execute a reconciliacao da fixture antes"));
        if (!USUARIO_ID.equals(usuario.getId())) {
            throw new IllegalStateException("identidade proprietaria da fixture diverge do ID canonico");
        }

        CredencialUsuarioEntity credencial = credencialRepository.findByUsuarioId(USUARIO_ID).orElse(null);
        if (credencial != null && passwordEncoder.matches(runtimeValue, credencial.getSenhaHash())) {
            return new FixtureOwnerCredentialResult(FixtureOwnerCredentialStatus.PRESERVADA);
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = passwordEncoder.encode(runtimeValue);
        if (credencial == null) {
            credencial = CredencialUsuarioEntity.criar(UUID.randomUUID(), USUARIO_ID, hash, agora);
            credencialRepository.save(credencial);
            return new FixtureOwnerCredentialResult(FixtureOwnerCredentialStatus.CRIADA);
        }

        credencial.atualizarHashHomologacao(hash, agora);
        credencialRepository.save(credencial);
        return new FixtureOwnerCredentialResult(FixtureOwnerCredentialStatus.ATUALIZADA);
    }

    private int provisionarCategorias() {
        int criadas = 0;
        for (CategoriaFixture categoria : CATEGORIAS) {
            CategoriaHomeEntity existente = categoriaHomeRepository.findById(categoria.id()).orElse(null);
            if (existente == null) {
                categoriaHomeRepository.save(CategoriaHomeEntity.criarFixtureHomologacao(
                        categoria.id(),
                        categoria.identificador(),
                        categoria.titulo(),
                        categoria.descricao(),
                        categoria.destino(),
                        categoria.imagemPublicaUrl(),
                        categoria.ordem(),
                        categoria.ativo()));
                criadas++;
            } else if (existente.sincronizarFixtureHomologacao(
                    categoria.identificador(),
                    categoria.titulo(),
                    categoria.descricao(),
                    categoria.destino(),
                    categoria.imagemPublicaUrl(),
                    categoria.ordem(),
                    categoria.ativo())) {
                categoriaHomeRepository.save(existente);
            }
        }
        return criadas;
    }

    private int provisionarLocalidades(OffsetDateTime agora) {
        int criadas = 0;
        if (!estadoRepository.existsById(ESTADO_ID)) {
            estadoRepository.save(EstadoEntity.criarFixtureHomologacao(
                    ESTADO_ID, "GO", "Goias", "goias", agora));
            criadas++;
        }
        if (!cidadeRepository.existsById(CIDADE_ID)) {
            cidadeRepository.save(CidadeEntity.criarFixtureHomologacao(
                    CIDADE_ID, ESTADO_ID, "Goiania", "goiania", "goiania", agora));
            criadas++;
        }
        if (!bairroRepository.existsById(BAIRRO_ID)) {
            bairroRepository.save(BairroEntity.criarFixtureHomologacao(
                    BAIRRO_ID, CIDADE_ID, "Setor Bueno", "setor bueno", "setor-bueno", agora));
            criadas++;
        }
        return criadas;
    }

    private int sincronizarLocalizacao(UUID anuncioId, OffsetDateTime agora) {
        AnuncioLocalizacaoEntity localizacao = localizacaoRepository.findByAnuncioId(anuncioId).orElse(null);
        if (localizacao == null) {
            localizacaoRepository.save(AnuncioLocalizacaoEntity.criarFixtureHomologacao(
                    anuncioId, ESTADO_ID, CIDADE_ID, BAIRRO_ID, agora));
            return 1;
        }
        localizacao.sincronizarFixtureHomologacao(ESTADO_ID, CIDADE_ID, BAIRRO_ID, agora);
        localizacaoRepository.save(localizacao);
        return 0;
    }

    private UsuarioEntity provisionarUsuario(OffsetDateTime agora) {
        UsuarioEntity usuario = usuarioRepository.findByEmailNormalizado(USUARIO_EMAIL).orElse(null);
        if (usuario == null) {
            usuario = UsuarioEntity.criarCadastroPublico(
                    USUARIO_ID,
                    "Anunciante ficticio de homologacao",
                    USUARIO_EMAIL,
                    null,
                    USUARIO_DATA_NASCIMENTO,
                    agora);
            usuarioRepository.saveAndFlush(usuario);
        } else if (!USUARIO_DATA_NASCIMENTO.equals(usuario.getDataNascimento())) {
            usuario.sincronizarDataNascimentoHomologacao(USUARIO_DATA_NASCIMENTO, agora);
            usuarioRepository.save(usuario);
        }
        CredencialUsuarioEntity credencial = credencialRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (credencial == null) {
            String hashDescartavel = passwordEncoder.encode(UUID.randomUUID().toString());
            credencial = CredencialUsuarioEntity.criar(
                    UUID.randomUUID(), usuario.getId(), hashDescartavel, agora);
            credencialRepository.save(credencial);
        }
        boolean possuiPapel = papelRepository.findByUsuarioId(usuario.getId()).stream()
                .anyMatch(item -> item.getPapel() == PapelUsuario.USUARIO);
        if (!possuiPapel) {
            papelRepository.save(PapelUsuarioEntity.criarUsuarioPublico(usuario.getId(), agora));
        }
        return usuario;
    }

    private int sincronizarBeneficios(UUID usuarioId, OffsetDateTime agora) {
        int criados = sincronizarCatalogoBeneficio(agora);
        List<GrupoFixture> grupos = List.of(
                grupo("f3000000-0000-4000-8000-000000000101", ANUNCIO_A_ID,
                        TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.COMPRA,
                        BENEFICIO_ATIVO_INICIO, BENEFICIO_ATIVO_FIM, StatusGrupoAtivacaoBeneficio.ATIVO,
                        "hml-fixture-ocultar-idade-pago"),
                grupo("f3000000-0000-4000-8000-000000000102", ANUNCIO_B_ID,
                        TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.COMPRA,
                        BENEFICIO_EXPIRADO_INICIO, BENEFICIO_EXPIRADO_FIM, StatusGrupoAtivacaoBeneficio.EXPIRADO,
                        "hml-fixture-ocultar-idade-expirado"),
                grupo("f3000000-0000-4000-8000-000000000103", ANUNCIO_B_ID,
                        TipoGrupoAtivacaoBeneficio.CORTESIA, OrigemBeneficio.CORTESIA,
                        BENEFICIO_ATIVO_INICIO, BENEFICIO_ATIVO_FIM, StatusGrupoAtivacaoBeneficio.ATIVO,
                        "hml-fixture-ocultar-idade-cortesia"),
                grupo("f3000000-0000-4000-8000-000000000104", ANUNCIO_B_ID,
                        TipoGrupoAtivacaoBeneficio.ADMIN, OrigemBeneficio.ADMIN,
                        BENEFICIO_ATIVO_INICIO, BENEFICIO_ATIVO_FIM, StatusGrupoAtivacaoBeneficio.ATIVO,
                        "hml-fixture-ocultar-idade-admin"),
                grupo("f3000000-0000-4000-8000-000000000105", ANUNCIO_B_ID,
                        TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.COMPRA,
                        BENEFICIO_ATIVO_INICIO, BENEFICIO_ATIVO_FIM, StatusGrupoAtivacaoBeneficio.ATIVO,
                        "hml-fixture-ocultar-idade-gratuito"),
                grupo("f3000000-0000-4000-8000-000000000106", ANUNCIO_A_ID,
                        TipoGrupoAtivacaoBeneficio.PACOTE, OrigemBeneficio.COMPRA,
                        BENEFICIO_ATIVO_INICIO, BENEFICIO_ATIVO_FIM, StatusGrupoAtivacaoBeneficio.ATIVO,
                        "hml-fixture-fotos-extra-5-pago"));
        for (GrupoFixture grupo : grupos) {
            GrupoAtivacaoBeneficioEntity existente = grupoBeneficioRepository.findById(grupo.id()).orElse(null);
            if (existente == null) {
                grupoBeneficioRepository.save(GrupoAtivacaoBeneficioEntity.criarFixtureHomologacao(
                        grupo.id(), grupo.tipo(), grupo.origem(), usuarioId, grupo.anuncioId(),
                        grupo.inicioEm(), grupo.fimEm(), grupo.status(), grupo.chave(), agora));
                criados++;
            } else if (existente.sincronizarFixtureHomologacao(
                    grupo.tipo(), grupo.origem(), usuarioId, grupo.anuncioId(),
                    grupo.inicioEm(), grupo.fimEm(), grupo.status(), grupo.chave(), agora)) {
                grupoBeneficioRepository.save(existente);
            }
        }

        List<AtivacaoFixture> ativacoes = List.of(
                ativacao("f3000000-0000-4000-8000-000000000201", BENEFICIO_OCULTAR_IDADE_ID, grupos.get(0),
                        StatusAtivacaoBeneficio.ATIVA, 0, new BigDecimal("49.90")),
                ativacao("f3000000-0000-4000-8000-000000000202", BENEFICIO_OCULTAR_IDADE_ID, grupos.get(1),
                        StatusAtivacaoBeneficio.EXPIRADA, 0, new BigDecimal("49.90")),
                ativacao("f3000000-0000-4000-8000-000000000203", BENEFICIO_OCULTAR_IDADE_ID, grupos.get(2),
                        StatusAtivacaoBeneficio.ATIVA, 0, BigDecimal.ZERO),
                ativacao("f3000000-0000-4000-8000-000000000204", BENEFICIO_OCULTAR_IDADE_ID, grupos.get(3),
                        StatusAtivacaoBeneficio.ATIVA, 0, BigDecimal.ZERO),
                ativacao("f3000000-0000-4000-8000-000000000205", BENEFICIO_OCULTAR_IDADE_ID, grupos.get(4),
                        StatusAtivacaoBeneficio.ATIVA, 0, BigDecimal.ZERO),
                ativacao("f3000000-0000-4000-8000-000000000206", BENEFICIO_FOTOS_EXTRA_5_ID, grupos.get(5),
                        StatusAtivacaoBeneficio.ATIVA, 0, new BigDecimal("59.90")));
        for (AtivacaoFixture ativacao : ativacoes) {
            AtivacaoBeneficioEntity existente = ativacaoBeneficioRepository.findById(ativacao.id()).orElse(null);
            if (existente == null) {
                ativacaoBeneficioRepository.save(AtivacaoBeneficioEntity.criarFixtureHomologacao(
                        ativacao.id(), ativacao.beneficioId(), usuarioId, ativacao.grupo().anuncioId(),
                        ativacao.grupo().id(), ativacao.grupo().origem(), ativacao.grupo().inicioEm(),
                        ativacao.grupo().fimEm(), ativacao.status(), ativacao.custoCreditos(),
                        ativacao.preco(), ativacao.chave(), agora));
                criados++;
            } else if (existente.sincronizarFixtureHomologacao(
                    ativacao.beneficioId(), usuarioId, ativacao.grupo().anuncioId(),
                    ativacao.grupo().id(), ativacao.grupo().origem(), ativacao.grupo().inicioEm(),
                    ativacao.grupo().fimEm(), ativacao.status(), ativacao.custoCreditos(),
                    ativacao.preco(), ativacao.chave())) {
                ativacaoBeneficioRepository.save(existente);
            }
        }
        return criados;
    }

    private int sincronizarCatalogoBeneficio(OffsetDateTime agora) {
        int criados = 0;
        criados += sincronizarCatalogoBeneficio(
                BENEFICIO_OCULTAR_IDADE_ID,
                "OCULTAR_IDADE",
                "Ocultar idade",
                "Oculta a idade no anuncio enquanto o beneficio pago estiver vigente.",
                agora);
        criados += sincronizarCatalogoBeneficio(
                BENEFICIO_FOTOS_EXTRA_5_ID,
                "FOTOS_EXTRA_5",
                "Fotos extras",
                "Amplia o limite total do anuncio para ate dez fotos enquanto estiver vigente.",
                agora);
        return criados;
    }

    private int sincronizarCatalogoBeneficio(
            UUID beneficioId,
            String codigo,
            String nome,
            String descricao,
            OffsetDateTime agora) {
        BeneficioPremiumEntity existente = beneficioRepository.findById(beneficioId).orElse(null);
        if (existente == null) {
            beneficioRepository.save(BeneficioPremiumEntity.criarFixtureHomologacao(
                    beneficioId,
                    codigo,
                    nome,
                    descricao,
                    EscopoBeneficioPremium.ANUNCIO,
                    false,
                    true,
                    agora));
            return 1;
        }
        if (existente.sincronizarFixtureHomologacao(
                codigo,
                nome,
                descricao,
                EscopoBeneficioPremium.ANUNCIO,
                false,
                true)) {
            beneficioRepository.save(existente);
        }
        return 0;
    }

    private int sincronizarAnuncio(
            UUID id,
            UUID usuarioId,
            String slug,
            String titulo,
            String descricao,
            StatusAnuncio status,
            OffsetDateTime agora) {
        AnuncioEntity anuncio = anuncioRepository.findById(id).orElse(null);
        if (anuncio == null) {
            anuncio = AnuncioEntity.criarFixtureHomologacao(
                    id,
                    usuarioId,
                    slug,
                    titulo,
                    descricao,
                    status,
                    StatusModeracaoAnuncio.APROVADO,
                    agora);
            sincronizarAtendimento(anuncio, id);
            anuncioRepository.save(anuncio);
            return 1;
        }
        anuncio.sincronizarFixtureHomologacao(
                titulo, descricao, status, StatusModeracaoAnuncio.APROVADO, agora);
        sincronizarAtendimento(anuncio, id);
        anuncioRepository.save(anuncio);
        return 0;
    }

    private void sincronizarAtendimento(AnuncioEntity anuncio, UUID id) {
        if (ANUNCIO_A_ID.equals(id)) {
            anuncio.sincronizarAtendimentoEstruturado(
                    Set.of(LocalAtendimentoAnuncio.MEU_LOCAL, LocalAtendimentoAnuncio.HOTEL_MOTEL),
                    Set.of(ServicoAnuncio.ANAL, ServicoAnuncio.ORAL));
        } else if (ANUNCIO_B_ID.equals(id)) {
            anuncio.sincronizarAtendimentoEstruturado(
                    Set.of(LocalAtendimentoAnuncio.A_COMBINAR, LocalAtendimentoAnuncio.HOTEL_MOTEL),
                    Set.of(ServicoAnuncio.MASSAGEM_EROTICA));
        } else {
            anuncio.sincronizarAtendimentoEstruturado(Set.of(), Set.of());
        }
    }

    private ArquivoMidiaEntity arquivo(
            UUID id,
            String chave,
            String mimeType,
            StatusArquivoMidia status,
            OffsetDateTime agora) {
        return ArquivoMidiaEntity.criarFixtureHomologacao(id, chave, mimeType, status, agora);
    }

    private AnuncioMidiaEntity midia(
            UUID id,
            UUID anuncioId,
            UUID arquivoId,
            TipoAnuncioMidia tipo,
            FinalidadeAnuncioMidia finalidade,
            int ordem,
            StatusAnuncioMidia status,
            VisibilidadeMidia visibilidade,
            OffsetDateTime agora) {
        return AnuncioMidiaEntity.criarFixtureHomologacao(
                id,
                anuncioId,
                arquivoId,
                tipo,
                finalidade,
                ordem,
                status,
                visibilidade,
                agora);
    }

    private int salvarAusentes(List<ArquivoMidiaEntity> arquivos) {
        int criados = 0;
        for (ArquivoMidiaEntity arquivo : arquivos) {
            if (!arquivoRepository.existsById(arquivo.getId())) {
                arquivoRepository.save(arquivo);
                criados++;
            }
        }
        return criados;
    }

    private int sincronizarVinculos(List<AnuncioMidiaEntity> vinculos, OffsetDateTime agora) {
        Set<UUID> anuncioIds = new HashSet<>();
        Set<ChaveOrdem> ordensCanonicas = new HashSet<>();
        for (AnuncioMidiaEntity vinculo : vinculos) {
            anuncioIds.add(vinculo.getAnuncioId());
            if (ocupaOrdem(vinculo)) {
                ordensCanonicas.add(chaveOrdem(vinculo, vinculo.getOrdem()));
            }
        }

        Map<ChaveOrdem, UUID> ordensOcupadas = new HashMap<>();
        for (AnuncioMidiaEntity existente : anuncioMidiaRepository.findByAnuncioIdIn(anuncioIds)) {
            if (ocupaOrdem(existente)) {
                ordensOcupadas.put(chaveOrdem(existente, existente.getOrdem()), existente.getId());
            }
        }

        int criados = 0;
        for (AnuncioMidiaEntity vinculo : vinculos) {
            AnuncioMidiaEntity existente = anuncioMidiaRepository.findById(vinculo.getId()).orElse(null);
            if (existente == null) {
                int ordemLivre = proximaOrdemLivre(vinculo, ordensOcupadas, ordensCanonicas, null);
                if (ordemLivre != vinculo.getOrdem()) {
                    vinculo.reordenar(ordemLivre, agora);
                }
                anuncioMidiaRepository.save(vinculo);
                registrarOrdem(vinculo, ordensOcupadas);
                criados++;
            } else if (existente.getTipo() != TipoAnuncioMidia.STORY) {
                boolean ocupavaOrdem = ocupaOrdem(existente);
                int ordemLivre = ocupavaOrdem
                        ? existente.getOrdem()
                        : proximaOrdemLivre(vinculo, ordensOcupadas, ordensCanonicas, existente.getId());
                boolean decisaoAlterada = existente.getStatus() != vinculo.getStatus()
                        || existente.getVisibilidadeMidia() != vinculo.getVisibilidadeMidia();
                boolean ordemAlterada = !Integer.valueOf(ordemLivre).equals(existente.getOrdem());
                if (ocupavaOrdem && (ordemAlterada || !ocupaOrdem(vinculo))) {
                    removerOrdemDoProprioVinculo(existente, ordensOcupadas);
                }
                if (decisaoAlterada) {
                    existente.aplicarDecisao(vinculo.getStatus(), vinculo.getVisibilidadeMidia(), agora);
                }
                if (ordemAlterada) {
                    existente.reordenar(ordemLivre, agora);
                }
                if (decisaoAlterada || ordemAlterada) {
                    anuncioMidiaRepository.save(existente);
                }
                registrarOrdem(existente, ordensOcupadas);
            }
        }
        return criados;
    }

    private int proximaOrdemLivre(
            AnuncioMidiaEntity vinculo,
            Map<ChaveOrdem, UUID> ordensOcupadas,
            Set<ChaveOrdem> ordensCanonicas,
            UUID idExistente) {
        int ordem = vinculo.getOrdem();
        ChaveOrdem pretendida = chaveOrdem(vinculo, ordem);
        while (true) {
            ChaveOrdem candidata = chaveOrdem(vinculo, ordem);
            UUID ocupante = ordensOcupadas.get(candidata);
            boolean ocupadaPorOutro = ocupante != null && !ocupante.equals(idExistente);
            boolean reservadaParaOutroCanonico = !candidata.equals(pretendida)
                    && ordensCanonicas.contains(candidata);
            if (!ocupadaPorOutro && !reservadaParaOutroCanonico) {
                return ordem;
            }
            ordem++;
        }
    }

    private void registrarOrdem(
            AnuncioMidiaEntity vinculo,
            Map<ChaveOrdem, UUID> ordensOcupadas) {
        if (ocupaOrdem(vinculo)) {
            ordensOcupadas.put(chaveOrdem(vinculo, vinculo.getOrdem()), vinculo.getId());
        }
    }

    private void removerOrdemDoProprioVinculo(
            AnuncioMidiaEntity vinculo,
            Map<ChaveOrdem, UUID> ordensOcupadas) {
        ChaveOrdem atual = chaveOrdem(vinculo, vinculo.getOrdem());
        if (Objects.equals(vinculo.getId(), ordensOcupadas.get(atual))) {
            ordensOcupadas.remove(atual);
        }
    }

    private boolean ocupaOrdem(AnuncioMidiaEntity vinculo) {
        return vinculo.getStatus() != StatusAnuncioMidia.REMOVIDA;
    }

    private ChaveOrdem chaveOrdem(AnuncioMidiaEntity vinculo, Integer ordem) {
        return new ChaveOrdem(vinculo.getAnuncioId(), vinculo.getFinalidade(), ordem);
    }

    private void validarAmbiente() {
        if (!"homologacao".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim())) {
            throw new IllegalStateException("fixture recusada fora de homologacao");
        }
    }

    private void validarCredencialRuntime(String runtimeValue) {
        if (runtimeValue == null
                || runtimeValue.length() < 16
                || !runtimeValue.matches(".*[A-Z].*")
                || !runtimeValue.matches(".*[a-z].*")
                || !runtimeValue.matches(".*[0-9].*")
                || !runtimeValue.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("credencial de runtime nao atende a politica minima");
        }
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static CategoriaFixture categoria(
            String id,
            String identificador,
            String titulo,
            String descricao,
            String destino,
            String imagemPublicaUrl,
            int ordem,
            boolean ativo) {
        return new CategoriaFixture(
                uuid(id), identificador, titulo, descricao, destino, imagemPublicaUrl, ordem, ativo);
    }

    private static GrupoFixture grupo(
            String id,
            UUID anuncioId,
            TipoGrupoAtivacaoBeneficio tipo,
            OrigemBeneficio origem,
            OffsetDateTime inicioEm,
            OffsetDateTime fimEm,
            StatusGrupoAtivacaoBeneficio status,
            String chave) {
        return new GrupoFixture(uuid(id), anuncioId, tipo, origem, inicioEm, fimEm, status, chave);
    }

    private static AtivacaoFixture ativacao(
            String id,
            UUID beneficioId,
            GrupoFixture grupo,
            StatusAtivacaoBeneficio status,
            int custoCreditos,
            BigDecimal preco) {
        return new AtivacaoFixture(
                uuid(id), beneficioId, grupo, status, custoCreditos, preco, grupo.chave() + "-ativacao");
    }

    private record ChaveOrdem(
            UUID anuncioId,
            FinalidadeAnuncioMidia finalidade,
            Integer ordem) {
    }

    private record CategoriaFixture(
            UUID id,
            String identificador,
            String titulo,
            String descricao,
            String destino,
            String imagemPublicaUrl,
            int ordem,
            boolean ativo) {
    }

    private record GrupoFixture(
            UUID id,
            UUID anuncioId,
            TipoGrupoAtivacaoBeneficio tipo,
            OrigemBeneficio origem,
            OffsetDateTime inicioEm,
            OffsetDateTime fimEm,
            StatusGrupoAtivacaoBeneficio status,
            String chave) {
    }

    private record AtivacaoFixture(
            UUID id,
            UUID beneficioId,
            GrupoFixture grupo,
            StatusAtivacaoBeneficio status,
            int custoCreditos,
            BigDecimal preco,
            String chave) {
    }

    public record FixtureResult(
            int categoriasCriadas,
            int localidadesCriadas,
            int localizacoesCriadas,
            int anunciosCriados,
            int arquivosCriados,
            int vinculosCriados,
            int beneficiosCriados,
            boolean storyCriado) {
    }

    public enum FixtureOwnerCredentialStatus {
        CRIADA,
        ATUALIZADA,
        PRESERVADA
    }

    public record FixtureOwnerCredentialResult(FixtureOwnerCredentialStatus status) {
    }
}
