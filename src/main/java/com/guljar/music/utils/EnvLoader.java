package com.guljar.music.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvLoader {
    public static void loadEnvVariables() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        System.out.println(" .env variables loaded.");
    }
}
