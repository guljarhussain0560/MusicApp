package com.guljar.music.config;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvLoader {

    public static void loadEnvVariables() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        dotenv.entries().forEach((entry) -> {
            String key = entry.getKey();
            // Use existing system env variable if present, otherwise fallback to .env
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, entry.getValue());
            }
        });
    }
}
