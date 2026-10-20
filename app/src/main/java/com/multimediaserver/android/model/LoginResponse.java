package com.multimediaserver.android.model;

import com.google.gson.annotations.SerializedName;

/** Body of POST /login. */
public class LoginResponse {
    @SerializedName("Authentication")
    public String token;
    public String username;
    public Long id;
    public String rol;
    public String message;
}
