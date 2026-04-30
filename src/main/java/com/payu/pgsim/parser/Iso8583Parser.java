package com.payu.pgsim.parser;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class Iso8583Parser {

    private static final Logger log = LoggerFactory.getLogger(Iso8583Parser.class);

    private final GenericPackager primaryPackager;
    private final GenericPackager secondaryPackager;

    public Iso8583Parser(
            @Value("${pgsim.iso.primary-packager:iso87ascii.xml}") String primaryResource,
            @Value("${pgsim.iso.secondary-packager:}") String secondaryResource) {

        this.primaryPackager = loadPackager(primaryResource, "primary");
        if (secondaryResource != null && !secondaryResource.isBlank()) {
            this.secondaryPackager = loadPackager(secondaryResource, "secondary");
            log.info("ISO8583 secondary packager loaded: {}", secondaryResource);
        } else {
            this.secondaryPackager = null;
        }
    }

    private static GenericPackager loadPackager(String classpathResource, String label) {
        try (InputStream stream = Iso8583Parser.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new IllegalStateException("Packager resource not found: " + classpathResource);
            }
            GenericPackager p = new GenericPackager(stream);
            log.info("ISO8583 {} packager loaded: {}", label, classpathResource);
            return p;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ISO8583 packager: " + classpathResource, e);
        }
    }

    public ISOMsg parse(byte[] message) throws ISOException {

        ISOException primaryError = null;
        try {
            return unpack(primaryPackager, message);
        } catch (ISOException e) {
            primaryError = e;
        }

        if (secondaryPackager != null) {
            try {
                log.debug("Primary ISO unpack failed ({}), trying secondary packager", primaryError.getMessage());
                return unpack(secondaryPackager, message);
            } catch (ISOException e) {
                log.warn("Secondary ISO unpack also failed", e);
            }
        }

        log.warn("Malformed ISO8583 message", primaryError);
        throw primaryError != null ? primaryError : new ISOException("Unpack failed");
    }

    private ISOMsg unpack(GenericPackager packager, byte[] message) throws ISOException {
        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setPackager(packager);
        isoMsg.unpack(message);
        String mti = isoMsg.getMTI();
        if (mti == null || mti.length() != 4) {
            throw new ISOException("Invalid MTI format");
        }
        log.info("Received MTI: {}", isoMsg.getMTI());
        return isoMsg;
    }

    public GenericPackager getPackager() {
        return primaryPackager;
    }

    public GenericPackager getSecondaryPackager() {
        return secondaryPackager;
    }
}
