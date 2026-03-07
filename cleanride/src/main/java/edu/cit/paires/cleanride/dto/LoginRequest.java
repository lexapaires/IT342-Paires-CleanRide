package edu.cit.paires.cleanride.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}