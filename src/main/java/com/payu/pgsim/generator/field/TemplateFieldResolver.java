package com.payu.pgsim.generator.field;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.FieldMode;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateFieldResolver implements FieldResolver {

    private static final Pattern REQUEST_TOKEN = Pattern.compile("\\$\\{REQUEST_(\\d+)\\}");
    private final DynamicTokenGenerator dynamicTokenGenerator;

    public TemplateFieldResolver(DynamicTokenGenerator dynamicTokenGenerator) {
        this.dynamicTokenGenerator = dynamicTokenGenerator;
    }

    @Override
    public boolean supports(FieldMode mode) {
        return mode == FieldMode.TEMPLATE;
    }

    @Override
    public String resolve(FieldConfig fieldConfig, ISOMsg sourceMessage) {
        String template = fieldConfig.getTemplate() != null && !fieldConfig.getTemplate().isBlank()
                ? fieldConfig.getTemplate()
                : fieldConfig.getValue();
        if (template == null) {
            return null;
        }

        String out = template;
        out = replaceDynamic(out, "DATE");
        out = replaceDynamic(out, "TIME");
        out = replaceDynamic(out, "DATETIME");
        out = replaceDynamic(out, "STAN");
        out = replaceDynamic(out, "RRN");

        if (sourceMessage != null) {
            out = out.replace("${PAN}", sourceMessage.hasField(2) ? nullToEmpty(sourceMessage.getString(2)) : "");
            out = out.replace("${AMOUNT}", sourceMessage.hasField(4) ? nullToEmpty(sourceMessage.getString(4)) : "");

            Matcher matcher = REQUEST_TOKEN.matcher(out);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                int f = Integer.parseInt(matcher.group(1));
                String replacement = sourceMessage.hasField(f) ? nullToEmpty(sourceMessage.getString(f)) : "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(sb);
            out = sb.toString();
        } else {
            out = out.replace("${PAN}", "").replace("${AMOUNT}", "");
        }
        return out;
    }

    private String replaceDynamic(String source, String token) {
        String generated = dynamicTokenGenerator.generate(token);
        return source.replace("${" + token + "}", generated != null ? generated : "");
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}

