package com.courtbook.tournament.web;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, as described by their verified token. */
public record CurrentUser(long id, String name, String email, boolean admin) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String name = jwt.getClaimAsString("name");
        return new CurrentUser(
                Long.parseLong(jwt.getSubject()),
                name != null ? name : "Member",
                jwt.getClaimAsString("email"),
                roles != null && roles.contains("ADMIN"));
    }
}
