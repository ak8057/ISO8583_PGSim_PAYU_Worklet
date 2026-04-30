package com.payu.pgsim.controller;

import com.payu.pgsim.model.FieldConfig;
import com.payu.pgsim.model.MtiProfile;
import com.payu.pgsim.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides profile-driven client-mode field hints.
 *
 * When acting as CLIENT, the user selects an MTI. The UI calls
 * GET /api/profiles/client-fields/{requestMti} to get the list of fields
 * it should show (driven by the profile's mandatory + optional bits and
 * clientDefaultFields), pre-populated with any static/dynamic defaults.
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileClientFieldsController {

    private final ProfileService profileService;
    private final org.springframework.beans.factory.ObjectProvider<com.payu.pgsim.tcp.TransportModeManager> transportModeManager;
    private final org.springframework.beans.factory.ObjectProvider<com.payu.pgsim.service.ProfileClientService> profileClientService;

    /**
     * Returns the ordered list of fields the client UI should show for this
     * request MTI, with default values pre-filled where the profile specifies them.
     *
     * GET /api/profiles/client-fields/{requestMti}
     */
    @GetMapping("/client-fields/{requestMti}")
    public ResponseEntity<ClientFieldsResponse> getClientFields(
            @PathVariable String requestMti) {

        MtiProfile profile = null;

        String mode = "SERVER";
        if (transportModeManager.getIfAvailable() != null) {
            mode = transportModeManager.getIfAvailable().getCurrentMode();
        }

        if ("CLIENT".equalsIgnoreCase(mode) && profileClientService.getIfAvailable() != null) {
            com.payu.pgsim.model.MessageTypeConfig serverConfig = profileClientService.getIfAvailable().fetchProfileFromServer(requestMti);
            if (serverConfig != null) {
                // Convert to MtiProfile so we don't break the UI
                profile = profileService.toProfile(serverConfig);
            }
        }

        // Fallback to local profile
        if (profile == null) {
            profile = profileService.getProfileByRequestMti(requestMti);
        }

        if (profile == null) {
            return ResponseEntity.notFound().build();
        }

        ClientFieldsResponse resp = new ClientFieldsResponse();
        resp.setRequestMti(profile.getRequestMti());
        resp.setResponseMti(profile.getResponseMti());
        resp.setProfileId(profile.getProfileId());
        resp.setProfileName(profile.getName());
        resp.setMandatoryBits(profile.getMandatoryRequestBits());
        resp.setOptionalBits(profile.getOptionalRequestBits());

        // Build ordered field list: mandatory first, then optional
        List<ClientField> fields = new ArrayList<>();

        List<Integer> allBits = new ArrayList<>();
        if (profile.getMandatoryRequestBits() != null) {
            allBits.addAll(profile.getMandatoryRequestBits());
        }
        if (profile.getOptionalRequestBits() != null) {
            for (Integer bit : profile.getOptionalRequestBits()) {
                if (!allBits.contains(bit)) allBits.add(bit);
            }
        }

        for (Integer de : allBits) {
            ClientField cf = new ClientField();
            cf.setDeNumber(de);
            cf.setMandatory(profile.getMandatoryRequestBits() != null
                    && profile.getMandatoryRequestBits().contains(de));

            // Find field definition from profile requestFields
            if (profile.getRequestFields() != null) {
                profile.getRequestFields().stream()
                        .filter(f -> f.getField() == de)
                        .findFirst()
                        .ifPresent(fd -> {
                            cf.setType(fd.getType());
                            cf.setLength(fd.getLength());
                            cf.setFieldName(fd.getFieldName());
                        });
            }

            // Find default value from clientDefaultFields
            if (profile.getClientDefaultFields() != null) {
                profile.getClientDefaultFields().stream()
                        .filter(f -> f.getField() == de)
                        .findFirst()
                        .ifPresent(fd -> {
                            cf.setDefaultValue(fd.getValue());
                            cf.setDefaultMode(fd.getMode() != null ? fd.getMode() : "STATIC");
                        });
            }

            fields.add(cf);
        }

        resp.setFields(fields);
        return ResponseEntity.ok(resp);
    }

    // ── Inner DTO classes ─────────────────────────────────────────────────────

    public static class ClientFieldsResponse {
        private String requestMti;
        private String responseMti;
        private String profileId;
        private String profileName;
        private List<Integer> mandatoryBits;
        private List<Integer> optionalBits;
        private List<ClientField> fields;

        public String getRequestMti()              { return requestMti; }
        public void   setRequestMti(String v)      { this.requestMti = v; }
        public String getResponseMti()             { return responseMti; }
        public void   setResponseMti(String v)     { this.responseMti = v; }
        public String getProfileId()               { return profileId; }
        public void   setProfileId(String v)       { this.profileId = v; }
        public String getProfileName()             { return profileName; }
        public void   setProfileName(String v)     { this.profileName = v; }
        public List<Integer> getMandatoryBits()    { return mandatoryBits; }
        public void   setMandatoryBits(List<Integer> v) { this.mandatoryBits = v; }
        public List<Integer> getOptionalBits()     { return optionalBits; }
        public void   setOptionalBits(List<Integer> v)  { this.optionalBits = v; }
        public List<ClientField> getFields()       { return fields; }
        public void   setFields(List<ClientField> v)    { this.fields = v; }
    }

    public static class ClientField {
        private int    deNumber;
        private boolean mandatory;
        private String type;
        private int    length;
        private String fieldName;
        private String defaultValue;
        private String defaultMode;

        public int     getDeNumber()               { return deNumber; }
        public void    setDeNumber(int v)          { this.deNumber = v; }
        public boolean isMandatory()               { return mandatory; }
        public void    setMandatory(boolean v)     { this.mandatory = v; }
        public String  getType()                   { return type; }
        public void    setType(String v)           { this.type = v; }
        public int     getLength()                 { return length; }
        public void    setLength(int v)            { this.length = v; }
        public String  getFieldName()              { return fieldName; }
        public void    setFieldName(String v)      { this.fieldName = v; }
        public String  getDefaultValue()           { return defaultValue; }
        public void    setDefaultValue(String v)   { this.defaultValue = v; }
        public String  getDefaultMode()            { return defaultMode; }
        public void    setDefaultMode(String v)    { this.defaultMode = v; }
    }
}
