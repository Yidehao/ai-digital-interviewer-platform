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

    /** Below this, there is not enough audio to recognise anything. ~0.4 s at 16 kHz 16-bit mono. */
    private static final int MIN_PCM_BYTES = 12_800;

    /**
     * Transcribe one recorded answer.
     *
     * <p><b>Every failure here is classified by who can fix it</b>, because this endpoint is the
     * one a candidate hits repeatedly while being assessed and the previous behaviour was to answer
     * every problem with {@code 555 System busy, please try again later!}. That message is useless
     * to a candidate: it does not say whether to re-record, wait, or give up, and the one recovery
     * available to them is the one it fails to suggest.
     *
     * <p>It also hid a real outage. A missing credentials file threw, the generic handler turned it
     * into "System busy", and the interview looked healthy while every answer was silently refused
     * — the candidate's answer-gate eventually timed out and closed the interview. That happened
     * here, and it took reading the server log to find out why.
     *
     * <p>What the candidate is told never includes which of these it was beyond what they can act
     * on. "Google credentials are not configured" is an operator's problem and belongs in the log,
     * not in front of someone being interviewed.
     */
    @PostMapping(value = "uploadVoice")
    public GraceJSONResult uploadFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (StringUtils.isBlank(filename)) {
            return GraceJSONResult.errorCustom(ResponseStatusEnum.FILE_UPLOAD_NULL_ERROR);
        }

        log.info("[speech-upload] Received audio: filename={}, size={} bytes, contentType={}",
                filename, file.getSize(), file.getContentType());

        byte[] pcmBytes;
        try {
            pcmBytes = convertToPcm(file.getBytes());
        } catch (Exception e) {
            // The client sent something this server cannot decode. That is a client bug - the
            // recorder is producing a format the pipeline never agreed to - so it is logged at
            // error, while the candidate is simply told to answer again.
            log.error("[speech-upload] could not decode {} ({}): {}",
                    filename, file.getContentType(), e.toString());
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SPEECH_UNREADABLE_AUDIO);
        }

        if (pcmBytes.length < MIN_PCM_BYTES) {
            log.info("[speech-upload] {} bytes of PCM is too short to recognise", pcmBytes.length);
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SPEECH_TOO_SHORT);
        }
        log.info("[speech-upload] decoded to PCM: pcmLength={} bytes (~{} seconds @16kHz mono)",
                pcmBytes.length, pcmBytes.length / 32000);

        Recognition result;
        try {
            result = recognizePcm(pcmBytes);
        } catch (Exception e) {
            // Credentials, quota, network, service outage. All of these are the operator's to fix
            // and none of them are the candidate's fault, so the candidate is told their interview
            // is safe and to wait - which is both true and actionable - and the real cause goes to
            // the log where someone can act on it.
            log.error("[speech-upload] speech recognition failed", e);
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SPEECH_SERVICE_UNAVAILABLE);
        }

        if (result.transcript() == null || result.transcript().isBlank()) {
            // Recognition worked and heard nothing. This is the one case the candidate can
            // genuinely fix, and the only one where telling them to check their microphone helps.
            log.info("[speech-upload] recognised no speech in {} bytes of PCM", pcmBytes.length);
            return GraceJSONResult.errorCustom(ResponseStatusEnum.SPEECH_NO_SPEECH_DETECTED);
        }

        log.info("[speech-upload] Recognition result: \"{}\" (confidence={})",
                result.transcript(), result.confidence());
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
    private Recognition recognizePcm(byte[] pcmBytes) throws Exception {
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
            return Recognition.empty();
        }
        SpeechRecognitionResult first = results.get(0);
        if (first.getAlternativesCount() == 0) {
            return Recognition.empty();
        }
        SpeechRecognitionAlternative best = first.getAlternatives(0);
        return new Recognition(best.getTranscript(), (double) best.getConfidence());
    }

    /**
     * What the STT actually knows, rather than half of it.
     *
     * <p>Google returns a confidence with every alternative and this endpoint used to discard it,
     * which made {@code Turn.sttConfidence} unfillable: the column existed, the request parameter
     * existed, and no client could ever supply a value because the server never sent one. A field
     * that looks like a control and cannot be set is worse than no field.
     *
     * <p>{@code confidence} is null when nothing was recognised — distinct from 0.0, which would
     * claim the recogniser was certain the candidate said nothing.
     */
    public record Recognition(String transcript, Double confidence) {
        static Recognition empty() {
            return new Recognition("", null);
        }
    }
}
