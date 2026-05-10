package com.safalifter.jobservice.service;

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
    private final ModelMapper modelMapper;
    private final FileReferenceCoordinator fileReferenceCoordinator;

    private static final String ENTITY_TYPE_JOB = "job";

    @Transactional
    public Job createJob(JobCreateRequest request, MultipartFile file) {
        Category category = categoryService.getCategoryById(request.getCategoryId());

        return fileReferenceCoordinator.executeCreate(
                ENTITY_TYPE_JOB,
                file,
                imageId -> {
                    Job toSave = Job.builder()
                            .name(request.getName())
                            .description(request.getDescription())
                            .category(category)
                            .keys(Optional.of(List.of(request.getKeys()))
                                    .orElse(new ArrayList<>()))
                            .imageId(imageId)
                            .build();
                    return jobRepository.save(toSave);
                },
                Job::getId
        );
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

        return fileReferenceCoordinator.executeUpdate(
                ENTITY_TYPE_JOB,
                oldImageId,
                file,
                newImageId -> {
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

                    if (newImageId != null) {
                        toUpdate.setImageId(newImageId);
                    }

                    return jobRepository.save(toUpdate);
                },
                Job::getId
        );
    }

    @Transactional
    public void deleteJobById(String id) {
        Job toDelete = findJobById(id);
        String imageId = toDelete.getImageId();

        fileReferenceCoordinator.executeDelete(
                ENTITY_TYPE_JOB,
                id,
                imageId,
                () -> jobRepository.deleteById(id)
        );
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
