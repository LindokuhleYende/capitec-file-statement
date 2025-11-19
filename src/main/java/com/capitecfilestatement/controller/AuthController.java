package com.capitecfilestatement.controller;
import com.capitecfilestatement.dto.*;
import com.capitecfilestatement.entity.Customer;
import com.capitecfilestatement.repository.CustomerRepository;
import com.capitecfilestatement.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        Customer customer = customerRepository.findByEmail(request.getEmail())
                .orElseThrow();

        String token = tokenProvider.generateToken(customer);

        return ResponseEntity.ok(new AuthResponse(token, customer.getId(), customer.getEmail()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

        if (customerRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().build();
        }

        Customer customer = Customer.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .active(true)
                .build();

        customer = customerRepository.save(customer);
        String token = tokenProvider.generateToken(customer);

        return ResponseEntity.ok(new AuthResponse(token, customer.getId(), customer.getEmail()));
    }
}
