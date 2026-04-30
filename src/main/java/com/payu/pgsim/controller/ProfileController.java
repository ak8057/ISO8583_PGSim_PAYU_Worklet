package com.payu.pgsim.controller;

import com.payu.pgsim.model.MtiProfile;
import com.payu.pgsim.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

/**
 * REST endpoints for MTI Profile management.
 *
 * A profile ties a request MTI to a response MTI and fully describes both
 * the request-side (bitmap, mandatory/optional fields, validation) and
 * the response-side (echo fields, static values, dynamic tokens, rules).
 *
 * All endpoints are additive – the existing /api/config endpoints keep
 * working exactly as before.
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * List all registered profiles.
     * GET /api/profiles
     */
    @GetMapping
    public Collection<MtiProfile> listProfiles() {
        return profileService.getAllProfiles();
    }

    /**
     * Get a single profile by its ID (e.g. "0100-0110").
     * GET /api/profiles/{profileId}
     */
    @GetMapping("/{profileId}")
    public ResponseEntity<MtiProfile> getProfile(@PathVariable String profileId) {
        MtiProfile p = profileService.getProfile(profileId);
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }

    /**
     * Get the profile for a specific request MTI.
     * GET /api/profiles/by-mti/{requestMti}
     */
    @GetMapping("/by-mti/{requestMti}")
    public ResponseEntity<MtiProfile> getProfileByMti(@PathVariable String requestMti) {
        MtiProfile p = profileService.getProfileByRequestMti(requestMti);
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }

    /**
     * Create or update a profile.
     * POST /api/profiles
     */
    @PostMapping
    public MtiProfile saveProfile(@RequestBody MtiProfile profile) {
        return profileService.saveProfile(profile);
    }

    /**
     * Create or update a profile by ID (convenience upsert).
     * PUT /api/profiles/{profileId}
     */
    @PutMapping("/{profileId}")
    public MtiProfile upsertProfile(@PathVariable String profileId,
                                    @RequestBody MtiProfile profile) {
        profile.setProfileId(profileId);
        return profileService.saveProfile(profile);
    }

    /**
     * Delete a profile by ID.
     * DELETE /api/profiles/{profileId}
     */
    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileId) {
        profileService.deleteProfile(profileId);
        return ResponseEntity.ok().build();
    }
}
