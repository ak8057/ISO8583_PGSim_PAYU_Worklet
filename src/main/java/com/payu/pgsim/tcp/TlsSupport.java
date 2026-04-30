package com.payu.pgsim.tcp;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

/**
 * TR-6: optional TLS for the ISO TCP listener. If enabled without a keystore, logs a stub warning and serves plain TCP.
 */
@Component
public class TlsSupport {

    private static final Logger log = LoggerFactory.getLogger(TlsSupport.class);

    private final boolean requested;
    private final SslContext sslContext;

    public TlsSupport(
            @Value("${pgsim.tcp.tls.enabled:false}") boolean enabled,
            @Value("${pgsim.tcp.tls.keystore:}") String keystorePath,
            @Value("${pgsim.tcp.tls.keystore-password:changeit}") String keystorePassword,
            @Value("${pgsim.tcp.tls.keystore-type:PKCS12}") String keystoreType) {

        this.requested = enabled;
        SslContext ctx = null;
        if (enabled) {
            if (keystorePath == null || keystorePath.isBlank()) {
                log.warn("TR-6: pgsim.tcp.tls.enabled=true but pgsim.tcp.tls.keystore is empty — ISO TCP stays plain. "
                        + "Set pgsim.tcp.tls.keystore to a PKCS12/JKS file, or disable TLS. See docs/BRD-COMPLIANCE.md.");
            } else {
                try {
                    ctx = buildContext(keystorePath, keystorePassword, keystoreType);
                    log.info("TR-6: ISO TCP TLS active (keystore: {})", keystorePath);
                } catch (Exception e) {
                    log.error("TR-6: TLS keystore load failed — ISO TCP will use plain sockets", e);
                }
            }
        }
        this.sslContext = ctx;
    }

    private static SslContext buildContext(String keystorePath, String password, String keystoreType)
            throws Exception {

        KeyStore ks = KeyStore.getInstance(keystoreType);
        Path p = Path.of(keystorePath);
        try (InputStream in = Files.newInputStream(p)) {
            ks.load(in, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        return SslContextBuilder.forServer(kmf).build();
    }

    public boolean isTlsRequested() {
        return requested;
    }

    public boolean isTlsActive() {
        return sslContext != null;
    }

    public SslContext sslContext() {
        return sslContext;
    }
}
