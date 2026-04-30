package com.payu.pgsim.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinition {

    private int fieldNumber;
    private String fieldName;
    private String dataType;
    private String lengthDescription;
    private String description;
}
