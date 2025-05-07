package com.guljar.music.repo;

import com.guljar.music.model.SearchKeyword;
import com.guljar.music.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface SearchKeywordRepository extends JpaRepository<SearchKeyword, Long> {
    List<SearchKeyword> findByUser(User user);
}
