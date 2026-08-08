package org.interviewer.controller;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.utils.GoogleSpeechConfig;
import jakarta.annotation.Resource;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("speech")
public class GoogleSpeechController {

    private static final Logger log = LoggerFactory.getLogger(GoogleSpeechController.class);

    @Resource
    private GoogleSpeechConfig googleSpeechConfig;

    /** Get absolute path of credentials JSON: prefer config, then env var. */
    private String getCredentialsPath() {
        String fromConfig = googleSpeechConfig.getCredentials().getLocation();
        if (StringUtils.isNotBlank(fromConfig)) {
            return fromConfig.replaceFirst("^file:", "").trim();
        }
        String fromEnv = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (StringUtils.isNotBlank(fromEnv)) {
            return fromEnv.trim();
        }
        throw new IllegalStateException(
                "Google credentials are not configured. Please choose one of the following:\n" +
                "1) Set env GOOGLE_APPLICATION_CREDENTIALS to the absolute path of your JSON key\n" +
                "2) Set google.cloud.credentials.location in application-dev.yml (e.g. /Users/xxx/your-key.json)"
        );
    }

    private SpeechClient createSpeechClient() throws Exception {
        String path = getCredentialsPath();
        try (InputStream keyStream = new FileInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(keyStream);
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            return SpeechClient.create(settings);
        }
    }

    @PostMapping(value = "uploadVoice")
    public GraceJSONResult uploadFile(@RequestParam("file") MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (StringUtils.isBlank(filename)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_NULL_ERROR);
        }

        log.info("[speech-upload] Received audio: filename={}, size={} bytes, contentType={}",
                filename, file.getSize(), file.getContentType());

        byte[] pcmBytes = mp3ConvertToPcm(file.getInputStream());
        log.info("[speech-upload] MP3->PCM done: pcmLength={} bytes (approx {} seconds @16kHz)",
                pcmBytes.length, pcmBytes.length / 32000);

        String result = recognizePcm(pcmBytes);
        log.info("[speech-upload] Recognition result: \"{}\"", result);

        return GraceJSONResult.ok(result);
    }

    /**
     * Convert MP3 to PCM (16kHz, 16bit, mono, same as original Baidu logic).
     */
    public static byte[] mp3ConvertToPcm(InputStream inputStream) throws Exception {
        AudioInputStream audioInputStream = getPcmAudioInputStream(inputStream);
        return IOUtils.toByteArray(audioInputStream);
    }

    private static AudioInputStream getPcmAudioInputStream(InputStream inputStream) {
        try {
            MpegAudioFileReader mp = new MpegAudioFileReader();
            AudioInputStream in = mp.getAudioInputStream(inputStream);
            AudioFormat baseFormat = in.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            return AudioSystem.getAudioInputStream(targetFormat, in);
        } catch (Exception e) {
            throw new RuntimeException("MP3 to PCM conversion failed", e);
        }
    }

    /**
     * Recognize PCM data using Google Cloud Speech-to-Text (16kHz LINEAR16).
     */
    private String recognizePcm(byte[] pcmBytes) throws Exception {
        try (SpeechClient speechClient = createSpeechClient()) {
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode(googleSpeechConfig.getSpeech().getLanguageCode())
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(pcmBytes))
                    .build();

            RecognizeRequest request = RecognizeRequest.newBuilder()
                    .setConfig(config)
                    .setAudio(audio)
                    .build();

            RecognizeResponse response = speechClient.recognize(request);
            List<SpeechRecognitionResult> results = response.getResultsList();

            if (results.isEmpty()) {
                return "";
            }
            SpeechRecognitionResult first = results.get(0);
            if (first.getAlternativesCount() == 0) {
                return "";
            }
            return first.getAlternatives(0).getTranscript();
        }
    }
}
