package com.tutorservice.identity.dto;

import com.tutorservice.identity.entity.User;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class IdentityDtos {
    private IdentityDtos() {}
    public record Signup(@NotBlank @Email @Size(max=254) String email, @NotNull @Size(min=12,max=72) String password,
                         @NotBlank @Size(max=80) @Pattern(regexp="^[^\\p{Cntrl}]+$") String name,
                         User.Role requestedRole, @Size(max=128) String teacherInvitationToken) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotNull @Size(max=72) String password) {}
    public record Rename(@NotBlank @Size(max=80) @Pattern(regexp="^[^\\p{Cntrl}]+$") String name) {}
    public record DeleteAccount(@NotNull @Size(max=72) String password) {}
    public record Invite(@NotNull UUID recipientId) {}
    public record Profile(UUID id, String email, String name, User.Role role) {
        public static Profile of(User u) { return new Profile(u.getId(),u.getEmail(),u.getName(),u.getRole()); }
    }
    public record TokenResponse(String accessToken, long expiresIn, Profile user) {}
    public record Deletion(UUID deletionId) {}
}
