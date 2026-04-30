package com.payu.pgsim.engine;

import com.payu.pgsim.model.Condition;
import com.payu.pgsim.model.ResponseRule;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /**
     * Evaluate request against configured rules
     * Returns response code if rule matches
     */
    public String evaluate(ISOMsg request, List<ResponseRule> rules) {

        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (ResponseRule rule : rules) {
            try {
                log.info("Evaluating rule: {}", rule.getRuleId());

                List<Condition> conditions = rule.getConditions();

                if (conditions != null && !conditions.isEmpty()) {
                    String logic = normalizeLogic(rule.getLogic());
                    if (matchesMultiCondition(request, conditions, logic)) {
                        log.info("Rule matched: ruleId={} logic={} conditions={} responseCode={}",
                                rule.getRuleId(), logic, conditions, rule.getResponseCode());
                        return rule.getResponseCode();
                    }
                    continue;
                }

                if (!request.hasField(rule.getField())) {
                    continue;
                }

                String fieldValue = request.getString(rule.getField());
                if (matches(fieldValue, rule.getOperator(), rule.getValue())) {
                    log.info("Rule matched: ruleId={} logic=SINGLE conditions=[field={} operator={} value={}] responseCode={}",
                            rule.getRuleId(), rule.getField(), rule.getOperator(), rule.getValue(), rule.getResponseCode());
                    return rule.getResponseCode();
                }
            } catch (Exception e) {
                log.warn("Rule evaluation error for ruleId={}", rule.getRuleId(), e);
            }
        }

        return null;
    }

    private String normalizeLogic(String logic) {
        if (logic == null || logic.isBlank()) {
            return "AND";
        }
        String normalized = logic.trim().toUpperCase();
        if (!"AND".equals(normalized) && !"OR".equals(normalized)) {
            log.warn("Unsupported rule logic: {}", logic);
            return "AND";
        }
        return normalized;
    }

    private boolean matchesMultiCondition(ISOMsg request, List<Condition> conditions, String logic) {
        if ("OR".equals(logic)) {
            for (Condition condition : conditions) {
                if (matchesCondition(request, condition)) {
                    return true;
                }
            }
            return false;
        }

        for (Condition condition : conditions) {
            if (!matchesCondition(request, condition)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(ISOMsg request, Condition condition) {
        if (condition == null) {
            return false;
        }

        int field = condition.getField();
        if (field <= 0 || field > 128) {
            return false;
        }

        try {
            if (!request.hasField(field)) {
                return false;
            }
            String fieldValue = request.getString(field);
            return matches(fieldValue, condition.getOperator(), condition.getValue());
        } catch (Exception e) {
            log.warn("Condition evaluation error: field={} operator={} value={}",
                    condition.getField(), condition.getOperator(), condition.getValue(), e);
            return false;
        }
    }

    /**
     * Check if rule condition matches request field
     */
    private boolean matches(String value, String operator, String ruleValue) {

        if (value == null || ruleValue == null) {
            return false;
        }

        try {

            switch (operator) {

                case "=":
                    return value.equals(ruleValue);

                case ">":
                    Long gtLeft = parseLongSafe(value);
                    Long gtRight = parseLongSafe(ruleValue);
                    return gtLeft != null && gtRight != null && gtLeft > gtRight;

                case "<":
                    Long ltLeft = parseLongSafe(value);
                    Long ltRight = parseLongSafe(ruleValue);
                    return ltLeft != null && ltRight != null && ltLeft < ltRight;

                case "startsWith":
                    return value.startsWith(ruleValue);

                case "contains":
                    return value.contains(ruleValue);

                default:

                    log.warn("Unsupported rule operator: {}", operator);

                    return false;
            }

        } catch (Exception e) {

            log.warn("Rule evaluation error for operator: {}", operator);

            return false;
        }
    }

    private Long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}