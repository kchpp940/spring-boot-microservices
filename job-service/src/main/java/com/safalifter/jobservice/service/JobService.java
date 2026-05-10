package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Category;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.JobRepository;
import com.safalifter.jobservice.request.job.JobCreateRequest;
import com.safalifter.jobservice.request.job.JobUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {
    private final JobRepository jobRepository;
    private final CategoryService categoryService;
    private final FileStorageClient fileStorageClient;
    private final ModelMapper modelMapper;

    private static final String ENTITY_TYPE_JOB = "job";

    @Transactional
    public Job createJob(JobCreateRequest request, MultipartFile file) {
        Category category = categoryService.getCategoryById(request.getCategoryId());

        String imageId = null;
        Job savedJob = null;

        try {
            if (file != null) {
                imageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();
            }

            Job toSave = Job.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .category(category)
                    .keys(Optional.of(List.of(request.getKeys()))
                            .orElse(new ArrayList<>()))
                    .imageId(imageId)
                    .build();

            savedJob = jobRepository.save(toSave);

            if (imageId != null) {
                bindNewImage(savedJob.getId(), imageId);
            }

            return savedJob;
        } catch (Exception e) {
            if (imageId != null && savedJob == null) {
                rollbackUploadedFile(imageId);
            }
            if (savedJob != null && imageId != null) {
                try {
                    jobRepository.deleteById(savedJob.getId());
                } catch (Exception ex) {
                    log.warn("Failed to cleanup job after failure: {}", savedJob.getId(), ex);
                }
                rollbackUploadedFile(imageId);
            }
            throw e;
        }
    }

    public List<Job> getAll() {
        return jobRepository.findAll();
    }

    public Job getJobById(String id) {
        return findJobById(id);
    }

    @Transactional
    public Job updateJob(JobUpdateRequest request, MultipartFile file) {
        Job toUpdate = findJobById(request.getId());

        String oldImageId = toUpdate.getImageId();
        String newImageId = null;
        boolean newImageBound = false;

        try {
            modelMapper.map(request, toUpdate);

            if (request.getCategoryId() != null) {
                String currentCategoryId = toUpdate.getCategory() != null
                        ? toUpdate.getCategory().getId()
                        : null;
                if (!request.getCategoryId().equals(currentCategoryId)) {
                    Category newCategory = categoryService.getCategoryById(request.getCategoryId());
                    toUpdate.setCategory(newCategory);
                }
            }

            if (file != null) {
                newImageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();
                if (newImageId != null) {
                    bindNewImage(toUpdate.getId(), newImageId);
                    newImageBound = true;
                    toUpdate.setImageId(newImageId);
                }
            }

            Job savedJob = jobRepository.save(toUpdate);

            if (file != null && newImageId != null && oldImageId != null 
                    && !oldImageId.trim().isEmpty() && !oldImageId.equals(newImageId)) {
                try {
                    safeUnbindAndDeleteImage(toUpdate.getId(), oldImageId, ENTITY_TYPE_JOB);
                } catch (Exception e) {
                    log.warn("Failed to unbind old image after update for job: {}", toUpdate.getId(), e);
                }
            }

            return savedJob;
        } catch (Exception e) {
            if (newImageId != null) {
                if (newImageBound) {
                    try {
                        safeUnbindImage(toUpdate.getId(), newImageId, ENTITY_TYPE_JOB);
                    } catch (Exception ex) {
                        log.warn("Failed to rollback bind for new image: {}", newImageId, ex);
                    }
                }
                rollbackUploadedFile(newImageId);
            }
            throw e;
        }
    }

    @Transactional
    public void deleteJobById(String id) {
        Job toDelete = findJobById(id);
        
        String imageId = toDelete.getImageId();

        jobRepository.deleteById(id);

        if (imageId != null && !imageId.trim().isEmpty()) {
            try {
                safeUnbindAndDeleteImage(id, imageId, ENTITY_TYPE_JOB);
            } catch (Exception e) {
                log.warn("Failed to cleanup image for job: {}", id, e);
            }
        }
    }

    public List<Job> getJobsByCategoryId(String id) {
        return jobRepository.getJobsByCategoryId(id);
    }

    public List<Job> getJobsThatFitYourNeeds(String needs) {
        String[] keys = needs.replaceAll("\"", "").split(" ");
        HashMap<String, Integer> map = new HashMap<>();
        Arrays.stream(keys).forEach(key -> jobRepository.getJobsByKeysContainsIgnoreCase(key)
                .forEach(job -> {
                    if (map.containsKey(job.getId())) {
                        int count = map.get(job.getId());
                        map.put(job.getId(), count + 1);
                    } else map.put(job.getId(), 1);
                }));
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> findJobById(entry.getKey()))
                .collect(Collectors.toList());
    }

    protected Job findJobById(String id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Job not found"));
    }

    private void safeUnbindImage(String entityId, String fileId, String entityType) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, String> unbindRequest = new HashMap<>();
            unbindRequest.put("fileId", fileId);
            unbindRequest.put("entityType", entityType);
            unbindRequest.put("entityId", entityId);
            fileStorageClient.unbindReference(unbindRequest);
            log.info("Successfully unbound image reference: {} for entity: {}", fileId, entityId);
        } catch (Exception e) {
            log.warn("Failed to unbind image: {} for entity: {}", fileId, entityId, e);
        }
    }

    private void bindNewImage(String entityId, String fileId) {
        try {
            Map<String, String> bindRequest = new HashMap<>();
            bindRequest.put("fileId", fileId);
            bindRequest.put("entityType", ENTITY_TYPE_JOB);
            bindRequest.put("entityId", entityId);
            fileStorageClient.bindReference(bindRequest);
            log.info("Successfully bound job image reference: {}", entityId);
        } catch (Exception e) {
            log.error("Failed to bind job image reference: {}", entityId, e);
            throw e;
        }
    }

    private void safeUnbindAndDeleteImage(String entityId, String fileId, String entityType) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, String> unbindRequest = new HashMap<>();
            unbindRequest.put("fileId", fileId);
            unbindRequest.put("entityType", entityType);
            unbindRequest.put("entityId", entityId);
            
            var response = fileStorageClient.unbindReference(unbindRequest);
            Map<String, Object> body = response.getBody();
            
            if (body != null && Boolean.TRUE.equals(body.get("unbound"))) {
                Integer newCount = (Integer) body.get("referenceCount");
                if (newCount != null && newCount == 0) {
                    try {
                        fileStorageClient.deleteImageFromFileSystem(fileId);
                        log.info("Successfully deleted image: {} for entity: {}", fileId, entityId);
                    } catch (Exception e) {
                        log.warn("Failed to delete image after unbind: {} for entity: {}", fileId, entityId, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to unbind image: {} for entity: {}", fileId, entityId, e);
        }
    }

    private void rollbackUploadedFile(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return;
        }
        try {
            fileStorageClient.deleteImageFromFileSystem(fileId);
            log.info("Rolled back uploaded file: {}", fileId);
        } catch (Exception e) {
            log.warn("Failed to rollback uploaded file: {}", fileId, e);
        }
    }

    private void safeDeleteFile(String fileId) {
        if (fileId != null && !fileId.trim().isEmpty()) {
            try {
                fileStorageClient.deleteImageFromFileSystem(fileId);
            } catch (Exception e) {
                log.warn("Failed to delete file with id: {}", fileId, e);
            }
        }
    }
}
