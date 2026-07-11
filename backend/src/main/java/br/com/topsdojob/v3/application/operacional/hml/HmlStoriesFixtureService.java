package br.com.topsdojob.v3.application.operacional.hml;

import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.StoryAnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.CredencialUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.PapelUsuarioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.CredencialUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.PapelUsuarioRepository;
import br.com.topsdojob.v3.persistence.repository.StoryAnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    private static final UUID USUARIO_ID = uuid("f1000000-0000-4000-8000-000000000001");
    private static final UUID ANUNCIO_A_ID = uuid("f1000000-0000-4000-8000-000000000101");
    private static final UUID ANUNCIO_B_ID = uuid("f1000000-0000-4000-8000-000000000102");
    private static final UUID ANUNCIO_INELEGIVEL_ID = uuid("f1000000-0000-4000-8000-000000000103");
    private static final UUID ARQUIVO_COMPARTILHADO_ID = uuid("f1000000-0000-4000-8000-000000000201");
    private static final UUID ARQUIVO_FOTO_A_ID = uuid("f1000000-0000-4000-8000-000000000202");
    private static final UUID ARQUIVO_VIDEO_A_ID = uuid("f1000000-0000-4000-8000-000000000203");
    private static final UUID ARQUIVO_PENDENTE_ID = uuid("f1000000-0000-4000-8000-000000000204");
    private static final UUID ARQUIVO_FOTO_B_ID = uuid("f1000000-0000-4000-8000-000000000205");
    private static final UUID ARQUIVO_INELEGIVEL_ID = uuid("f1000000-0000-4000-8000-000000000206");
    private static final UUID MIDIA_COMPARTILHADA_ID = uuid("f1000000-0000-4000-8000-000000000301");
    private static final UUID MIDIA_FOTO_A_ID = uuid("f1000000-0000-4000-8000-000000000302");
    private static final UUID MIDIA_VIDEO_A_ID = uuid("f1000000-0000-4000-8000-000000000303");
    private static final UUID MIDIA_PENDENTE_ID = uuid("f1000000-0000-4000-8000-000000000304");
    private static final UUID MIDIA_FOTO_B_ID = uuid("f1000000-0000-4000-8000-000000000305");
    private static final UUID MIDIA_INELEGIVEL_ID = uuid("f1000000-0000-4000-8000-000000000306");
    private static final UUID MIDIA_STORY_USUARIO_ID = uuid("f1000000-0000-4000-8000-000000000307");
    private static final UUID STORY_USUARIO_ID = uuid("f1000000-0000-4000-8000-000000000401");

    private final String appEnv;
    private final UsuarioRepository usuarioRepository;
    private final CredencialUsuarioRepository credencialRepository;
    private final PapelUsuarioRepository papelRepository;
    private final AnuncioRepository anuncioRepository;
    private final ArquivoMidiaRepository arquivoRepository;
    private final AnuncioMidiaRepository anuncioMidiaRepository;
    private final StoryAnuncioRepository storyRepository;
    private final PasswordEncoder passwordEncoder;

    public HmlStoriesFixtureService(
            @Value("${app.env:nao_configurado}") String appEnv,
            UsuarioRepository usuarioRepository,
            CredencialUsuarioRepository credencialRepository,
            PapelUsuarioRepository papelRepository,
            AnuncioRepository anuncioRepository,
            ArquivoMidiaRepository arquivoRepository,
            AnuncioMidiaRepository anuncioMidiaRepository,
            StoryAnuncioRepository storyRepository,
            PasswordEncoder passwordEncoder) {
        this.appEnv = appEnv;
        this.usuarioRepository = usuarioRepository;
        this.credencialRepository = credencialRepository;
        this.papelRepository = papelRepository;
        this.anuncioRepository = anuncioRepository;
        this.arquivoRepository = arquivoRepository;
        this.anuncioMidiaRepository = anuncioMidiaRepository;
        this.storyRepository = storyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public FixtureResult provisionar(String runtimeValue) {
        validarAmbiente();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UsuarioEntity usuario = provisionarUsuario(runtimeValue, agora);

        int anunciosCriados = 0;
        anunciosCriados += criarAnuncioSeAusente(ANUNCIO_A_ID, usuario.getId(), "fixture-stories-hml-a", "Perfil ficticio Stories A", StatusAnuncio.PUBLICADO, agora);
        anunciosCriados += criarAnuncioSeAusente(ANUNCIO_B_ID, usuario.getId(), "fixture-stories-hml-b", "Perfil ficticio Stories B", StatusAnuncio.PUBLICADO, agora);
        anunciosCriados += criarAnuncioSeAusente(ANUNCIO_INELEGIVEL_ID, usuario.getId(), "fixture-stories-hml-inelegivel", "Perfil ficticio inelegivel", StatusAnuncio.PAUSADO, agora);

        List<ArquivoMidiaEntity> arquivos = List.of(
                arquivo(ARQUIVO_COMPARTILHADO_ID, "fixture/stories/compartilhada.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_FOTO_A_ID, "fixture/stories/foto-a.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_VIDEO_A_ID, "fixture/stories/video-a.mp4", "video/mp4", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_PENDENTE_ID, "fixture/stories/pendente.webp", "image/webp", StatusArquivoMidia.PENDENTE, agora),
                arquivo(ARQUIVO_FOTO_B_ID, "fixture/stories/foto-b.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora),
                arquivo(ARQUIVO_INELEGIVEL_ID, "fixture/stories/inelegivel.webp", "image/webp", StatusArquivoMidia.VALIDADO, agora));
        int arquivosCriados = salvarAusentes(arquivos);

        List<AnuncioMidiaEntity> vinculos = List.of(
                midia(MIDIA_COMPARTILHADA_ID, ANUNCIO_A_ID, ARQUIVO_COMPARTILHADO_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, 0, StatusAnuncioMidia.PUBLICAVEL, agora),
                midia(MIDIA_FOTO_A_ID, ANUNCIO_A_ID, ARQUIVO_FOTO_A_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 0, StatusAnuncioMidia.PUBLICAVEL, agora),
                midia(MIDIA_VIDEO_A_ID, ANUNCIO_A_ID, ARQUIVO_VIDEO_A_ID, TipoAnuncioMidia.VIDEO, FinalidadeAnuncioMidia.GALERIA, 1, StatusAnuncioMidia.PUBLICAVEL, agora),
                midia(MIDIA_PENDENTE_ID, ANUNCIO_A_ID, ARQUIVO_PENDENTE_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.GALERIA, 2, StatusAnuncioMidia.PENDENTE, agora),
                midia(MIDIA_FOTO_B_ID, ANUNCIO_B_ID, ARQUIVO_FOTO_B_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, 0, StatusAnuncioMidia.PUBLICAVEL, agora),
                midia(MIDIA_INELEGIVEL_ID, ANUNCIO_INELEGIVEL_ID, ARQUIVO_INELEGIVEL_ID, TipoAnuncioMidia.FOTO, FinalidadeAnuncioMidia.CAPA, 0, StatusAnuncioMidia.PUBLICAVEL, agora),
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
        int vinculosCriados = salvarVinculosAusentes(vinculos);

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
        return new FixtureResult(anunciosCriados, arquivosCriados, vinculosCriados, storyCriado);
    }

    private UsuarioEntity provisionarUsuario(String runtimeValue, OffsetDateTime agora) {
        UsuarioEntity usuario = usuarioRepository.findByEmailNormalizado(USUARIO_EMAIL).orElse(null);
        if (usuario == null) {
            usuario = UsuarioEntity.criarCadastroPublico(
                    USUARIO_ID,
                    "Anunciante ficticio de homologacao",
                    USUARIO_EMAIL,
                    null,
                    agora);
            usuarioRepository.saveAndFlush(usuario);
        }
        String hash = passwordEncoder.encode(runtimeValue);
        CredencialUsuarioEntity credencial = credencialRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (credencial == null) {
            credencial = CredencialUsuarioEntity.criar(UUID.randomUUID(), usuario.getId(), hash, agora);
        } else {
            credencial.atualizarHashHomologacao(hash, agora);
        }
        credencialRepository.save(credencial);
        boolean possuiPapel = papelRepository.findByUsuarioId(usuario.getId()).stream()
                .anyMatch(item -> item.getPapel() == PapelUsuario.USUARIO);
        if (!possuiPapel) {
            papelRepository.save(PapelUsuarioEntity.criarUsuarioPublico(usuario.getId(), agora));
        }
        return usuario;
    }

    private int criarAnuncioSeAusente(
            UUID id,
            UUID usuarioId,
            String slug,
            String titulo,
            StatusAnuncio status,
            OffsetDateTime agora) {
        if (anuncioRepository.existsById(id)) {
            return 0;
        }
        anuncioRepository.save(AnuncioEntity.criarFixtureHomologacao(
                id,
                usuarioId,
                slug,
                titulo,
                status,
                StatusModeracaoAnuncio.APROVADO,
                agora));
        return 1;
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
            OffsetDateTime agora) {
        return AnuncioMidiaEntity.criarFixtureHomologacao(
                id,
                anuncioId,
                arquivoId,
                tipo,
                finalidade,
                ordem,
                status,
                VisibilidadeMidia.RESTRITA_18,
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

    private int salvarVinculosAusentes(List<AnuncioMidiaEntity> vinculos) {
        int criados = 0;
        for (AnuncioMidiaEntity vinculo : vinculos) {
            if (!anuncioMidiaRepository.existsById(vinculo.getId())) {
                anuncioMidiaRepository.save(vinculo);
                criados++;
            }
        }
        return criados;
    }

    private void validarAmbiente() {
        if (!"homologacao".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim())) {
            throw new IllegalStateException("fixture recusada fora de homologacao");
        }
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    public record FixtureResult(
            int anunciosCriados,
            int arquivosCriados,
            int vinculosCriados,
            boolean storyCriado) {
    }
}
