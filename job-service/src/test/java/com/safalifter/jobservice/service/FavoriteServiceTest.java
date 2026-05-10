package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.exc.DuplicateFavoriteException;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.exc.UnauthorizedException;
import com.safalifter.jobservice.model.Favorite;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.FavoriteRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private JobService jobService;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private FavoriteService favoriteService;

    private String testUsername;
    private String testUserId;
    private String testJobId;
    private UserDto testUserDto;
    private Job testJob;
    private Favorite testFavorite;

    @BeforeEach
    void setUp() {
        testUsername = "testuser";
        testUserId = "user-123";
        testJobId = "job-123";

        testUserDto = new UserDto();
        testUserDto.setId(testUserId);
        testUserDto.setUsername(testUsername);

        testJob = Job.builder()
                .name("Test Job")
                .description("Test Description")
                .build();
        ReflectionTestUtils.setField(testJob, "id", testJobId);

        testFavorite = Favorite.builder()
                .userId(testUserId)
                .jobId(testJobId)
                .build();
        ReflectionTestUtils.setField(testFavorite, "id", "fav-123");
    }

    @Test
    @DisplayName("addFavorite - 正常收藏成功")
    void testAddFavorite_Success() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);
        when(favoriteRepository.existsByUserIdAndJobId(testUserId, testJobId)).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class))).thenReturn(testFavorite);

        Favorite result = favoriteService.addFavorite(testUsername, testJobId);

        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(testJobId, result.getJobId());
        verify(favoriteRepository).save(argThat(fav ->
                testUserId.equals(fav.getUserId()) && testJobId.equals(fav.getJobId())
        ));
    }

    @Test
    @DisplayName("addFavorite - 用户不存在时抛出 NotFoundException")
    void testAddFavorite_UserNotFound_ThrowsNotFoundException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenThrow(FeignException.NotFound.class);
        when(jobService.getJobById(testJobId)).thenReturn(testJob);

        assertThrows(NotFoundException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    @DisplayName("addFavorite - 岗位不存在时抛出 NotFoundException")
    void testAddFavorite_JobNotFound_ThrowsNotFoundException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenThrow(new NotFoundException("Job not found"));

        assertThrows(NotFoundException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    @DisplayName("addFavorite - 重复收藏时抛出 DuplicateFavoriteException")
    void testAddFavorite_Duplicate_ThrowsDuplicateFavoriteException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);
        when(favoriteRepository.existsByUserIdAndJobId(testUserId, testJobId)).thenReturn(true);

        assertThrows(DuplicateFavoriteException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    @DisplayName("addFavorite - 并发场景下数据库唯一约束冲突时抛出 DuplicateFavoriteException")
    void testAddFavorite_ConcurrentUniqueConstraint_ThrowsDuplicateFavoriteException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);
        when(favoriteRepository.existsByUserIdAndJobId(testUserId, testJobId)).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class))).thenThrow(
                new DataIntegrityViolationException("Unique constraint violation")
        );

        assertThrows(DuplicateFavoriteException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
    }

    @Test
    @DisplayName("addFavorite - 任何 DataIntegrityViolationException 都稳定兜底为 DuplicateFavoriteException")
    void testAddFavorite_AnyDataIntegrityViolation_StableFallback() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);
        when(favoriteRepository.existsByUserIdAndJobId(testUserId, testJobId)).thenReturn(false);
        when(favoriteRepository.save(any(Favorite.class))).thenThrow(
                new DataIntegrityViolationException("some database error without table name")
        );

        DuplicateFavoriteException exception = assertThrows(
                DuplicateFavoriteException.class,
                () -> favoriteService.addFavorite(testUsername, testJobId)
        );

        assertEquals("Job is already in favorites", exception.getMessage());
    }

    @Test
    @DisplayName("addFavorite - 用户验证失败时抛出 UnauthorizedException")
    void testAddFavorite_UserValidationFailed_ThrowsUnauthorizedException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenThrow(
                FeignException.errorStatus("getUserInfoByUsername",
                        feign.Response.builder().status(500).build())
        );

        assertThrows(UnauthorizedException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    @DisplayName("addFavorite - 用户名为空时抛出 UnauthorizedException")
    void testAddFavorite_NullUsername_ThrowsUnauthorizedException() {
        assertThrows(UnauthorizedException.class, () -> favoriteService.addFavorite(null, testJobId));
        assertThrows(UnauthorizedException.class, () -> favoriteService.addFavorite("", testJobId));
        assertThrows(UnauthorizedException.class, () -> favoriteService.addFavorite("   ", testJobId));
    }

    @Test
    @DisplayName("addFavorite - userService 返回 null body 时抛出 NotFoundException")
    void testAddFavorite_NullUserDto_ThrowsNotFoundException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(null));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);

        assertThrows(NotFoundException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    @DisplayName("addFavorite - userService 返回无 userId 时抛出 NotFoundException")
    void testAddFavorite_UserDtoWithNullId_ThrowsNotFoundException() {
        UserDto userWithoutId = new UserDto();
        userWithoutId.setUsername(testUsername);
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(userWithoutId));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);

        assertThrows(NotFoundException.class, () -> favoriteService.addFavorite(testUsername, testJobId));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    @DisplayName("removeFavorite - 正常取消收藏成功")
    void testRemoveFavorite_Success() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);
        when(favoriteRepository.existsByUserIdAndJobId(testUserId, testJobId)).thenReturn(true);

        assertDoesNotThrow(() -> favoriteService.removeFavorite(testUsername, testJobId));
        verify(favoriteRepository).deleteByUserIdAndJobId(testUserId, testJobId);
    }

    @Test
    @DisplayName("removeFavorite - 收藏不存在时抛出 NotFoundException (message: Favorite not found)")
    void testRemoveFavorite_FavoriteNotFound_ThrowsNotFoundException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenReturn(testJob);
        when(favoriteRepository.existsByUserIdAndJobId(testUserId, testJobId)).thenReturn(false);

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> favoriteService.removeFavorite(testUsername, testJobId)
        );

        assertEquals("Favorite not found", exception.getMessage());
        verify(favoriteRepository, never()).deleteByUserIdAndJobId(anyString(), anyString());
    }

    @Test
    @DisplayName("removeFavorite - 用户不存在时抛出 NotFoundException (resolveUser 抛出)")
    void testRemoveFavorite_UserNotFound_ThrowsNotFoundException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenThrow(FeignException.NotFound.class);

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> favoriteService.removeFavorite(testUsername, testJobId)
        );

        assertTrue(exception.getMessage().contains(testUsername), "Exception message should contain username");
        verify(favoriteRepository, never()).deleteByUserIdAndJobId(anyString(), anyString());
    }

    @Test
    @DisplayName("removeFavorite - 岗位不存在时抛出 NotFoundException (message: Job not found)")
    void testRemoveFavorite_JobNotFound_ThrowsNotFoundException() {
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(jobService.getJobById(testJobId)).thenThrow(new NotFoundException("Job not found"));

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> favoriteService.removeFavorite(testUsername, testJobId)
        );

        assertEquals("Job not found", exception.getMessage());
        verify(favoriteRepository, never()).deleteByUserIdAndJobId(anyString(), anyString());
    }

    @Test
    @DisplayName("getUserFavorites - 正常分页查询成功")
    void testGetUserFavorites_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Favorite> favorites = Collections.singletonList(testFavorite);
        Page<Favorite> expectedPage = new PageImpl<>(favorites, pageable, 1);

        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(favoriteRepository.findByUserId(testUserId, pageable)).thenReturn(expectedPage);

        Page<Favorite> result = favoriteService.getUserFavorites(testUsername, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(testFavorite, result.getContent().get(0));
    }

    @Test
    @DisplayName("getUserFavorites - 用户不存在时抛出 NotFoundException")
    void testGetUserFavorites_UserNotFound_ThrowsNotFoundException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userServiceClient.getUserInfoByUsername(testUsername)).thenThrow(FeignException.NotFound.class);

        assertThrows(NotFoundException.class, () -> favoriteService.getUserFavorites(testUsername, pageable));
        verify(favoriteRepository, never()).findByUserId(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("getUserFavorites - 空收藏列表返回空 Page")
    void testGetUserFavorites_EmptyList_ReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Favorite> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(userServiceClient.getUserInfoByUsername(testUsername)).thenReturn(ResponseEntity.ok(testUserDto));
        when(favoriteRepository.findByUserId(testUserId, pageable)).thenReturn(emptyPage);

        Page<Favorite> result = favoriteService.getUserFavorites(testUsername, pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }
}
