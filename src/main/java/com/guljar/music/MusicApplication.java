package com.guljar.music;

import com.guljar.music.config.EnvLoader;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.guljar.music.repo")
@EntityScan(basePackages = "com.guljar.music.model")
public class MusicApplication {

    public static void main(String[] args) {
        com.guljar.music.utils.EnvLoader.loadEnvVariables();
        SpringApplication.run(MusicApplication.class, args);
    }

}
