package com.multimediaserver.android.model;

/** FullUserDTO. The person fields can be null (the initial admin has none). */
public class User {
    public Long id;
    public String username;
    public String rol;
    public String status;
    public String name;
    public String lastname;
    public String email;

    public boolean isAdmin() {
        return "ADMIN".equals(rol);
    }
}
