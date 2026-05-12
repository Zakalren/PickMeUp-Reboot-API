package dev.zakalren.pickmeup.user;

import dev.zakalren.pickmeup.user.exception.DuplicateUserException;
import dev.zakalren.pickmeup.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        if (userRepository.existsByServiceNumber(request.serviceNumber())) {
            throw new DuplicateUserException(request.serviceNumber());
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.create(
                request.serviceNumber(),
                encodedPassword,
                request.name(),
                request.affiliatedUnit(),
                request.rank(),
                request.dateOfBirth(),
                request.telNumber()
        );

        User saved = userRepository.save(user);
        return UserResponse.from(saved);
    }

    public UserResponse findByServiceNumber(String serviceNumber) {
        User user = userRepository.findByServiceNumber(serviceNumber)
                .orElseThrow(() -> new UserNotFoundException(serviceNumber));
        return UserResponse.from(user);
    }
}
