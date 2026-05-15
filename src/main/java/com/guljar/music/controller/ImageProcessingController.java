package com.guljar.music.controller;

import com.guljar.music.dto.ImageAnalysisResult;
import com.guljar.music.dto.MusicRecommendationResponse;
import com.guljar.music.dto.Song;
import com.guljar.music.model.User;
import com.guljar.music.processor.ImageProcessor;
import com.guljar.music.repo.UserRepository;
import com.guljar.music.service.MusicSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/image")
public class ImageProcessingController {

    @Autowired
    private ImageProcessor imageProcessor;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MusicSearchService musicSearchService;

    @PostMapping("/process")
    public ResponseEntity<?> processImage(@RequestParam("image") MultipartFile image){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            //Find user from database
            User user = userRepository.findByUserName(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            //Process image to generate structured analysis (mood, genre, search queries)
            ImageAnalysisResult analysis = imageProcessor.process(image, user);

            if(analysis == null || analysis.getSearchQueries() == null || analysis.getSearchQueries().isEmpty()){
                return ResponseEntity.badRequest().body("Failed to analyze image for music recommendations");
            }

            //Search for songs using the AI-generated optimized queries
            List<Song> songs = musicSearchService.searchSongs(analysis);

            if(songs.isEmpty()){
                return ResponseEntity.ok(new MusicRecommendationResponse(analysis, songs, 0));
            }

            return ResponseEntity.ok(new MusicRecommendationResponse(analysis, songs, songs.size()));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing image: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("An unexpected error occurred: " + e.getMessage());
        }

    }
}
