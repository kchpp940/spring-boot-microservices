package com.safalifter.jobservice.request.favorite;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class FavoriteRequest {
    @NotBlank(message = "Job id is required")
    private String jobId;
}
