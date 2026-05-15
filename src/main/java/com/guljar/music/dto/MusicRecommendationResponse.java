package com.guljar.music.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper that includes both the AI analysis context
 * and the resulting song recommendations, giving the frontend
 * rich information to display alongside results.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MusicRecommendationResponse {

    /** The mood/genre/scene detected from the image */
    private ImageAnalysisResult analysis;

    /** Deduplicated, relevance-sorted list of song recommendations */
    private List<Song> songs;

    /** Total songs found before deduplication */
    private int totalResultsBeforeFilter;
}
