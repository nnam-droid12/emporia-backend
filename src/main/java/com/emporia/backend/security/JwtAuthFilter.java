package com.emporia.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j // ADDED LOMBOK LOGGER
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        log.info("--- JWT FILTER TRIGGERED for path: {} ---", request.getServletPath());

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JWT Filter: Missing or invalid Authorization header. Header value: {}", authHeader);
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String phoneNumber;

        try {
            phoneNumber = jwtService.extractPhoneNumber(jwt);
            log.info("JWT Filter: Extracted Phone Number from token: {}", phoneNumber);
        } catch (Exception e) {
            log.error("JWT Filter: Failed to extract phone number! Token might be invalid or expired. Error: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (phoneNumber != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(phoneNumber);
                log.info("JWT Filter: Successfully found user in database: {}", userDetails.getUsername());

                if (jwtService.isTokenValid(jwt, userDetails.getUsername())) {
                    log.info("JWT Filter: Token is VALID. Authenticating user...");
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.warn("JWT Filter: Token is INVALID or EXPIRED for user: {}", phoneNumber);
                }
            } catch (Exception e) {
                log.error("JWT Filter: Could not load user from database! Is the phone number exactly correct? Error: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}