package br.com.topsdojob.v3.infrastructure.payment.efi;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "efi.pix",
        name = {"enabled", "webhook-registration-enabled"},
        havingValue = "true")
public class EfiWebhookRegistrationRunner implements ApplicationRunner {

    private final EfiPixGateway gateway;

    public EfiWebhookRegistrationRunner(EfiPixGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void run(ApplicationArguments args) {
        gateway.garantirWebhookConfigurado();
    }
}
