package com.payu.pgsim.model;

import lombok.Data;

import java.util.List;

/**
 * An MTI Profile pairs a request MTI with its response MTI and fully describes
 * both the request-side (allowed/mandatory bitmap fields + validation rules) and
 * the response-side (echo fields, static fields, dynamic fields, decline codes).
 *
 * The profile-based approach is the *preferred* configuration unit. Each profile
 * drives both server-mode response generation and client-mode request construction.
 */
@Data
public class MtiProfile {

    // ── Identity ─────────────────────────────────────────────────────────────

    /** e.g. "0100-0110" — a stable unique key for this profile. */
    private String profileId;

    /** Human-readable name, e.g. "Authorization Profile". */
    private String name;

    /** e.g. "Authorization Request / Response" */
    private String description;

    // ── MTI pair ─────────────────────────────────────────────────────────────

    /** Incoming (request) MTI, e.g. "0100". */
    private String requestMti;

    /** Outgoing (response) MTI, e.g. "0110". */
    private String responseMti;

    // ── Request side ─────────────────────────────────────────────────────────

    /**
     * Bitmap bits that MUST appear on the incoming request.
     * Missing → profile validation failure → decline response.
     */
    private List<Integer> mandatoryRequestBits;

    /**
     * Bitmap bits that MAY appear on the incoming request.
     * An unexpected bit (not in mandatory OR optional) → decline.
     */
    private List<Integer> optionalRequestBits;

    /** Per-field definitions for the request side (type, length, validation). */
    private List<FieldConfig> requestFields;

    /** Whether a secondary bitmap is expected on the request. */
    private boolean secondaryBitmap;

    // ── Response side ────────────────────────────────────────────────────────

    /**
     * Bitmap bits that will be present in the outgoing response.
     * Fields resolved outside this set will be stripped.
     */
    private List<Integer> responseBits;

    /**
     * Fields whose values are copied verbatim from the request.
     * e.g. [2, 11, 37] → echo PAN, STAN, RRN.
     */
    private List<Integer> echoFields;

    /** Fields with a fixed static value in the response. */
    private List<FieldConfig> staticResponseFields;

    /** Fields whose values are generated dynamically (DATETIME, STAN, RRN, …). */
    private List<FieldConfig> dynamicResponseFields;

    // ── Rules & Scenarios ────────────────────────────────────────────────────

    /**
     * Conditional rules that can override DE39 (response code).
     * These work exactly the same as the rules in {@link MessageTypeConfig}.
     */
    private List<ResponseRule> rules;

    /**
     * Scenario simulation (NONE / DELAY / TIMEOUT).
     */
    private ScenarioRule scenario;

    // ── Client-mode hints ────────────────────────────────────────────────────

    /**
     * Default field values used when constructing the *outgoing* request in
     * CLIENT mode. Keys are DE numbers, values follow the same
     * STATIC / DYNAMIC / TEMPLATE / FROM_REQUEST resolution rules.
     */
    private List<FieldConfig> clientDefaultFields;
}
