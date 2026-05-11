package com.safalifter.userservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.userservice.exc.GenericErrorResponse;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class CustomErrorDecoder implements ErrorDecoder {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.body() == null) {
            return GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.valueOf(response.status()))
                    .message("Service error: " + HttpStatus.valueOf(response.status()).getReasonPhrase())
                    .build();
        }
        try (InputStream body = response.body().asInputStream()) {
            String bodyString = IOUtils.toString(body, StandardCharsets.UTF_8);
            Map<String, Object> errors = mapper.readValue(bodyString, Map.class);

            String errorMessage = extractErrorMessage(errors, response.status());
            return GenericErrorResponse
                    .builder()
                    .httpStatus(HttpStatus.valueOf(response.status()))
                    .message(errorMessage)
                    .build();

        } catch (IOException exception) {
            log.error("Failed to decode Feign error response for methodKey={}, status={}",
                    methodKey, response.status(), exception);
            return GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.valueOf(response.status()))
                    .message("Service error: " + HttpStatus.valueOf(response.status()).getReasonPhrase())
                    .build();
        }
    }

    private String extractErrorMessage(Map<String, Object> errors, int status) {
        if (errors == null || errors.isEmpty()) {
            return HttpStatus.valueOf(status).getReasonPhrase();
        }
        Object error = errors.get("error");
        if (error instanceof String) {
            return (String) error;
        }
        return HttpStatus.valueOf(status).getReasonPhrase();
    }
}