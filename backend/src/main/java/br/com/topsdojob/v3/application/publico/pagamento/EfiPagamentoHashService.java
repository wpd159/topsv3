package br.com.topsdojob.v3.application.publico.pagamento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EfiPagamentoHashService {

    private final String salt;

    public EfiPagamentoHashService(@Value("${app.event.hash-salt:}") String salt) {
        this.salt = salt == null ? "" : salt;
    }

    public String hash(String value) {
        try {
            byte[] data = (salt + ":" + (value == null ? "" : value)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    public boolean segredoConfere(String esperado, String recebido) {
        byte[] left = (esperado == null ? "" : esperado).getBytes(StandardCharsets.UTF_8);
        byte[] right = (recebido == null ? "" : recebido).getBytes(StandardCharsets.UTF_8);
        return left.length > 0 && MessageDigest.isEqual(left, right);
    }
}
