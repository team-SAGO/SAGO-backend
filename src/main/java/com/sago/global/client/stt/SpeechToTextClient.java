package com.sago.global.client.stt;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google Cloud Speech-to-Text 클라이언트. 음성 원본을 텍스트로 변환한다.
 * 인증은 GOOGLE_APPLICATION_CREDENTIALS 환경변수를 SDK가 자동으로 읽어 처리한다.
 * WAV·FLAC 등 헤더에 인코딩 정보가 포함된 포맷을 전제로 인코딩을 명시하지 않는다.
 * 1분 이내 오디오 기준 동기 API(recognize)만 지원하며, 긴 오디오는 추후 longRunningRecognize로 확장 필요.
 */
@Component
public class SpeechToTextClient {

    private final String languageCode;

    public SpeechToTextClient(@Value("${stt.language-code}") String languageCode) {
        this.languageCode = languageCode;
    }

    public String transcribe(byte[] audioBytes) {
        try (SpeechClient speechClient = SpeechClient.create()) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                .setLanguageCode(languageCode)
                .build();
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioBytes))
                .build();

            RecognizeResponse response = speechClient.recognize(config, audio);

            StringBuilder transcript = new StringBuilder();
            for (SpeechRecognitionResult result : response.getResultsList()) {
                if (result.getAlternativesCount() == 0) {
                    continue;
                }
                SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                transcript.append(alternative.getTranscript());
            }

            if (transcript.isEmpty()) {
                throw new SpeechToTextException("음성 인식 결과가 없습니다");
            }

            return transcript.toString();
        } catch (SpeechToTextException e) {
            throw e;
        } catch (Exception e) {
            throw new SpeechToTextException("Google Cloud STT 호출 실패", e);
        }
    }
}
