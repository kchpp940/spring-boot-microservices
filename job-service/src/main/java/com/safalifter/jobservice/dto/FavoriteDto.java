package com.safalifter.jobservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FavoriteDto {
    private String id;
    private String userId;
    private String jobId;
    private LocalDateTime creationTimestamp;
}
