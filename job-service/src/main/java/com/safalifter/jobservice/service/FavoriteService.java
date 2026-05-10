package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.adapter.UserServiceClientAdapter;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.exc.DuplicateFavoriteException;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.exc.UnauthorizedException;
import com.safalifter.jobservice.model.Favorite;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final JobService jobService;
    private final UserServiceClientAdapter userServiceClientAdapter;

    @Transactional
    public Favorite addFavorite(String username, String jobId) {
        UserDto user = resolveUser(username);
        Job job = jobService.getJobById(jobId);

        if (favoriteRepository.existsByUserIdAndJobId(user.getId(), job.getId())) {
            throw new DuplicateFavoriteException("Job is already in favorites");
        }

        try {
            return favoriteRepository.save(Favorite.builder()
                    .userId(user.getId())
                    .jobId(job.getId())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFavoriteException("Job is already in favorites");
        }
    }

    @Transactional
    public void removeFavorite(String username, String jobId) {
        UserDto user = resolveUser(username);
        jobService.getJobById(jobId);

        if (!favoriteRepository.existsByUserIdAndJobId(user.getId(), jobId)) {
            throw new NotFoundException("Favorite not found");
        }

        favoriteRepository.deleteByUserIdAndJobId(user.getId(), jobId);
    }

    public Page<Favorite> getUserFavorites(String username, Pageable pageable) {
        UserDto user = resolveUser(username);
        return favoriteRepository.findByUserId(user.getId(), pageable);
    }

    private UserDto resolveUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return userServiceClientAdapter.getUserInfoByUsernameOrThrow(username);
    }
}
