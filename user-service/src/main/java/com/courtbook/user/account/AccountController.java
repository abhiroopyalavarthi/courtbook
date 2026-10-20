package com.courtbook.user.account;

import com.courtbook.user.account.AccountDtos.LoginRequest;
import com.courtbook.user.account.AccountDtos.RegisterRequest;
import com.courtbook.user.account.AccountDtos.RoleChange;
import com.courtbook.user.account.AccountDtos.TokenResponse;
import com.courtbook.user.account.AccountDtos.UserStats;
import com.courtbook.user.account.AccountDtos.UserView;
import com.courtbook.user.security.TokenService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final AccountService accounts;
    private final TokenService tokens;

    public AccountController(AccountService accounts, TokenService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @PostMapping("/v1/users")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest req) {
        User user = accounts.register(req);
        return ResponseEntity.created(URI.create("/v1/users/" + user.getId())).body(UserView.of(user));
    }

    @PostMapping("/v1/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        User user = accounts.authenticate(req);
        TokenService.IssuedToken token = tokens.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), UserView.of(user));
    }

    @GetMapping("/v1/users/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return UserView.of(accounts.get(Long.parseLong(jwt.getSubject())));
    }

    @GetMapping("/v1/admin/users")
    public List<UserView> all() {
        return accounts.all().stream().map(UserView::of).toList();
    }

    @PutMapping("/v1/admin/users/{id}/role")
    public UserView changeRole(@PathVariable long id, @Valid @RequestBody RoleChange body) {
        return UserView.of(accounts.changeRole(id, body.role()));
    }

    @GetMapping("/v1/admin/stats/users")
    public UserStats stats() {
        return accounts.stats();
    }
}
