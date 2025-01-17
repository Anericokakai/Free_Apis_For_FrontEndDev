package com.farmdigital.nerddevs.Registration.service;

import com.farmdigital.nerddevs.Registration.Dto.AuthenticationDto;
import com.farmdigital.nerddevs.Registration.Dto.FarmerRegistrationDto;
import com.farmdigital.nerddevs.Registration.Exceptions.UserAlreadyExistException;
import com.farmdigital.nerddevs.Mails.Service.ResetPasswordEmailComposer;
import com.farmdigital.nerddevs.Mails.Service.VerificationEmailComposer;
import com.farmdigital.nerddevs.Registration.model.Farmer;
import com.farmdigital.nerddevs.Registration.model.Roles;
import com.farmdigital.nerddevs.Registration.Repository.FarmerRepository;
import com.farmdigital.nerddevs.Registration.Repository.RolesRepository;
import com.farmdigital.nerddevs.Registration.security.JwtServices;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor

public class UserRegistrationService {
    private final FarmerRepository farmerRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtServices jwtServices;
    private final RolesRepository rolesRepository;
    private final Map<String, String> response = new HashMap<>();
    private  final VerificationEmailComposer verificationEmailComposer;
    private  final ResetPasswordEmailComposer resetPasswordEmailComposer;
    private final static Logger LOGGER = LoggerFactory.getLogger(UserRegistrationService.class);
    public Map<String, String> saveUser(FarmerRegistrationDto user) throws Exception {

        Roles role = rolesRepository.findByName("USER");

        //verify validity of the entered number.
        verifyPhoneNumber(user.getPhoneNumber());

//        ! if the farmer already exist throw an exception
        if (farmerRepository.findByEmail(user.getEmail()).isPresent()) {

            throw new UserAlreadyExistException("user already exist !, please try to log in");
        }
//      !  create a new user
        Farmer newUser = Farmer.builder()
                .name(user.getName())
                .email(user.getEmail())
                .password(passwordEncoder.encode(user.getPassword()))
                .farmerId(createUniqueId(user.getPhoneNumber()))
                .roles(Collections.singletonList(role))
                .phoneNumber(user.getPhoneNumber())
                .registrationTime(timeCreatedAccout())
                .build();
        farmerRepository.save(newUser);
//        ! validate emails for the users;
        verificationEmailComposer.sendVerificationEmail(newUser.getEmail());
        response.put("message", "user created successfully, please check your email to verify  your account !");

        return response;

    }

    public void verifyPhoneNumber(String phoneNumber) throws NumberFormatException
    {
            int number = Integer.parseInt(phoneNumber);
    }

//    ! unique email constraint
    public String timeCreatedAccout() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyy 'at' hh:mm a");
        return formatter.format(LocalDateTime.now());
    }
    //    ! method that helps us create a unique id for the user
    public String createUniqueId(String  phoneNumber) {
        String uniqueId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddss"));
        return "FARMER-" + phoneNumber.substring(7) + uniqueId;
    }

//    ! method to check the time when the user created an account

    

    public Map<String ,Object > authenticateauser(AuthenticationDto req) {
        Map<String ,Object > response=new HashMap<>();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        req.getEmail(),
                        req.getPassword()
                )
        );

        Farmer user;
        if (farmerRepository.findByEmail(req.getEmail()).isPresent()) {
            user = farmerRepository.findByEmail(req.getEmail()).get();
        } else {
            throw new EntityNotFoundException("invalid login credentials");
        }


        if(!user.isVerified()){
            //        ! validate emails for the users;
            verificationEmailComposer.sendVerificationEmail(user.getEmail());
            response.put("verified",false);
response.put("errorMessage","you have not verified your account , please check your email to verify your account!!");
return response;
        }
//       Generate token

        String token=jwtServices.generateAToken(user);
        response.put("token",token);
        return response;

    }
//    todo test this email sending method


    public Map<String, String> forgotPassword(String email) throws EntityNotFoundException {

        Farmer farmer = farmerRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("invalid email adress"));
//! send email to the user to change the password
        resetPasswordEmailComposer.sendResetPasswordEmail(email,farmer.getName());
        response.put("message", "check your email address for a link to change  your password");
        return response;
    }

//    ! change password
    public Map<String ,Object> resetPassword(String password,String  token) {

        Map<String ,Object > message=new HashMap<>();
//      !  check the password strength
      String   regexp = "^.*(?=.{8,})(?=..*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$";
     String errorMessage = "password must contain least 8 character with an upper case , a special" +
                        " character(!,@,$,&,%) and a number ";

     if(!password.matches(regexp)){
           message.put("errorMessage",errorMessage);
           return  message;
     }

;
        String  email= jwtServices.extractUsername(token);
        jwtServices.CheckTokenExpiryForAccountVerification(token,email);
//        ! find and update the use
        Farmer farmerToUpdate=farmerRepository.findByEmail(email).orElseThrow(()-> new EntityNotFoundException("Invalid credentials "));
        farmerToUpdate.setPassword(passwordEncoder.encode(password));
        farmerRepository.save(farmerToUpdate);
        message.put("message","user password updated successfully !");
        return message;
    }
}
