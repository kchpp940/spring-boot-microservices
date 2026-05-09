package com.safalifter.jobservice.request;

import com.safalifter.jobservice.request.job.JobUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JobUpdateRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("JobUpdateRequest should be valid when id is provided")
    void testValidRequest_WhenIdIsProvided() {
        JobUpdateRequest request = new JobUpdateRequest();
        request.setId("job-123");

        Set<ConstraintViolation<JobUpdateRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("JobUpdateRequest should be invalid when id is null")
    void testInvalidRequest_WhenIdIsNull() {
        JobUpdateRequest request = new JobUpdateRequest();
        request.setId(null);

        Set<ConstraintViolation<JobUpdateRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> "id".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("JobUpdateRequest should be invalid when id is blank")
    void testInvalidRequest_WhenIdIsBlank() {
        JobUpdateRequest request = new JobUpdateRequest();
        request.setId("");

        Set<ConstraintViolation<JobUpdateRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> "id".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("JobUpdateRequest should be valid with all fields set")
    void testValidRequest_WithAllFields() {
        JobUpdateRequest request = new JobUpdateRequest();
        request.setId("job-123");
        request.setName("Test Job");
        request.setDescription("Test Description");
        request.setCategoryId("cat-456");
        request.setKeys(new String[]{"key1", "key2"});

        Set<ConstraintViolation<JobUpdateRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("JobUpdateRequest should have correct field values after setting")
    void testFieldValues() {
        JobUpdateRequest request = new JobUpdateRequest();
        request.setId("job-123");
        request.setName("Updated Job");
        request.setDescription("Updated Description");
        request.setCategoryId("category-456");
        request.setKeys(new String[]{"skill1", "skill2"});

        assertEquals("job-123", request.getId());
        assertEquals("Updated Job", request.getName());
        assertEquals("Updated Description", request.getDescription());
        assertEquals("category-456", request.getCategoryId());
        assertArrayEquals(new String[]{"skill1", "skill2"}, request.getKeys());
    }
}
