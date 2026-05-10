package com.safalifter.jobservice.controller;

import com.safalifter.jobservice.dto.FavoriteDto;
import com.safalifter.jobservice.dto.JobDto;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Favorite;
import com.safalifter.jobservice.request.favorite.FavoriteRequest;
import com.safalifter.jobservice.service.FavoriteService;
import com.safalifter.jobservice.service.JobService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.security.Principal;
import java.util.Set;

@RestController
@RequestMapping("/v1/job-service/favorite")
@RequiredArgsConstructor
public class FavoriteController {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "creationTimestamp", "updateTimestamp"
    );

    private final FavoriteService favoriteService;
    private final JobService jobService;
    private final ModelMapper modelMapper;

    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    ResponseEntity<FavoriteDto> addFavorite(@Valid @RequestBody FavoriteRequest request, Principal principal) {
        Favorite favorite = favoriteService.addFavorite(principal.getName(), request.getJobId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(modelMapper.map(favorite, FavoriteDto.class));
    }

    @DeleteMapping("/{jobId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    ResponseEntity<Void> removeFavorite(@PathVariable String jobId, Principal principal) {
        try {
            favoriteService.removeFavorite(principal.getName(), jobId);
            return ResponseEntity.ok().build();
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    ResponseEntity<Page<JobDto>> getUserFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "creationTimestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            Principal principal) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        String validatedSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "creationTimestamp";

        Sort.Direction validatedDirection;
        try {
            validatedDirection = Sort.Direction.valueOf(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            validatedDirection = Sort.Direction.DESC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(validatedDirection, validatedSortBy));
        Page<Favorite> favorites = favoriteService.getUserFavorites(principal.getName(), pageable);
        Page<JobDto> jobDtos = favorites.map(favorite ->
                modelMapper.map(jobService.getJobById(favorite.getJobId()), JobDto.class));
        return ResponseEntity.ok(jobDtos);
    }
}
