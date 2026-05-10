package com.safalifter.jobservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.jobservice.dto.FavoriteDto;
import com.safalifter.jobservice.dto.JobDto;
import com.safalifter.jobservice.exc.DuplicateFavoriteException;
import com.safalifter.jobservice.exc.GeneralExceptionHandler;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Favorite;
import com.safalifter.jobservice.request.favorite.FavoriteRequest;
import com.safalifter.jobservice.service.FavoriteService;
import com.safalifter.jobservice.service.JobService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FavoriteService favoriteService;

    @MockBean
    private JobService jobService;

    @MockBean
    private ModelMapper modelMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SECRET = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private Key signKey;
    private String userToken;
    private String testUsername;
    private String testJobId;
    private Favorite testFavorite;
    private FavoriteDto testFavoriteDto;
    private JobDto testJobDto;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        signKey = Keys.hmacShaKeyFor(keyBytes);
        testUsername = "testuser";
        testJobId = "job-123";

        userToken = Jwts.builder()
                .setSubject(testUsername)
                .setIssuer("ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();

        testFavorite = Favorite.builder()
                .userId("user-123")
                .jobId(testJobId)
                .build();
        ReflectionTestUtils.setField(testFavorite, "id", "fav-123");

        testFavoriteDto = new FavoriteDto();
        testFavoriteDto.setId("fav-123");
        testFavoriteDto.setUserId("user-123");
        testFavoriteDto.setJobId(testJobId);

        testJobDto = new JobDto();
        testJobDto.setId(testJobId);
        testJobDto.setName("Test Job");
    }

    @Test
    @DisplayName("POST /favorite - @Valid 缺少 jobId 返回 400 Bad Request")
    void testAddFavorite_MissingJobId_Returns400() throws Exception {
        FavoriteRequest request = new FavoriteRequest();

        mockMvc.perform(post("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /favorite - 正常收藏返回 201 Created")
    void testAddFavorite_Success_Returns201() throws Exception {
        FavoriteRequest request = new FavoriteRequest();
        request.setJobId(testJobId);

        when(favoriteService.addFavorite(testUsername, testJobId)).thenReturn(testFavorite);
        when(modelMapper.map(testFavorite, FavoriteDto.class)).thenReturn(testFavoriteDto);

        mockMvc.perform(post("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("fav-123"))
                .andExpect(jsonPath("$.jobId").value(testJobId));
    }

    @Test
    @DisplayName("POST /favorite - 重复收藏返回 409 Conflict")
    void testAddFavorite_Duplicate_Returns409() throws Exception {
        FavoriteRequest request = new FavoriteRequest();
        request.setJobId(testJobId);

        when(favoriteService.addFavorite(testUsername, testJobId))
                .thenThrow(new DuplicateFavoriteException("Job is already in favorites"));

        mockMvc.perform(post("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Job is already in favorites"));
    }

    @Test
    @DisplayName("DELETE /favorite/{jobId} - 取消不存在收藏返回 404 Not Found")
    void testRemoveFavorite_NotFound_Returns404() throws Exception {
        doThrow(new NotFoundException("Favorite not found"))
                .when(favoriteService).removeFavorite(testUsername, testJobId);

        mockMvc.perform(delete("/v1/job-service/favorite/" + testJobId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /favorite/{jobId} - 正常取消返回 200 OK")
    void testRemoveFavorite_Success_Returns200() throws Exception {
        doNothing().when(favoriteService).removeFavorite(testUsername, testJobId);

        mockMvc.perform(delete("/v1/job-service/favorite/" + testJobId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /favorite - 非法 sortBy 回退到 creationTimestamp")
    void testGetFavorites_InvalidSortBy_FallsBack() throws Exception {
        Page<Favorite> favoritePage = new PageImpl<>(Collections.singletonList(testFavorite));
        when(favoriteService.getUserFavorites(eq(testUsername), any(Pageable.class))).thenReturn(favoritePage);
        when(modelMapper.map(any(), eq(JobDto.class))).thenReturn(testJobDto);

        mockMvc.perform(get("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .param("sortBy", "invalidField")
                        .param("direction", "DESC"))
                .andExpect(status().isOk());

        verify(favoriteService).getUserFavorites(eq(testUsername), argThat(pageable ->
                "creationTimestamp".equals(pageable.getSort().iterator().next().getProperty())
        ));
    }

    @Test
    @DisplayName("GET /favorite - 非法 direction 回退到 DESC")
    void testGetFavorites_InvalidDirection_FallsBackToDesc() throws Exception {
        Page<Favorite> favoritePage = new PageImpl<>(Collections.singletonList(testFavorite));
        when(favoriteService.getUserFavorites(eq(testUsername), any(Pageable.class))).thenReturn(favoritePage);
        when(modelMapper.map(any(), eq(JobDto.class))).thenReturn(testJobDto);

        mockMvc.perform(get("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .param("sortBy", "id")
                        .param("direction", "INVALID"))
                .andExpect(status().isOk());

        verify(favoriteService).getUserFavorites(eq(testUsername), argThat(pageable ->
                org.springframework.data.domain.Sort.Direction.DESC.equals(pageable.getSort().iterator().next().getDirection())
        ));
    }

    @Test
    @DisplayName("GET /favorite - 合法 sortBy=id 使用该字段")
    void testGetFavorites_ValidSortBy_UsesField() throws Exception {
        Page<Favorite> favoritePage = new PageImpl<>(Collections.singletonList(testFavorite));
        when(favoriteService.getUserFavorites(eq(testUsername), any(Pageable.class))).thenReturn(favoritePage);
        when(modelMapper.map(any(), eq(JobDto.class))).thenReturn(testJobDto);

        mockMvc.perform(get("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .param("sortBy", "id")
                        .param("direction", "ASC"))
                .andExpect(status().isOk());

        verify(favoriteService).getUserFavorites(eq(testUsername), argThat(pageable ->
                "id".equals(pageable.getSort().iterator().next().getProperty())
        ));
    }

    @Test
    @DisplayName("GET /favorite - 无 Token 返回 403 Forbidden")
    void testGetFavorites_NoToken_Returns403() throws Exception {
        mockMvc.perform(get("/v1/job-service/favorite"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /favorite - 请求体不包含 userId，使用 Principal 的 username")
    void testAddFavorite_RequestHasNoUserId_UsesPrincipal() throws Exception {
        FavoriteRequest request = new FavoriteRequest();
        request.setJobId(testJobId);

        when(favoriteService.addFavorite(anyString(), eq(testJobId))).thenReturn(testFavorite);
        when(modelMapper.map(testFavorite, FavoriteDto.class)).thenReturn(testFavoriteDto);

        mockMvc.perform(post("/v1/job-service/favorite")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(favoriteService).addFavorite(eq(testUsername), eq(testJobId));
    }
}
