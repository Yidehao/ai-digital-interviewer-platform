package org.interviewer.controller;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.interviewer.grace.result.GraceJSONResult;
import org.interviewer.grace.result.ResponseStatusEnum;
import org.interviewer.speech.GoogleSpeechClientProvider;
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
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.util.List;

@RestController
@RequestMapping("speech")
public class GoogleSpeechController {

    private static final Logger log = LoggerFactory.getLogger(GoogleSpeechController.class);

    @Resource
    private GoogleSpeechConfig googleSpeechConfig;

    @Resource
    private GoogleSpeechClientProvider speechClientProvider;

    @PostMapping(value = "uploadVoice")
    public GraceJSONResult uploadFile(@RequestParam("file") MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (StringUtils.isBlank(filename)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_NULL_ERROR);
        }

        log.info("[speech-upload] Received audio: filename={}, size={} bytes, contentType={}",
                filename, file.getSize(), file.getContentType());

        byte[] pcmBytes = convertToPcm(file.getBytes());
        log.info("[speech-upload] decoded to PCM: pcmLength={} bytes (~{} seconds @16kHz mono)",
                pcmBytes.length, pcmBytes.length / 32000);

        String result = recognizePcm(pcmBytes);
        log.info("[speech-upload] Recognition result: \"{}\"", result);

        return GraceJSONResult.ok(result);
    }

    /** What Google STT is told it is receiving: 16 kHz, 16-bit, mono, signed, little-endian. */
    private static final AudioFormat STT_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 16000f, 16, 1, 2, 16000f, false);

    /**
     * Decode an uploaded clip to the exact PCM format {@link #recognizePcm} declares.
     *
     * Handles two input families:
     *   - containers the JDK reads natively (WAV, AIFF, AU) - what a browser produces via
     *     Web Audio, and what the eval harness sends
     *   - MP3, via the mp3spi SPI - what the uni-app recorder produces
     *
     * Previously this ran MP3 decoding unconditionally, so any non-MP3 upload threw. It also
     * built its target format from the *source* sample rate and channel count while
     * recognizePcm hardcoded 16 kHz mono - meaning a 44.1 kHz stereo clip was decoded
     * faithfully and then described to Google as something it was not, which produces
     * confident nonsense rather than an error. Both are fixed by converting to one canonical
     * format here and deriving the recogniser config from that same constant.
     */
    public static byte[] convertToPcm(byte[] audioBytes) throws Exception {
        try (AudioInputStream decoded = openAnyFormat(audioBytes);
             AudioInputStream pcm = toSttFormat(decoded)) {
            return IOUtils.toByteArray(pcm);
        }
    }

    /** Open the clip with whichever reader understands it. */
    private static AudioInputStream openAnyFormat(byte[] bytes) throws Exception {
        // The JDK's own readers cover WAV/AIFF/AU and, because mp3spi registers an SPI, often
        // MP3 as well. Try them first so the common case needs no special handling.
        try {
            return AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
        } catch (UnsupportedAudioFileException ignored) {
            // fall through to an explicit MP3 attempt
        }
        try {
            return new MpegAudioFileReader().getAudioInputStream(new ByteArrayInputStream(bytes));
        } catch (UnsupportedAudioFileException e) {
            throw new UnsupportedAudioFileException(
                    "Unsupported audio format. Supported: WAV/AIFF/AU (PCM) and MP3. " +
                    "Note that WebM/Opus and MP4/AAC - what MediaRecorder produces by default - " +
                    "cannot be decoded by the JVM; record 16 kHz mono WAV via Web Audio instead.");
        }
    }

    /** Convert to 16 kHz mono 16-bit PCM, in two steps so each conversion is one the JDK offers. */
    private static AudioInputStream toSttFormat(AudioInputStream in) {
        AudioFormat source = in.getFormat();
        if (source.matches(STT_FORMAT)) {
            return in;
        }

        // Step 1: get to signed 16-bit PCM, keeping the source rate and channel count. Encoding
        // conversion (e.g. MP3 frames, or unsigned 8-bit) is separate from resampling, and the
        // JDK will not always do both at once.
        AudioFormat intermediate = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(),
                16,
                source.getChannels(),
                source.getChannels() * 2,
                source.getSampleRate(),
                false);
        AudioInputStream pcm = source.getEncoding() == AudioFormat.Encoding.PCM_SIGNED
                && source.getSampleSizeInBits() == 16 && !source.isBigEndian()
                ? in
                : AudioSystem.getAudioInputStream(intermediate, in);

        // Step 2: downmix and resample to exactly what the recogniser is told to expect.
        return pcm.getFormat().matches(STT_FORMAT)
                ? pcm
                : AudioSystem.getAudioInputStream(STT_FORMAT, pcm);
    }

    /**
     * Recognize PCM data using Google Cloud Speech-to-Text (16kHz LINEAR16).
     */
    private String recognizePcm(byte[] pcmBytes) throws Exception {
        // Shared client - deliberately not closed here, it outlives the request
        SpeechClient speechClient = speechClientProvider.get();
        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz((int) STT_FORMAT.getSampleRate())
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
