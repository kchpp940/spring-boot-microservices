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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    public Advert createAdvert(AdvertCreateRequest request, MultipartFile file) {
        String userId = getUserById(request.getUserId()).getId();
        Job job = jobService.getJobById(request.getJobId());

        String imageId = null;

        if (file != null)
            imageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();

        try {
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
            return advertRepository.save(toSave);
        } catch (Exception e) {
            safeDeleteFile(imageId);
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

    public Advert updateAdvertById(AdvertUpdateRequest request, MultipartFile file) {
        Advert toUpdate = findAdvertById(request.getId());
        modelMapper.map(request, toUpdate);

        if (file != null) {
            String oldImageId = toUpdate.getImageId();
            String newImageId = fileStorageClient.uploadImageToFIleSystem(file).getBody();
            if (newImageId != null) {
                toUpdate.setImageId(newImageId);
                safeDeleteFile(oldImageId);
            }
        }

        return advertRepository.save(toUpdate);
    }

    public void deleteAdvertById(String id) {
        Advert toDelete = findAdvertById(id);
        safeDeleteFile(toDelete.getImageId());
        advertRepository.deleteById(id);
    }

    public boolean authorizeCheck(String id, String principal) {
        return getUserById(getAdvertById(id).getUserId()).getUsername().equals(principal);
    }

    protected Advert findAdvertById(String id) {
        return advertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Advert not found"));
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
