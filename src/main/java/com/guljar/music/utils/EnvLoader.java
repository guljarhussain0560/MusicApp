package com.guljar.music.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class EnvLoader {
    public static void loadEnvVariables() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();

            // Only set if not already set by system or Elastic Beanstalk env
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        });

        System.out.println(".env variables loaded (only where not already defined).");
    }
}
