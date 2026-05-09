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

    public Job createJob(JobCreateRequest request, MultipartFile file) {
        Category category = categoryService.getCategoryById(request.getCategoryId());

        String imageId = null;

        if (file != null)
            imageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();

        return jobRepository.save(Job.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .keys(Optional.of(List.of(request.getKeys()))
                        .orElse(new ArrayList<>()))
                .imageId(imageId)
                .build());
    }

    public List<Job> getAll() {
        return jobRepository.findAll();
    }

    public Job getJobById(String id) {
        return findJobById(id);
    }

    public Job updateJob(JobUpdateRequest request, MultipartFile file) {
        Job toUpdate = findJobById(request.getId());

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
            String newImageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();
            if (newImageId != null) {
                String oldImageId = toUpdate.getImageId();
                if (oldImageId != null && !oldImageId.isEmpty()) {
                    try {
                        fileStorageClient.deleteImageFromFileSystem(oldImageId);
                    } catch (Exception e) {
                        log.warn("Failed to delete old image with id: {}", oldImageId, e);
                    }
                }
                toUpdate.setImageId(newImageId);
            }
        }

        return jobRepository.save(toUpdate);
    }

    public void deleteJobById(String id) {
        jobRepository.deleteById(id);
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
}
