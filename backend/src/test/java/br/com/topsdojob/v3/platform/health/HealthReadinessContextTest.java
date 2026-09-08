package br.com.topsdojob.v3.platform.health;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.service.LocalidadesConsultaCoordenador;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootTest(
        classes = HealthReadinessContextTest.HealthContextApplication.class,
        properties = {
            "app.health.readiness-timeout-ms=500",
            "app.health.readiness-query-timeout-seconds=1"
        })
class HealthReadinessContextTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private HealthController healthController;

    @Autowired
    private BackendReadinessService readinessService;

    @Autowired
    private ApplicationReadinessState applicationState;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PackagedMigrationCatalog migrationCatalog;

    @BeforeEach
    void prepare() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reset(dataSource, migrationCatalog);
        applicationState.applicationReady();
        when(migrationCatalog.minimumCompatibleVersion()).thenReturn(52);
    }

    @Test
    void contextCreatesHealthBeansAndReturnsLiveness200() throws Exception {
        org.assertj.core.api.Assertions.assertThat(healthController).isNotNull();
        org.assertj.core.api.Assertions.assertThat(readinessService).isNotNull();

        mockMvc.perform(get("/api/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.application").value("UP"));
    }

    @Test
    void readinessReturns200WhenDatabaseAndFlywayAreCompatible() throws Exception {
        Connection connection = readyConnection();
        when(dataSource.getConnection()).thenReturn(connection);

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.application").value("UP"))
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.migrations").value("UP"));
    }

    @Test
    void readinessReturnsSanitized503WhenDatabaseIsUnavailable() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("internal-db-host:5432"));

        mockMvc.perform(get("/api/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.application").value("UP"))
                .andExpect(jsonPath("$.components.database").value("DOWN"))
                .andExpect(jsonPath("$.components.migrations").value("DOWN"))
                .andExpect(content().string(not(containsString("internal-db-host"))))
                .andExpect(content().string(not(containsString("5432"))))
                .andExpect(content().string(not(containsString("SQLException"))));
    }

    private static Connection readyConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement databaseStatement = mock(Statement.class);
        Statement migrationStatement = mock(Statement.class);
        ResultSet databaseResult = mock(ResultSet.class);
        ResultSet migrationResult = mock(ResultSet.class);

        when(connection.createStatement()).thenReturn(databaseStatement, migrationStatement);
        when(databaseStatement.executeQuery(anyString())).thenReturn(databaseResult);
        when(migrationStatement.executeQuery(anyString())).thenReturn(migrationResult);
        when(databaseResult.next()).thenReturn(true);
        when(databaseResult.getInt("probe")).thenReturn(1);
        when(migrationResult.next()).thenReturn(true);
        when(migrationResult.getInt("latest_version")).thenReturn(52);
        when(migrationResult.getLong("failed_count")).thenReturn(0L);
        return connection;
    }

    @SpringBootConfiguration
    @EnableWebMvc
    @Import({
        HealthController.class,
        BackendReadinessService.class,
        DatabaseReadinessProbe.class,
        ApplicationReadinessState.class
    })
    static class HealthContextApplication {

        @Bean
        LocalidadesConsultaCoordenador localidadesConsultaCoordenador() {
            return mock(LocalidadesConsultaCoordenador.class);
        }

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PackagedMigrationCatalog migrationCatalog() {
            return mock(PackagedMigrationCatalog.class);
        }
    }
}
