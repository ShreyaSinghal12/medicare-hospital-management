package com.medicare.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * ABO/Rh blood group. Stored in the database and exchanged in JSON using the
 * conventional label ("A+", "O-", ...), not the Java constant name.
 */
public enum BloodGroup {
    A_POS("A+"), A_NEG("A-"), B_POS("B+"), B_NEG("B-"),
    AB_POS("AB+"), AB_NEG("AB-"), O_POS("O+"), O_NEG("O-");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static BloodGroup fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(b -> b.label.equalsIgnoreCase(value.trim()) || b.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown blood group: " + value));
    }

    /** JPA converter: BloodGroup <-> "A+" column value. */
    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<BloodGroup, String> {
        @Override
        public String convertToDatabaseColumn(BloodGroup attribute) {
            return attribute == null ? null : attribute.label;
        }

        @Override
        public BloodGroup convertToEntityAttribute(String dbData) {
            return dbData == null ? null : fromLabel(dbData);
        }
    }
}
