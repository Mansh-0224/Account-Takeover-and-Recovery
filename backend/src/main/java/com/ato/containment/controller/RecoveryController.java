package com.ato.containment.controller;

import com.ato.containment.dto.RecoveryCompleteRequest;
import com.ato.containment.dto.RecoveryCompleteResponse;
import com.ato.containment.dto.RecoveryRequestInput;
import com.ato.containment.dto.RecoveryRequestResponse;
import com.ato.containment.dto.RecoveryStatusDto;
import com.ato.containment.dto.RecoveryVerifyRequest;
import com.ato.containment.dto.UserDto;
import com.ato.containment.model.RecoveryRequest;
import com.ato.containment.recovery.RecoveryService;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The /request, /{id}/verify, /{id}/complete, and /{id}/status endpoints are
 * deliberately PUBLIC -- a user who's locked out has no session to
 * authenticate with. Identity is bound to the RecoveryRequest id itself
 * (see that class's comment) rather than to anything the client asserts.
 *
 * /admin is the only endpoint here that goes through CurrentSessionResolver
 * -- a tenant admin viewing a user's recovery history.
 */
@RestController
@RequestMapping("/api/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final CurrentSessionResolver currentSessionResolver;

    public RecoveryController(RecoveryService recoveryService, CurrentSessionResolver currentSessionResolver) {
        this.recoveryService = recoveryService;
        this.currentSessionResolver = currentSessionResolver;
    }

    @PostMapping("/request")
    public RecoveryRequestResponse request(@RequestBody RecoveryRequestInput body, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String device = httpRequest.getHeader("X-Device-Id");

        return recoveryService.request(body.email(), ip, device)
                .map(result -> new RecoveryRequestResponse(
                        result.request().getId(), result.request().getExpiresAt(), result.testModeCode(),
                        "Recovery started. In test mode the code is included in this response " +
                        "(a real deployment would email it instead)."))
                .orElseGet(RecoveryRequestResponse::generic);
    }

    @GetMapping("/{id}/status")
    public RecoveryStatusDto status(@PathVariable String id) {
        return RecoveryStatusDto.from(recoveryService.getPublic(id));
    }

    @PostMapping("/{id}/verify")
    public RecoveryStatusDto verify(@PathVariable String id, @RequestBody RecoveryVerifyRequest body, HttpServletRequest httpRequest) {
        RecoveryRequest req = recoveryService.verify(id, body.code(),
                httpRequest.getRemoteAddr(), httpRequest.getHeader("X-Device-Id"));
        return RecoveryStatusDto.from(req);
    }

    @PostMapping("/{id}/complete")
    public RecoveryCompleteResponse complete(@PathVariable String id, @RequestBody RecoveryCompleteRequest body, HttpServletRequest httpRequest) {
        RecoveryService.CompleteResult result = recoveryService.complete(id, body.newPassword(),
                httpRequest.getRemoteAddr(), httpRequest.getHeader("X-Device-Id"));
        return new RecoveryCompleteResponse(result.sessionToken(), UserDto.from(result.user()));
    }

    @GetMapping("/admin")
    public List<RecoveryStatusDto> adminHistory(HttpServletRequest httpRequest, @RequestParam String userId) {
        AuthenticatedUser current = currentSessionResolver.require(httpRequest);
        return recoveryService.listForTenantAndUser(current.tenantId(), userId).stream()
                .map(RecoveryStatusDto::from).toList();
    }
}
