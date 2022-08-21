package ru.aiefu.discordium.mclog;

/**
 * MIT Licensed
 * https://github.com/aternosorg/mclogs-java
 */

import com.google.gson.Gson;

public class APIResponse {
    public final boolean success;
    public final String id;
    public final String url;
    public final String error;

    public APIResponse(boolean success, String id, String url, String error) {
        this.success = success;
        this.id = id;
        this.url = url;
        this.error = error;
    }

    public static APIResponse parse(String json){
        Gson g = new Gson();
        return g.fromJson(json, APIResponse.class);
    }
}
