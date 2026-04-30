package com.payu.pgsim.service;

import com.payu.pgsim.model.FieldDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class IsoFieldCatalog {

    private final List<FieldDefinition> definitions;

    public IsoFieldCatalog() {
        Map<Integer, FieldDefinition> known = new HashMap<>();
        known.put(0, new FieldDefinition(0, "Message Type Indicator", "N", "4", "MTI"));
        known.put(1, new FieldDefinition(1, "Primary Bitmap", "B", "64 bits", "Bitmap"));
        known.put(2, new FieldDefinition(2, "Primary Account Number", "LLVAR_NUMERIC", "up to 19", "PAN"));
        known.put(3, new FieldDefinition(3, "Processing Code", "N", "6", "Transaction type"));
        known.put(4, new FieldDefinition(4, "Amount, Transaction", "N", "12", "Transaction amount"));
        known.put(7, new FieldDefinition(7, "Transmission Date/Time", "N", "10", "MMDDhhmmss"));
        known.put(11, new FieldDefinition(11, "System Trace Audit Number", "N", "6", "STAN"));
        known.put(12, new FieldDefinition(12, "Local Transaction Time", "N", "6", "hhmmss"));
        known.put(13, new FieldDefinition(13, "Local Transaction Date", "N", "4", "MMDD"));
        known.put(14, new FieldDefinition(14, "Expiration Date", "N", "4", "YYMM"));
        known.put(22, new FieldDefinition(22, "POS Entry Mode", "N", "3", "Entry mode"));
        known.put(24, new FieldDefinition(24, "Network International Identifier", "N", "3", "NII"));
        known.put(25, new FieldDefinition(25, "POS Condition Code", "N", "2", ""));
        known.put(35, new FieldDefinition(35, "Track 2 Data", "Z_LLVAR", "var", "Track 2"));
        known.put(37, new FieldDefinition(37, "Retrieval Reference Number", "AN", "12", "RRN"));
        known.put(38, new FieldDefinition(38, "Authorization ID Response", "AN", "6", "Auth code"));
        known.put(39, new FieldDefinition(39, "Response Code", "AN", "2", "DE39"));
        known.put(41, new FieldDefinition(41, "Card Acceptor Terminal ID", "ANS", "8", "Terminal ID"));
        known.put(42, new FieldDefinition(42, "Card Acceptor ID Code", "ANS", "15", "Merchant ID"));
        known.put(49, new FieldDefinition(49, "Currency Code, Transaction", "N", "3", ""));
        known.put(54, new FieldDefinition(54, "Additional Amounts", "LLVAR_AN", "var", ""));
        known.put(64, new FieldDefinition(64, "Message Authentication Code", "B", "8", "MAC"));
        known.put(70, new FieldDefinition(70, "Network Management Information Code", "N", "3", "001/002/301"));
        known.put(90, new FieldDefinition(90, "Original Data Elements", "N", "42", "Reversal"));
        known.put(128, new FieldDefinition(128, "MAC 2", "B", "8", "Secondary MAC"));

        List<FieldDefinition> list = new ArrayList<>();
        for (int i = 0; i <= 128; i++) {
            list.add(known.getOrDefault(
                    i,
                    new FieldDefinition(i, "Data Element " + i, "ANS", "var", "ISO 8583 field " + i)));
        }
        this.definitions = Collections.unmodifiableList(list);
    }

    public List<FieldDefinition> all() {
        return definitions;
    }
}
