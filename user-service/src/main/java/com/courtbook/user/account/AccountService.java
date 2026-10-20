package com.courtbook.user.account;

import com.courtbook.user.account.AccountDtos.LoginRequest;
import com.courtbook.user.account.AccountDtos.RegisterRequest;
import com.courtbook.user.account.AccountDtos.UserStats;
import com.courtbook.user.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Set<String> adminEmails;
    // Compared against when the email doesn't exist, so a wrong email takes
    // as long as a wrong password and can't be used to probe for accounts.
    private final String dummyHash;

    public AccountService(UserRepository users, PasswordEncoder passwords,
            @Value("${courtbook.admin-emails:}") String adminEmails) {
        this.users = users;
        this.passwords = passwords;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(AccountService::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.dummyHash = passwords.encode("not-a-real-password");
    }

    @Transactional
    public User register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Role role = adminEmails.contains(email) ? Role.ADMIN : Role.MEMBER;
        try {
            return users.saveAndFlush(new User(email, req.name().trim(), passwords.encode(req.password()), role));
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same email raced past the check above.
            throw ApiException.conflict("An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwords.matches(req.password(), hash);
        if (user == null || !matches) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User get(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("No user with id " + id));
    }

    @Transactional(readOnly = true)
    public List<User> all() {
        return users.findAll(Sort.by("createdAt"));
    }

    @Transactional
    public User changeRole(long id, Role role) {
        User user = get(id);
        user.setRole(role);
        return user;
    }

    @Transactional(readOnly = true)
    public UserStats stats() {
        return new UserStats(
                users.countByRole(Role.MEMBER),
                users.countByRole(Role.ADMIN),
                users.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7))));
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
