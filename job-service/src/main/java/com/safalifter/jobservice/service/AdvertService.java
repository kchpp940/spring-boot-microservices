package com.safalifter.jobservice.service;

import com.safalifter.jobservice.client.FileStorageClient;
import com.safalifter.jobservice.client.UserServiceClient;
import com.safalifter.jobservice.dto.UserDto;
import com.safalifter.jobservice.enums.AdvertStatus;
import com.safalifter.jobservice.enums.Advertiser;
import com.safalifter.jobservice.exc.NotFoundException;
import com.safalifter.jobservice.model.Advert;
import com.safalifter.jobservice.model.Job;
import com.safalifter.jobservice.repository.AdvertRepository;
import com.safalifter.jobservice.request.advert.AdvertCreateRequest;
import com.safalifter.jobservice.request.advert.AdvertUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvertService {
    private final AdvertRepository advertRepository;
    private final JobService jobService;
    private final UserServiceClient userServiceclient;
    private final FileStorageClient fileStorageClient;
    private final ModelMapper modelMapper;

    private static final String ENTITY_TYPE_ADVERT = "advert";

    @Transactional
    public Advert createAdvert(AdvertCreateRequest request, MultipartFile file) {
        String userId = getUserById(request.getUserId()).getId();
        Job job = jobService.getJobById(request.getJobId());

        String imageId = null;
        Advert savedAdvert = null;

        try {
            if (file != null) {
                imageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();
            }

            Advert toSave = Advert.builder()
                    .userId(userId)
                    .job(job)
                    .name(request.getName())
                    .advertiser(request.getAdvertiser())
                    .deliveryTime(request.getDeliveryTime())
                    .description(request.getDescription())
                    .price(request.getPrice())
                    .status(AdvertStatus.OPEN)
                    .imageId(imageId)
                    .build();
            
            savedAdvert = advertRepository.save(toSave);

            if (imageId != null) {
                bindNewImage(savedAdvert.getId(), imageId);
            }

            return savedAdvert;
        } catch (Exception e) {
            if (imageId != null && savedAdvert == null) {
                rollbackUploadedFile(imageId);
            }
            if (savedAdvert != null && imageId != null) {
                try {
                    advertRepository.deleteById(savedAdvert.getId());
                } catch (Exception ex) {
                    log.warn("Failed to cleanup advert after failure: {}", savedAdvert.getId(), ex);
                }
                rollbackUploadedFile(imageId);
            }
            throw e;
        }
    }

    public List<Advert> getAll() {
        return advertRepository.findAll();
    }

    public Advert getAdvertById(String id) {
        return findAdvertById(id);
    }

    public List<Advert> getAdvertsByUserId(String id, Advertiser type) {
        String userId = getUserById(id).getId();
        return advertRepository.getAdvertsByUserIdAndAdvertiser(userId, type);
    }

    public UserDto getUserById(String id) {
        return Optional.ofNullable(userServiceclient.getUserById(id).getBody())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public Advert updateAdvertById(AdvertUpdateRequest request, MultipartFile file) {
        Advert toUpdate = findAdvertById(request.getId());
        
        String oldImageId = toUpdate.getImageId();
        String newImageId = null;
        boolean newImageBound = false;

        try {
            modelMapper.map(request, toUpdate);

            if (file != null) {
                newImageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();
                if (newImageId != null) {
                    bindNewImage(toUpdate.getId(), newImageId);
                    newImageBound = true;
                    toUpdate.setImageId(newImageId);
                }
            }

            Advert savedAdvert = advertRepository.save(toUpdate);

            if (file != null && newImageId != null && oldImageId != null 
                    && !oldImageId.trim().isEmpty() && !oldImageId.equals(newImageId)) {
                try {
                    safeUnbindAndDeleteImage(toUpdate.getId(), oldImageId, ENTITY_TYPE_ADVERT);
                } catch (Exception e) {
                    log.warn("Failed to unbind old image after update for advert: {}", toUpdate.getId(), e);
                }
            }

            return savedAdvert;
        } catch (Exception e) {
            if (newImageId != null) {
                if (newImageBound) {
                    try {
                        safeUnbindImage(toUpdate.getId(), newImageId, ENTITY_TYPE_ADVERT);
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
    public void deleteAdvertById(String id) {
        Advert toDelete = findAdvertById(id);
        
        String imageId = toDelete.getImageId();

        advertRepository.deleteById(id);

        if (imageId != null && !imageId.trim().isEmpty()) {
            try {
                safeUnbindAndDeleteImage(id, imageId, ENTITY_TYPE_ADVERT);
            } catch (Exception e) {
                log.warn("Failed to cleanup image for advert: {}", id, e);
            }
        }
    }

    public boolean authorizeCheck(String id, String principal) {
        return getUserById(getAdvertById(id).getUserId()).getUsername().equals(principal);
    }

    protected Advert findAdvertById(String id) {
        return advertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Advert not found"));
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
            bindRequest.put("entityType", ENTITY_TYPE_ADVERT);
            bindRequest.put("entityId", entityId);
            fileStorageClient.bindReference(bindRequest);
            log.info("Successfully bound advert image reference: {}", entityId);
        } catch (Exception e) {
            log.error("Failed to bind advert image reference: {}", entityId, e);
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
