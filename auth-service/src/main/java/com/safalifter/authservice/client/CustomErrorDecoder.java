package com.safalifter.authservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.exc.ValidationException;
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
        try (InputStream body = response.body().asInputStream()) {
            String bodyString = IOUtils.toString(body, StandardCharsets.UTF_8);
            Map<String, Object> errors = mapper.readValue(bodyString, Map.class);

            if (response.status() == 400) {
                if (isValidationErrorMap(errors)) {
                    Map<String, String> validationErrors = convertToStringMap(errors);
                    return ValidationException.builder()
                            .validationErrors(validationErrors).build();
                } else {
                    String errorMessage = extractErrorMessage(errors, response.status());
                    return GenericErrorResponse
                            .builder()
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .message(errorMessage)
                            .build();
                }
            } else {
                String errorMessage = extractErrorMessage(errors, response.status());
                return GenericErrorResponse
                        .builder()
                        .httpStatus(HttpStatus.valueOf(response.status()))
                        .message(errorMessage)
                        .build();
            }

        } catch (IOException exception) {
            log.error("Failed to decode Feign error response for methodKey={}, status={}",
                    methodKey, response.status(), exception);
            return GenericErrorResponse.builder()
                    .httpStatus(HttpStatus.valueOf(response.status()))
                    .message("Service error: " + HttpStatus.valueOf(response.status()).getReasonPhrase())
                    .build();
        }
    }

    private boolean isValidationErrorMap(Map<String, Object> errors) {
        if (errors == null || errors.isEmpty()) {
            return false;
        }
        return !errors.containsKey("error");
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

    @SuppressWarnings("unchecked")
    private Map<String, String> convertToStringMap(Map<String, Object> source) {
        if (source == null) {
            return java.util.Collections.emptyMap();
        }
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value != null ? value.toString() : "");
        }
        return result;
    }
}