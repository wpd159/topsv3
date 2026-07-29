package br.com.topsdojob.v3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TopsDoJobBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopsDoJobBackendApplication.class, args);
    }
}
