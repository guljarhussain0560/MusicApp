package com.guljar.music.config;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvLoader {

    public static void loadEnvVariables() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        dotenv.entries().forEach((entry) -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });
    }
}
