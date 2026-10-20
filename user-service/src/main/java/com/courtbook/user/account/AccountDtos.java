package com.courtbook.user.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request and response bodies for the user service's REST API. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String name,
            // BCrypt only looks at the first 72 bytes.
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserView(Long id, String email, String name, Role role, Instant createdAt) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.getCreatedAt());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {
    }

    public record RoleChange(@NotNull Role role) {
    }

    public record UserStats(long members, long admins, long joinedLast7Days) {
    }
}
