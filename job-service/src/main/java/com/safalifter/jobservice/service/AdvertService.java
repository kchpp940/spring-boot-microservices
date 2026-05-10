package com.safalifter.jobservice.service;

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

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvertService {
    private final AdvertRepository advertRepository;
    private final JobService jobService;
    private final UserServiceClient userServiceclient;
    private final ModelMapper modelMapper;
    private final FileReferenceCoordinator fileReferenceCoordinator;

    private static final String ENTITY_TYPE_ADVERT = "advert";

    @Transactional
    public Advert createAdvert(AdvertCreateRequest request, MultipartFile file) {
        String userId = getUserById(request.getUserId()).getId();
        Job job = jobService.getJobById(request.getJobId());

        return fileReferenceCoordinator.executeCreate(
                ENTITY_TYPE_ADVERT,
                file,
                imageId -> {
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
                },
                Advert::getId
        );
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

        return fileReferenceCoordinator.executeUpdate(
                ENTITY_TYPE_ADVERT,
                oldImageId,
                file,
                newImageId -> {
                    modelMapper.map(request, toUpdate);
                    if (newImageId != null) {
                        toUpdate.setImageId(newImageId);
                    }
                    return advertRepository.save(toUpdate);
                },
                Advert::getId
        );
    }

    @Transactional
    public void deleteAdvertById(String id) {
        Advert toDelete = findAdvertById(id);
        String imageId = toDelete.getImageId();

        fileReferenceCoordinator.executeDelete(
                ENTITY_TYPE_ADVERT,
                id,
                imageId,
                () -> advertRepository.deleteById(id)
        );
    }

    public boolean authorizeCheck(String id, String principal) {
        return getUserById(getAdvertById(id).getUserId()).getUsername().equals(principal);
    }

    protected Advert findAdvertById(String id) {
        return advertRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Advert not found"));
    }
}
