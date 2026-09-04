package com.sago.domain.statement;

import com.sago.domain.accident.Accident;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.S3UploadException;
import com.sago.global.client.s3.S3Uploader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Step 4 — 녹음된 음성 진술 파일을 S3에 올리고 STT 변환·저장까지 이어준다.
 *
 * 파일 바이트는 한 번만 읽어 업로드와 STT에 재사용한다. 음성 파일은 최대 20MB라
 * MultipartFile에서 두 번 읽으면 그만큼 메모리를 더 쓰기 때문이다.
 * 같은 이유로 확장자·용량 검증은 바이트를 읽기 전에 먼저 수행한다.
 *
 * 트랜잭션은 일부러 걸지 않았다. S3 업로드는 롤백되지 않는 외부 호출이라
 * 트랜잭션 안에서 수행하면 업로드가 끝날 때까지 DB 커넥션을 붙잡게 된다.
 * DB 저장은 {@link StatementService#transcribe}가 자체 트랜잭션으로 처리하고,
 * 저장이 실패하면 여기서 업로드된 파일을 지워 고아 파일을 남기지 않는다.
 */
@Service
public class StatementUploadService {

    private final S3Uploader s3Uploader;
    private final StatementService statementService;

    public StatementUploadService(S3Uploader s3Uploader, StatementService statementService) {
        this.s3Uploader = s3Uploader;
        this.statementService = statementService;
    }

    /**
     * 음성 파일을 업로드하고 STT 변환 결과와 함께 진술을 저장한다.
     * 음성 인식에 실패해도 원본은 보존되고 진술은 저장된다(sttText만 비어 있음).
     */
    public Statement upload(Accident accident, MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new S3UploadException("업로드할 음성 파일이 없습니다");
        }

        // 파일 전체를 메모리에 올리기 전에 확장자·용량부터 확인한다.
        // getBytes() 이후에 검사하면 걸러낼 파일까지 메모리를 차지하게 된다.
        String extension = s3Uploader.extractExtension(audioFile.getOriginalFilename());
        FileCategory.STATEMENT_AUDIO.validate(extension, audioFile.getSize());

        byte[] audioBytes = readBytes(audioFile);
        String audioFileUrl = s3Uploader.upload(audioBytes, extension, FileCategory.STATEMENT_AUDIO);

        try {
            return statementService.transcribe(accident, audioFileUrl, audioBytes);
        } catch (RuntimeException e) {
            deleteQuietly(audioFileUrl);
            throw e;
        }
    }

    private byte[] readBytes(MultipartFile audioFile) {
        try {
            return audioFile.getBytes();
        } catch (IOException e) {
            throw new S3UploadException("음성 파일을 읽지 못했습니다", e);
        }
    }

    /**
     * 되돌리기용 삭제. 여기서 발생한 예외 때문에 원래 실패 원인이 가려지면 안 되므로 삼킨다.
     */
    private void deleteQuietly(String fileUrl) {
        try {
            s3Uploader.delete(fileUrl);
        } catch (RuntimeException ignored) {
            // 삭제 실패 시 S3에 고아 파일이 남지만, 호출자에게는 원래 예외를 그대로 전달한다
        }
    }
}
