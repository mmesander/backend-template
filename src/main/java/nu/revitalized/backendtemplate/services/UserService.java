package nu.revitalized.backendtemplate.services;

// Imports

import lombok.Getter;
import lombok.Setter;
import nu.revitalized.backendtemplate.dtos.input.UserInputDto;
import nu.revitalized.backendtemplate.dtos.output.UserDto;
import nu.revitalized.backendtemplate.exceptions.BadRequestException;
import nu.revitalized.backendtemplate.exceptions.InvalidInputException;
import nu.revitalized.backendtemplate.exceptions.RecordNotFoundException;
import nu.revitalized.backendtemplate.exceptions.UsernameNotFoundException;
import nu.revitalized.backendtemplate.models.Authority;
import nu.revitalized.backendtemplate.models.User;
import nu.revitalized.backendtemplate.repositories.AuthorityRepository;
import nu.revitalized.backendtemplate.repositories.UserRepository;
import nu.revitalized.backendtemplate.specifications.UserSpecification;
import org.springframework.stereotype.Service;

import java.util.*;

import static nu.revitalized.backendtemplate.helpers.CopyProperties.copyProperties;
import static nu.revitalized.backendtemplate.security.config.SpringSecurityConfig.passwordEncoder;

@Getter
@Setter
@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;

    public UserService(
            UserRepository userRepository,
            AuthorityRepository authorityRepository
    ) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
    }

    // Transfer Methods
    public User dtoToUser(UserInputDto inputDto) {
        User user = new User();

        user.setUsername(inputDto.getUsername().toLowerCase());
        user.setPassword(passwordEncoder().encode(inputDto.getPassword()));
        user.setEmail(inputDto.getEmail());

        return user;
    }

    public UserDto userToDto(User user) {
        UserDto userDto = new UserDto();

        copyProperties(user, userDto);

        return userDto;
    }

    // CRUD Methods
    public List<UserDto> getUsers() {
        List<User> users = userRepository.findAll();
        List<UserDto> userDtos = new ArrayList<>();

        for (User user : users) {
            UserDto userDto = userToDto(user);
            userDtos.add(userDto);
        }

        if (userDtos.isEmpty()) {
            throw new RecordNotFoundException("No users found");
        } else {
            userDtos.sort(Comparator.comparing(UserDto::getUsername));
            return userDtos;
        }
    }

    public UserDto getUser(String username) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        return userToDto(user);
    }

    public List<UserDto> getUsersByFilter(
            String username,
            String email
    ) {
        UserSpecification filters = new UserSpecification(username, email);

        List<User> filteredUsers = userRepository.findAll(filters);
        List<UserDto> userDtos = new ArrayList<>();

        for (User user : filteredUsers) {
            UserDto userDto = userToDto(user);
            userDtos.add(userDto);
        }

        if (userDtos.isEmpty()) {
            throw new RecordNotFoundException("No users found with the specified filters");
        } else {
            userDtos.sort(Comparator.comparing(UserDto::getUsername));
            return userDtos;
        }
    }

    public UserDto createUser(UserInputDto inputDto) {
        User user = dtoToUser(inputDto);

        boolean usernameExists = userRepository.existsByUsernameIgnoreCase(inputDto.getUsername());
        boolean emailExists = userRepository.existsByEmailIgnoreCase(inputDto.getEmail());

        if (usernameExists && emailExists) {
            throw new InvalidInputException("Username: " + inputDto.getUsername().toLowerCase() + " and email: "
                    + inputDto.getEmail().toLowerCase() + "are already taken");
        } else if (usernameExists) {
            throw new InvalidInputException("Username: " + inputDto.getUsername().toLowerCase() + " is already taken");
        } else if (emailExists) {
            throw new InvalidInputException("Email:" + inputDto.getEmail().toLowerCase() + " is already taken");
        } else {
            user.addAuthority(new Authority(user.getUsername(), "ROLE_USER"));
            userRepository.save(user);

            return userToDto(user);
        }
    }

    public String deleteUser(String username) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        if (user.getUsername().equalsIgnoreCase("mmesander")) {
            throw new BadRequestException("Can't remove user: " + user.getUsername());
        }

        userRepository.deleteById(username);

        return "User: " + username + " is deleted";
    }

    // Relation - Authorities Methods
    public Set<Authority> getUserAuthorities(String username) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        UserDto userDto = userToDto(user);

        return userDto.getAuthorities();
    }

    public UserDto assignAuthorityToUser(String username, String authority) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        Optional<Authority> optionalAuthority = authorityRepository.findAuthoritiesByAuthorityContainsIgnoreCaseAndUsernameIgnoreCase(username, authority);
        UserDto userDto = null;

        if (user != null && optionalAuthority.isPresent()) {
            user.addAuthority(new Authority(username, authority));

            userRepository.save(user);

            userDto = userToDto(user);
        }

        return userDto;
    }

    public String removeAuthorityFromUser(String username, String authority) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        Authority toRemove = user.getAuthorities().stream()
                .filter(a -> a.getAuthority().equalsIgnoreCase(authority))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("user: " + username + " does not have authority "
                        + authority.toUpperCase()));

        long count = 0;

        if (toRemove.getUsername() != null) {
            List<User> users = userRepository.findAll();

            if (!users.isEmpty()) {
                for (User user1 : users) {
                    for (Authority checkAuthority : user1.getAuthorities()) {
                        if (checkAuthority.getAuthority().equalsIgnoreCase(authority)) {
                            count++;
                            break;
                        }
                    }
                }
            }
        }

        if (count <= 1) {
            throw new BadRequestException("At least 1 user must have the authority: " + authority.toUpperCase());
        } else {
            user.removeAuthority(toRemove);
            userRepository.save(user);

            return "Authority " + authority.toUpperCase() + " is removed from user: " + username;
        }
    }
}
