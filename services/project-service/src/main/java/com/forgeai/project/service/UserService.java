package com.forgeai.project.service;

import com.forgeai.project.dto.LoginRequest;
import com.forgeai.project.dto.LoginResponse;
import com.forgeai.project.dto.RegisterRequest;
import com.forgeai.project.entity.User;
import com.forgeai.project.exception.EmailAlreadyExistsException;
import com.forgeai.project.exception.InvalidCredentialsException;
import com.forgeai.project.repository.UserRepository;
import jdk.jshell.spi.ExecutionControl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest request){
        if(userRepository.existsByEmail(request.getEmail())){
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        User user=new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("DEVELOPER");
        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request){
        User user=userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);
        if(!passwordEncoder.matches(request.getPassword(),user.getPasswordHash())){
            throw new InvalidCredentialsException();
        }
        String token=jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        return new LoginResponse(token);
    }
}
