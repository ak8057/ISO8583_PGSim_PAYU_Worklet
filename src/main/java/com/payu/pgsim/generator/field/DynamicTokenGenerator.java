package com.payu.pgsim.generator.field;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Component
public class DynamicTokenGenerator {

    private final Random random = new Random();

    public String generate(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.trim().toUpperCase()) {
            case "DATE" -> LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd"));
            case "TIME" -> LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            case "DATETIME" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
            case "STAN" -> String.valueOf(random.nextInt(900000) + 100000);
            case "RRN" -> String.valueOf(100000000000L + random.nextInt(900000000));
            default -> null;
        };
    }
}

