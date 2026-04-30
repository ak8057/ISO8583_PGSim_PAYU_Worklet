package com.payu.pgsim.brd;

import com.payu.pgsim.brd.dto.BrdConditionalValue;
import com.payu.pgsim.brd.dto.BrdFieldConfig;
import com.payu.pgsim.brd.dto.BrdMessageTypeConfig;
import com.payu.pgsim.brd.dto.BrdRequestConfig;
import com.payu.pgsim.brd.dto.BrdResponseConfig;
import com.payu.pgsim.model.BitmapConfig;
import com.payu.pgsim.model.ConditionalValueEntry;
import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MessageTypeConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BrdConfigMapper {

    public MessageTypeConfig toInternal(BrdMessageTypeConfig brd) {
        if (brd == null) {
            return null;
        }
        MessageTypeConfig c = new MessageTypeConfig();
        c.setMti(brd.getMti());
        c.setDescription(brd.getDescription());

        BrdRequestConfig req = brd.getRequestConfig();
        BrdResponseConfig res = brd.getResponseConfig();
        if (res != null) {
            c.setResponseMti(res.getResponseMti());
        }

        BitmapConfig bm = new BitmapConfig();
        if (req != null) {
            bm.setMandatoryBits(copy(req.getMandatoryBits()));
            bm.setOptionalBits(copy(req.getOptionalBits()));
            LinkedHashSet<Integer> union = new LinkedHashSet<>();
            if (req.getMandatoryBits() != null) {
                union.addAll(req.getMandatoryBits());
            }
            if (req.getOptionalBits() != null) {
                union.addAll(req.getOptionalBits());
            }
            bm.setRequestBits(new ArrayList<>(union));
            List<FieldConfig> rf = mapRequestFields(req.getFieldConfigs(), req.getMandatoryBits());
            ensureRequestFieldStubs(rf, req.getMandatoryBits(), true);
            ensureRequestFieldStubs(rf, req.getOptionalBits(), false);
            c.setRequestFields(rf);
        }

        if (res != null) {
            bm.setResponseBits(copy(res.getResponseBits()));
            List<FieldConfig> responseFields = mapResponseFields(res.getFieldConfigs());
            if (res.getDefaultResponseCode() != null && !res.getDefaultResponseCode().isBlank()) {
                boolean has39 = responseFields.stream().anyMatch(f -> f.getField() == 39);
                if (!has39) {
                    FieldConfig de39 = new FieldConfig();
                    de39.setField(39);
                    de39.setFieldName("Response Code");
                    de39.setMode("STATIC");
                    de39.setValueType("STATIC");
                    de39.setValue(res.getDefaultResponseCode());
                    de39.setMandatory(false);
                    responseFields.add(de39);
                }
            }
            c.setResponseFields(responseFields);
        }

        boolean sec = needsSecondary(bm.getRequestBits()) || needsSecondary(bm.getResponseBits());
        if (req != null && req.getSecondaryBitmap() != null) {
            sec = req.getSecondaryBitmap();
        }
        bm.setSecondaryBitmap(sec);
        c.setBitmap(bm);

        return c;
    }

    public BrdMessageTypeConfig toBrd(MessageTypeConfig c) {
        if (c == null) {
            return null;
        }
        BrdMessageTypeConfig brd = new BrdMessageTypeConfig();
        brd.setMti(c.getMti());
        brd.setDescription(c.getDescription());

        BrdRequestConfig req = new BrdRequestConfig();
        BitmapConfig bm = c.getBitmap();
        if (bm != null) {
            if (bm.getMandatoryBits() != null && !bm.getMandatoryBits().isEmpty()) {
                req.setMandatoryBits(copy(bm.getMandatoryBits()));
                req.setOptionalBits(copy(bm.getOptionalBits()));
            } else if (bm.getRequestBits() != null) {
                req.setMandatoryBits(copy(bm.getRequestBits()));
            }
            req.setSecondaryBitmap(bm.isSecondaryBitmap());
        }
        req.setFieldConfigs(toBrdFieldList(c.getRequestFields(), true));
        brd.setRequestConfig(req);

        BrdResponseConfig res = new BrdResponseConfig();
        res.setResponseMti(c.getResponseMti());
        if (bm != null) {
            res.setResponseBits(copy(bm.getResponseBits()));
        }
        res.setFieldConfigs(toBrdFieldList(c.getResponseFields(), false));
        String def39 = extractDefaultResponseCode(c);
        if (def39 != null) {
            res.setDefaultResponseCode(def39);
        }
        brd.setResponseConfig(res);

        return brd;
    }

    private static String extractDefaultResponseCode(MessageTypeConfig c) {
        if (c.getResponseFields() == null) {
            return null;
        }
        return c.getResponseFields().stream()
                .filter(f -> f.getField() == 39 && f.getValue() != null && f.getValue().matches("\\d{2}"))
                .map(FieldConfig::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<FieldConfig> mapRequestFields(List<BrdFieldConfig> list, List<Integer> mandatoryBits) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<FieldConfig> out = new ArrayList<>();
        for (BrdFieldConfig b : list) {
            FieldConfig f = mapOne(b, true, mandatoryBits);
            out.add(f);
        }
        return out;
    }

    private List<FieldConfig> mapResponseFields(List<BrdFieldConfig> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<FieldConfig> out = new ArrayList<>();
        for (BrdFieldConfig b : list) {
            out.add(mapOne(b, false, null));
        }
        return out;
    }

    private FieldConfig mapOne(BrdFieldConfig b, boolean request, List<Integer> mandatoryBits) {
        FieldConfig f = new FieldConfig();
        f.setField(b.getFieldNumber());
        f.setFieldName(b.getFieldName());
        f.setDataType(b.getDataType());
        f.setType(b.getDataType() != null ? b.getDataType() : b.getFieldName());
        f.setMandatory(b.isMandatory());
        if (mandatoryBits != null && mandatoryBits.contains(b.getFieldNumber())) {
            f.setMandatory(true);
        }
        f.setValidation(b.getValidation());
        f.setValue(b.getValue());
        f.setTemplate(b.getTemplate());
        f.setDynamicType(b.getDynamicType());
        f.setSourceField(b.getSourceField());
        f.setValueType(b.getValueType());
        if (b.getValueType() != null && !b.getValueType().isBlank()) {
            f.setMode(b.getValueType());
        }
        if (b.getConditionalValues() != null) {
            f.setConditionalValues(b.getConditionalValues().stream()
                    .map(BrdConfigMapper::mapCond)
                    .collect(Collectors.toList()));
        }
        inferLength(f, request);
        return f;
    }

    private static ConditionalValueEntry mapCond(BrdConditionalValue v) {
        ConditionalValueEntry e = new ConditionalValueEntry();
        e.setCondition(v.getCondition());
        e.setValue(v.getValue());
        return e;
    }

    private void inferLength(FieldConfig f, boolean request) {
        if (f.getLength() > 0) {
            return;
        }
        String t = f.getDataType() != null ? f.getDataType().toUpperCase() : "";
        if (t.contains("PAN") || f.getField() == 2) {
            f.setLength(19);
        } else if (f.getField() == 11) {
            f.setLength(6);
        } else if (f.getField() == 7 || t.contains("DATETIME")) {
            f.setLength(10);
        } else if (f.getField() == 70) {
            f.setLength(3);
        } else if (f.getField() == 3) {
            f.setLength(6);
        } else if (f.getField() == 4) {
            f.setLength(12);
        }
    }

    private List<BrdFieldConfig> toBrdFieldList(List<FieldConfig> list, boolean request) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<BrdFieldConfig> out = new ArrayList<>();
        for (FieldConfig f : list) {
            out.add(toBrdField(f));
        }
        return out;
    }

    private BrdFieldConfig toBrdField(FieldConfig f) {
        BrdFieldConfig b = new BrdFieldConfig();
        b.setFieldNumber(f.getField());
        b.setFieldName(f.getFieldName());
        b.setDataType(f.getDataType() != null ? f.getDataType() : f.getType());
        b.setMandatory(f.isMandatory());
        b.setValueType(f.getValueType() != null ? f.getValueType() : f.getMode());
        b.setValue(f.getValue());
        b.setTemplate(f.getTemplate());
        b.setDynamicType(f.getDynamicType());
        b.setSourceField(f.getSourceField());
        b.setValidation(f.getValidation());
        if (f.getConditionalValues() != null) {
            b.setConditionalValues(f.getConditionalValues().stream().map(e -> {
                BrdConditionalValue v = new BrdConditionalValue();
                v.setCondition(e.getCondition());
                v.setValue(e.getValue());
                return v;
            }).collect(Collectors.toList()));
        }
        return b;
    }

    private static List<Integer> copy(List<Integer> in) {
        return in == null ? null : new ArrayList<>(in);
    }

    private static boolean needsSecondary(List<Integer> bits) {
        if (bits == null) {
            return false;
        }
        return bits.stream().anyMatch(b -> b != null && b > 64);
    }

    private void ensureRequestFieldStubs(List<FieldConfig> rf, List<Integer> bits, boolean mandatory) {
        if (bits == null) {
            return;
        }
        for (Integer bit : bits) {
            if (bit == null) {
                continue;
            }
            boolean exists = rf.stream().anyMatch(f -> f.getField() == bit);
            if (!exists) {
                FieldConfig stub = new FieldConfig();
                stub.setField(bit);
                stub.setMandatory(mandatory);
                stub.setType("NUMERIC");
                inferLength(stub, true);
                rf.add(stub);
            }
        }
    }
}
