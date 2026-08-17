package br.com.topsdojob.v3.web.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioProprietarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioProprietarioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioDetalhadoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioOrdenacao;
import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioSituacao;
import br.com.topsdojob.v3.web.admin.anuncio.AdminAnuncioAtualizacaoController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminAnuncioModeracaoRbacMethodSecurityTest.Config.class)
class AdminAnuncioModeracaoRbacMethodSecurityTest {

    @Autowired
    private AdminAnuncioDetalhadoController controller;

    @Autowired
    private AdminAnuncioAtualizacaoController atualizacaoController;

    @Autowired
    private AdminAnuncioDetalhadoConsultaService service;

    @Autowired
    private AdminAnuncioAtualizacaoService atualizacaoService;

    @Autowired
    private AdminAnuncioProprietarioAtualizacaoService proprietarioAtualizacaoService;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminComAnuncioLerAcessaDetalhe() {
        autenticar("ROLE_ADMIN", "ANUNCIO_LER");
        UUID id = UUID.randomUUID();

        var response = controller.detalhar(id);

        verify(service).detalhar(id, false);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void filaELocalidadesSaoProtegidasENoStore() {
        autenticar("ROLE_ADMIN", "ANUNCIO_LER");

        var fila = controller.listar(
                0,
                30,
                AdminAnuncioSituacao.TODOS,
                null,
                null,
                null,
                null,
                AdminAnuncioOrdenacao.MAIS_RECENTES);
        var localidades = controller.localidadesFiltro();

        verify(service).listar(
                0,
                30,
                AdminAnuncioSituacao.TODOS,
                null,
                null,
                null,
                null,
                AdminAnuncioOrdenacao.MAIS_RECENTES,
                false);
        verify(service).localidadesFiltro();
        assertThat(fila.getHeaders().getCacheControl()).contains("no-store");
        assertThat(localidades.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void usuarioNaoAcessaFilaNemCatalogoDeLocalidades() {
        autenticar("ROLE_USUARIO", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.listar(
                0,
                30,
                AdminAnuncioSituacao.TODOS,
                null,
                null,
                null,
                null,
                AdminAnuncioOrdenacao.MAIS_RECENTES))
                .isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> controller.localidadesFiltro())
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void moderadorComAnuncioLerAcessaDetalhe() {
        autenticar("ROLE_MODERADOR", "ANUNCIO_LER");
        UUID id = UUID.randomUUID();

        controller.detalhar(id);

        verify(service).detalhar(id, false);
    }

    @Test
    void usuarioMesmoComAutoridadeNaoAcessaDetalhe() {
        autenticar("ROLE_USUARIO", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void anuncianteMesmoComAutoridadeNaoAcessaDetalhe() {
        autenticar("ROLE_ANUNCIANTE", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void comercialMesmoComAutoridadeNaoAcessaDetalhe() {
        autenticar("ROLE_COMERCIAL", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void papelAdministrativoSemPermissaoNaoAcessaDetalhe() {
        autenticar("ROLE_ADMIN");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void somenteAdminComAnuncioModerarEditaCamposComerciais() {
        UUID id = UUID.randomUUID();
        AdminAnuncioAtualizacaoRequest body = mock(AdminAnuncioAtualizacaoRequest.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        autenticar("ROLE_ADMIN", "ANUNCIO_MODERAR");

        atualizacaoController.atualizar(id, body, null, request);

        verify(atualizacaoService).atualizar(
                org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.eq(body),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString());

        autenticar("ROLE_MODERADOR", "ANUNCIO_MODERAR");
        assertThatThrownBy(() -> atualizacaoController.atualizar(id, body, null, request))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void somenteAdminComAnuncioModerarEditaNomeECpfDoProprietario() {
        UUID id = UUID.randomUUID();
        AdminAnuncioProprietarioAtualizacaoRequest body =
                new AdminAnuncioProprietarioAtualizacaoRequest("Pessoa QA", "52998224725");
        HttpServletRequest request = mock(HttpServletRequest.class);
        autenticar("ROLE_ADMIN", "ANUNCIO_MODERAR");

        atualizacaoController.atualizarProprietario(id, body, null, request);

        verify(proprietarioAtualizacaoService).atualizar(
                org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.eq(body),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString());

        autenticar("ROLE_MODERADOR", "ANUNCIO_MODERAR");
        assertThatThrownBy(() -> atualizacaoController.atualizarProprietario(id, body, null, request))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void documentosExigemPapelAdministrativoEPermissaoDocumental() {
        UUID id = UUID.randomUUID();
        autenticar("ROLE_MODERADOR", "DOCUMENTO_REVISAR");

        controller.documentos(id);

        verify(service).documentosDoAnunciante(id);
        autenticar("ROLE_USUARIO", "DOCUMENTO_REVISAR");
        assertThatThrownBy(() -> controller.documentos(id))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    private void autenticar(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teste", "n/a", granted));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AdminAnuncioDetalhadoConsultaService service() {
            return mock(AdminAnuncioDetalhadoConsultaService.class);
        }

        @Bean
        AdminAnuncioAtualizacaoService atualizacaoService() {
            return mock(AdminAnuncioAtualizacaoService.class);
        }

        @Bean
        AdminAnuncioProprietarioAtualizacaoService proprietarioAtualizacaoService() {
            return mock(AdminAnuncioProprietarioAtualizacaoService.class);
        }

        @Bean
        AdminAnuncioDetalhadoController controller(AdminAnuncioDetalhadoConsultaService service) {
            return new AdminAnuncioDetalhadoController(service);
        }

        @Bean
        AdminAnuncioAtualizacaoController atualizacaoController(
                AdminAnuncioAtualizacaoService atualizacaoService,
                AdminAnuncioProprietarioAtualizacaoService proprietarioAtualizacaoService) {
            return new AdminAnuncioAtualizacaoController(
                    atualizacaoService,
                    proprietarioAtualizacaoService);
        }
    }
}
