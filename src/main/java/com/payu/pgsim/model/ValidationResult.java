package com.payu.pgsim.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;
    private List<String> errors = new ArrayList<>();

    public static ValidationResult ok() {
        return new ValidationResult(true, new ArrayList<>());
    }

    public static ValidationResult fail(String message) {
        List<String> e = new ArrayList<>();
        e.add(message);
        return new ValidationResult(false, e);
    }
}
