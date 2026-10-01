package com.ato.containment.controller;

import com.ato.containment.dto.CreateUserRequest;
import com.ato.containment.dto.UpdateUserRequest;
import com.ato.containment.dto.UserDto;
import com.ato.containment.security.AuthenticatedUser;
import com.ato.containment.security.CurrentSessionResolver;
import com.ato.containment.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The {@code id} path variable below is fine to take from the URL — it just
 * says "which user am I asking about". The TENANT that user must belong to
 * always comes from {@link CurrentSessionResolver}, never from the URL, a
 * query parameter, or the request body. That distinction is what tenant
 * isolation in this controller depends on.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentSessionResolver currentSessionResolver;

    public UserController(UserService userService, CurrentSessionResolver currentSessionResolver) {
        this.userService = userService;
        this.currentSessionResolver = currentSessionResolver;
    }

    @GetMapping
    public List<UserDto> list(HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return userService.listForTenant(current.tenantId()).stream().map(UserDto::from).toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return UserDto.from(userService.getForTenant(current.tenantId(), id));
    }

    @PostMapping
    public UserDto create(@RequestBody CreateUserRequest body, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return UserDto.from(userService.create(current, body));
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable String id, @RequestBody UpdateUserRequest body, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        return UserDto.from(userService.update(current, id, body));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, HttpServletRequest request) {
        AuthenticatedUser current = currentSessionResolver.require(request);
        userService.delete(current, id);
    }
}
