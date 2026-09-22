package br.com.topsdojob.v3.web.admin.blog;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.blog.BlogCategoriaService;
import br.com.topsdojob.v3.application.blog.BlogImagemService;
import br.com.topsdojob.v3.application.blog.BlogPostService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    controllers = {AdminBlogPostController.class, AdminBlogCategoriaController.class},
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminBlogControllerSecurityTest {

  private static final List<String> ROTAS = List.of(
      "/api/admin/blog-posts", "/api/admin/blog-categorias");

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private BlogPostService posts;

  @MockBean
  private BlogCategoriaService categorias;

  @MockBean
  private BlogImagemService imagens;

  @Test
  void adminComPermissaoConsultaPostsECategorias() throws Exception {
    for (String rota : ROTAS) {
      mockMvc.perform(get(rota).with(adminConfigurador())).andExpect(status().isOk());
    }
    verify(posts).listarAdmin(null, null);
    verify(categorias).listarAdmin();
  }

  @Test
  void acessoIndevidoNaoChegaAosServicosMesmoComCsrfValido() throws Exception {
    for (String rota : ROTAS) {
      mockMvc.perform(get(rota)).andExpect(status().isUnauthorized());
      mockMvc.perform(post(rota).with(csrf())
              .contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isUnauthorized());
      for (RequestPostProcessor identidade : List.of(
          user("usuario-sintetico").authorities(
              new SimpleGrantedAuthority("ROLE_USUARIO"),
              new SimpleGrantedAuthority("ADMIN_CONFIGURAR")),
          user("moderador-sintetico").authorities(
              new SimpleGrantedAuthority("ROLE_MODERADOR"),
              new SimpleGrantedAuthority("ADMIN_CONFIGURAR")),
          user("admin-sem-permissao").roles("ADMIN"))) {
        mockMvc.perform(get(rota).with(identidade)).andExpect(status().isForbidden());
        mockMvc.perform(post(rota).with(identidade).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
      }
    }
    verifyNoInteractions(posts, categorias, imagens);
  }

  @Test
  void mutacaoContinuaExigindoCsrfMesmoParaAdminComPermissao() throws Exception {
    for (String rota : ROTAS) {
      mockMvc.perform(post(rota).with(adminConfigurador())
              .contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(posts, categorias, imagens);
  }

  private RequestPostProcessor adminConfigurador() {
    return user("admin-sintetico").authorities(
        new SimpleGrantedAuthority("ROLE_ADMIN"),
        new SimpleGrantedAuthority("ADMIN_CONFIGURAR"));
  }
}
