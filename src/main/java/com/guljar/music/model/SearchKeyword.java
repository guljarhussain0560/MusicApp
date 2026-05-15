package com.guljar.music.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Setter
@Getter
@NoArgsConstructor
@Table (name = "search_keyword")
public class SearchKeyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String keyword;

    /** Primary mood detected from the image (e.g., "melancholic", "joyful") */
    private String mood;

    /** Suggested genre (e.g., "Lo-fi", "Classical") */
    private String genre;

    /** Energy level: "low", "medium", "high" */
    private String energy;

    /** Scene description (e.g., "sunset at the beach") */
    private String scene;

    /** Timestamp of when this search was performed */
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

