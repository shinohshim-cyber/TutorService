package com.tutorservice.relationship.web;
import com.tutorservice.identity.dto.IdentityDtos.Invite;
import com.tutorservice.identity.security.AuthPrincipal;
import com.tutorservice.common.web.PageResult;
import com.tutorservice.relationship.dto.RelationshipDtos.*;
import com.tutorservice.relationship.service.RelationshipService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/relationships")
public class RelationshipController {
    private final RelationshipService service;
    public RelationshipController(RelationshipService service) { this.service=service; }
    @PostMapping("/invitations") @ResponseStatus(HttpStatus.CREATED)
    public InvitationView invite(@AuthenticationPrincipal AuthPrincipal auth,@Valid @RequestBody Invite input) { return service.invite(auth,input.recipientId()); }
    @GetMapping("/invitations") public PageResult<InvitationView> invitations(@AuthenticationPrincipal AuthPrincipal auth,@RequestParam(name="cursor",required=false) String cursor,@RequestParam(name="limit",defaultValue="30") int limit) { return service.invitations(auth,cursor,limit); }
    @PostMapping("/invitations/{id}/accept") public RelationView accept(@AuthenticationPrincipal AuthPrincipal auth,@PathVariable("id") UUID id) { return service.accept(auth,id); }
    @GetMapping public PageResult<RelationView> list(@AuthenticationPrincipal AuthPrincipal auth,@RequestParam(name="cursor",required=false) String cursor,@RequestParam(name="limit",defaultValue="30") int limit) { return service.list(auth,cursor,limit); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthPrincipal auth,@PathVariable("id") UUID id) { service.revoke(auth,id); }
}
