package com.ttuk.grouppurchase;

import com.ttuk.global.error.BusinessException;
import com.ttuk.global.error.ErrorCode;
import com.ttuk.global.storage.ImageType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GroupPurchaseImageValidator {

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 5;
    private static final long MAX_SIZE_BYTES = DataSize.ofMegabytes(5).toBytes();

    public List<ValidatedImage> validate(List<MultipartFile> images) {

        if (images == null || images.size() < MIN_COUNT || images.size() > MAX_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        List<ValidatedImage> validated = new ArrayList<>(images.size());
        for (MultipartFile image : images) {
            validated.add(new ValidatedImage(image, detectType(image)));
        }
        return validated;

    }



    private ImageType detectType(MultipartFile image){

        if(image.isEmpty() || image.getSize() > MAX_SIZE_BYTES){
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

        try(InputStream inputStream = image.getInputStream()) {
            byte[] header = inputStream.readNBytes(ImageType.HEADER_LENGTH);
            Optional<ImageType> type = ImageType.detect(header);
            if(type.isEmpty()){
                throw new BusinessException(ErrorCode.INVALID_IMAGE);
            }
            return type.get();
        }catch (IOException e){
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }

    }

    public record ValidatedImage(MultipartFile file, ImageType type) {

    }


}
