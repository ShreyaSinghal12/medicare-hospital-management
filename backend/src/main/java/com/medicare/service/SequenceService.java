package com.medicare.service;

import com.medicare.exception.ResourceNotFoundException;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates gap-tolerant, human-readable numbers (patient codes, appointment
 * references, invoice numbers, ...) from the {@code number_sequences} table.
 *
 * <p>The row is locked with {@code SELECT ... FOR UPDATE}, so two concurrent
 * transactions can never receive the same value. It must run inside the caller's
 * transaction (MANDATORY) so the number is released if that transaction rolls back.</p>
 */
@Service
public class SequenceService {

    public static final String PATIENT = "PATIENT";
    public static final String APPOINTMENT = "APPOINTMENT";
    public static final String PRESCRIPTION = "PRESCRIPTION";
    public static final String LAB_ORDER = "LAB_ORDER";
    public static final String INVOICE = "INVOICE";
    public static final String PAYMENT = "PAYMENT";
    public static final String DISPENSING = "DISPENSING";
    public static final String EMPLOYEE = "EMPLOYEE";

    private final JdbcTemplate jdbcTemplate;

    public SequenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String sequenceName) {
        Long value;
        try {
            value = jdbcTemplate.queryForObject(
                    "SELECT next_value FROM number_sequences WHERE sequence_name = ? FOR UPDATE",
                    Long.class, sequenceName);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Number sequence", sequenceName);
        }
        jdbcTemplate.update("UPDATE number_sequences SET next_value = next_value + 1 WHERE sequence_name = ?",
                sequenceName);
        return value;
    }

    /** e.g. PAT-000042 */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextPatientCode() {
        return String.format("PAT-%06d", next(PATIENT));
    }

    /** e.g. EMP-0007 */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextEmployeeCode() {
        return String.format("EMP-%04d", next(EMPLOYEE));
    }
}
