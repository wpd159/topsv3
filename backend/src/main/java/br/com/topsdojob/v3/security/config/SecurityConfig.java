package br.com.topsdojob.v3.security.config;

import br.com.topsdojob.v3.platform.error.ApiErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final boolean local;
    private final AdminSecurityErrorWriter errorWriter;

    public SecurityConfig(
            @Value("${app.env:nao_configurado}") String appEnv,
            AdminSecurityErrorWriter errorWriter) {
        this.local = "local".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim());
        this.errorWriter = errorWriter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> {
                    if (local) {
                        csrf.disable();
                    } else {
                        csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
                    }
                })
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                errorWriter.write(request, response, ApiErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorWriter.write(request, response, ApiErrorCode.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/api/health", "/api/health/**", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/visao-geral")
                        .hasAnyRole("ADMIN", "MODERADOR", "COMERCIAL")
                        .requestMatchers(HttpMethod.GET, "/api/admin/anuncios/resumo")
                        .hasAnyRole("ADMIN", "MODERADOR", "COMERCIAL")
                        .requestMatchers(HttpMethod.GET, "/api/admin/moderacao/resumo")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/midias/resumo")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/metricas/resumo")
                        .hasAnyRole("ADMIN", "COMERCIAL")
                        .requestMatchers(HttpMethod.GET, "/api/admin/sistema/status")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/admin/anuncios")
                        .hasAnyRole("ADMIN", "MODERADOR", "COMERCIAL")
                        .requestMatchers(HttpMethod.GET, "/api/admin/anuncios/*")
                        .hasAnyRole("ADMIN", "MODERADOR", "COMERCIAL")
                        .requestMatchers(HttpMethod.GET, "/api/admin/premium/**")
                        .hasAnyRole("ADMIN", "MODERADOR", "COMERCIAL")
                        .requestMatchers(HttpMethod.POST, "/api/admin/anuncios/*/remeter-revisao")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/anuncios/*/midias")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/midias", "/api/admin/midias/*")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/moderacao/revisoes", "/api/admin/moderacao/revisoes/*")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/outbox/*/preview")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/admin/outbox", "/api/admin/outbox/*")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/admin/outbox/*/simular-processamento-local")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/moderacao/revisoes/*/decidir")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/admin/midias/*/decidir")
                        .hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers("/api/admin/**").authenticated()
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public boolean csrfDisabledOnlyInLocal() {
        return local;
    }
}
