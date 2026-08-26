package br.com.topsdojob.v3.application.publico.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublicClientIpResolver {

    private static final int MAX_FORWARDED_HOPS = 32;
    private static final List<CidrRange> CLOUDFLARE_PROXIES = cidrs(
            "173.245.48.0/20",
            "103.21.244.0/22",
            "103.22.200.0/22",
            "103.31.4.0/22",
            "141.101.64.0/18",
            "108.162.192.0/18",
            "190.93.240.0/20",
            "188.114.96.0/20",
            "197.234.240.0/22",
            "198.41.128.0/17",
            "162.158.0.0/15",
            "104.16.0.0/13",
            "104.24.0.0/14",
            "172.64.0.0/13",
            "131.0.72.0/22",
            "2400:cb00::/32",
            "2606:4700::/32",
            "2803:f800::/32",
            "2405:b500::/32",
            "2405:8100::/32",
            "2a06:98c0::/29",
            "2c0f:f248::/32");

    private final List<CidrRange> trustedProxies;

    public PublicClientIpResolver(
            @Value("${app.security.auth.trusted-proxy-cidrs:127.0.0.0/8,::1/128}")
            String configuredTrustedProxyCidrs) {
        this.trustedProxies = configuredCidrs(configuredTrustedProxyCidrs);
    }

    public String resolve(HttpServletRequest request) {
        InetAddress remote = parseLiteral(request == null ? null : request.getRemoteAddr());
        if (remote == null) {
            return "unknown";
        }
        if (isCloudflare(remote)) {
            InetAddress connecting = cloudflareConnectingIp(request);
            return connecting == null ? normalize(remote) : normalize(connecting);
        }
        if (!isTrustedProxy(remote)) {
            return normalize(remote);
        }

        String forwardedHeader = request.getHeader("X-Forwarded-For");
        if (forwardedHeader == null || forwardedHeader.isBlank()) {
            return normalize(remote);
        }
        String[] hops = forwardedHeader.split(",", -1);
        int first = Math.max(0, hops.length - MAX_FORWARDED_HOPS);
        for (int index = hops.length - 1; index >= first; index--) {
            InetAddress hop = parseLiteral(hops[index]);
            if (hop == null) {
                return normalize(remote);
            }
            if (isTrustedProxy(hop)) {
                continue;
            }
            if (isCloudflare(hop)) {
                InetAddress connecting = cloudflareConnectingIp(request);
                if (connecting != null) {
                    return normalize(connecting);
                }
            }
            return normalize(hop);
        }
        return normalize(remote);
    }

    private InetAddress cloudflareConnectingIp(HttpServletRequest request) {
        InetAddress connecting = parseLiteral(request == null ? null : request.getHeader("CF-Connecting-IP"));
        return connecting == null || isTrustedProxy(connecting) ? null : connecting;
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxies.stream().anyMatch(range -> range.contains(address));
    }

    private boolean isCloudflare(InetAddress address) {
        return CLOUDFLARE_PROXIES.stream().anyMatch(range -> range.contains(address));
    }

    private static InetAddress parseLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty()
                || value.length() > 45
                || value.indexOf('%') >= 0
                || (!value.contains(".") && !value.contains(":"))
                || !value.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        if (value.indexOf(':') < 0 && !validIpv4(value)) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static boolean validIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.matches("[0-9]+")) {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(InetAddress address) {
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }

    private static List<CidrRange> cidrs(String... values) {
        List<CidrRange> ranges = new ArrayList<>(values.length);
        for (String value : values) {
            ranges.add(CidrRange.parse(value));
        }
        return List.copyOf(ranges);
    }

    private static List<CidrRange> configuredCidrs(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            throw new IllegalArgumentException("Ao menos um proxy confiavel deve ser configurado");
        }
        String[] values = configuredCidrs.split(",", -1);
        List<CidrRange> ranges = new ArrayList<>(values.length);
        for (String value : values) {
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("CIDR de proxy confiavel vazio");
            }
            ranges.add(CidrRange.parse(normalized));
        }
        return List.copyOf(ranges);
    }

    private record CidrRange(byte[] network, int prefixLength) {

        static CidrRange parse(String value) {
            String[] parts = value.split("/", 2);
            InetAddress address = parseLiteral(parts[0]);
            if (address == null || parts.length != 2) {
                throw new IllegalArgumentException("CIDR invalido");
            }
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > address.getAddress().length * 8) {
                throw new IllegalArgumentException("Prefixo CIDR invalido");
            }
            return new CidrRange(address.getAddress(), prefix);
        }

        boolean contains(InetAddress candidate) {
            byte[] address = candidate.getAddress();
            if (address.length != network.length) {
                return false;
            }
            int completeBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < completeBytes; index++) {
                if (address[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (address[completeBytes] & mask) == (network[completeBytes] & mask);
        }
    }
}
