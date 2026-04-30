package com.payu.pgsim.nmm;

import com.payu.pgsim.parser.Iso8583Parser;
import lombok.RequiredArgsConstructor;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Builds raw NMM (0800) payloads using the existing jPOS packager.
 *
 * Fields are always generated fresh (DE7 = current datetime, DE11 = random STAN).
 * No profile config is hard-coded here — only the packager and MTI are fixed.
 */
@Component
@RequiredArgsConstructor
public class NmmMessageBuilder {

    public static final String DE70_LOGON  = "001";
    public static final String DE70_LOGOFF = "002";
    public static final String DE70_ECHO   = "301";

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("MMddHHmmss");

    private final Iso8583Parser parser;
    private final Random random = new Random();

    /** Raw bytes for a LOGON request (DE70 = 001). */
    public byte[] buildLogon() throws Exception {
        return buildNmm(DE70_LOGON);
    }

    /** Raw bytes for a LOGOFF request (DE70 = 002). */
    public byte[] buildLogoff() throws Exception {
        return buildNmm(DE70_LOGOFF);
    }

    /** Raw bytes for an ECHO request (DE70 = 301). */
    public byte[] buildEcho() throws Exception {
        return buildNmm(DE70_ECHO);
    }

    /**
     * Builds an 0800 message with the given DE70 value.
     * Returns the raw packed bytes.
     *
     * @param de70Code  one of {@link #DE70_LOGON}, {@link #DE70_LOGOFF}, {@link #DE70_ECHO}
     */
    public byte[] buildNmm(String de70Code) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(parser.getPackager());
        msg.setMTI("0800");
        msg.set(7,  LocalDateTime.now().format(DATETIME_FMT));
        msg.set(11, generateStan());
        msg.set(70, de70Code);
        return msg.pack();
    }

    /**
     * Extracts DE11 (STAN) from a packed byte array.
     * Used to build the correlation key for sendAndAwait.
     */
    public String extractStan(byte[] payload) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(parser.getPackager());
            msg.unpack(payload);
            return msg.hasField(11) ? msg.getString(11) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts DE70 from a packed 0810 response.
     * Returns null when the message cannot be parsed.
     */
    public String extractDe70(byte[] payload) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(parser.getPackager());
            msg.unpack(payload);
            return msg.hasField(70) ? msg.getString(70) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true when the packed message has MTI 0810 and DE39 = "00".
     */
    public boolean isSuccessfulNmmResponse(byte[] payload) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(parser.getPackager());
            msg.unpack(payload);
            return "0810".equals(msg.getMTI())
                    && msg.hasField(39)
                    && "00".equals(msg.getString(39));
        } catch (Exception e) {
            return false;
        }
    }

    private String generateStan() {
        return String.valueOf(100000 + random.nextInt(900000));
    }
}
