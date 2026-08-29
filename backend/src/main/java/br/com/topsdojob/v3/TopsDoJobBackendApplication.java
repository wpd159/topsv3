package br.com.topsdojob.v3;

import br.com.topsdojob.v3.application.operacional.midia.backfill.RestrictedMediaPreviewBackfillBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TopsDoJobBackendApplication {

    public static void main(String[] args) {
        if (RestrictedMediaPreviewBackfillBootstrap.requested(args)) {
            RestrictedMediaPreviewBackfillBootstrap.run(args);
            return;
        }
        SpringApplication.run(TopsDoJobBackendApplication.class, args);
    }
}
