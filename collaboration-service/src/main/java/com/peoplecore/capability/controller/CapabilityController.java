package com.peoplecore.capability.controller;

import com.peoplecore.capability.entity.Capability;
import com.peoplecore.capability.entity.TitleCapability;
import com.peoplecore.capability.service.CapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/capability")
@RequiredArgsConstructor
public class CapabilityController {

    private final CapabilityService capabilityService;

    @GetMapping("/me/capabilities")
    public ResponseEntity<List<String>> myCapabilities(
        @RequestHeader(value = "X-User-Title", required = false) Long titleId
    ) {
        if (titleId == null) {
            return ResponseEntity.ok(List.of());
        }
        List<String> codes = capabilityService.listByTitle(titleId).stream()
            .map(TitleCapability::getCapabilityCode)
            .toList();
        return ResponseEntity.ok(codes);
    }

    @GetMapping("/capabilities")
    public ResponseEntity<List<Capability>> listAll() {
        return ResponseEntity.ok(capabilityService.listAll());
    }

    @GetMapping("/titles/{titleId}/capabilities")
    public ResponseEntity<List<String>> listByTitle(@PathVariable Long titleId) {
        List<String> codes = capabilityService.listByTitle(titleId).stream()
            .map(TitleCapability::getCapabilityCode)
            .toList();
        return ResponseEntity.ok(codes);
    }

    @PostMapping("/titles/{titleId}/capabilities/{code}")
    public ResponseEntity<TitleCapability> grant(
        @RequestHeader("X-User-Company") UUID companyId,
        @PathVariable Long titleId,
        @PathVariable String code
    ) {
        return ResponseEntity.ok(capabilityService.grant(companyId, titleId, code));
    }

    @DeleteMapping("/titles/{titleId}/capabilities/{code}")
    public ResponseEntity<Void> revoke(
        @PathVariable Long titleId,
        @PathVariable String code
    ) {
        capabilityService.revoke(titleId, code);
        return ResponseEntity.noContent().build();
    }
}
