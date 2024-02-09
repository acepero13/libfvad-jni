package io.github.givimad.libfvadjni;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class MicrophoneVAD {
    public static final int SAMPLE_RATE = 16000;
    private static final AudioFormat audioFormat = new AudioFormat(
            SAMPLE_RATE,
            16,
            1,
            true
            , false);
    private static VoiceActivityDetector vad;

    public static void main(String[] args) throws IOException {
        VoiceActivityDetector.loadLibrary();
        vad = VoiceActivityDetector.newInstance();
        vad.setMode(VoiceActivityDetector.Mode.VERY_AGGRESSIVE);
        vad.setSampleRate(VoiceActivityDetector.SampleRate.fromValue(SAMPLE_RATE));
        try (TargetDataLine targetDataLine = AudioSystem.getTargetDataLine(audioFormat)) {
            targetDataLine.open();
            capture(targetDataLine);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private static void capture(TargetDataLine line) {
        try {
            int frameSizeInBytes = audioFormat.getFrameSize();
            int bufferLengthInFrames = line.getBufferSize() / 8;
            final int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
            readData(line, bufferLengthInBytes);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            line.stop();
            line.close();

        }
    }

    private static void readData(final TargetDataLine line, final int bufferLengthInBytes) throws IOException {
        int numBytesRead;

        line.start();
        while (true) {
            byte[] data = new byte[bufferLengthInBytes];
            if ((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
                break;
            }

            ByteBuffer captureBuffer = ByteBuffer.wrap(data);
            captureBuffer.order(ByteOrder.LITTLE_ENDIAN);

            var shortBuffer = captureBuffer.asShortBuffer();
            short[] samples = new short[captureBuffer.capacity() / 2];
            var j = 0;
            while (shortBuffer.hasRemaining()) {
                samples[j++] = shortBuffer.get();
            }

            int samplesLength = samples.length;
            int step = (SAMPLE_RATE / 1000) * 10; // 10ms step (only allows 10, 20 or 30ms frame)
            int detection = 0;
            for (int i = 0; i < samplesLength - step; i += step) {
                short[] frame = Arrays.copyOfRange(samples, i, i + step);
                if (vad.process(frame)) {
                    detection = i;
                    System.out.println("Voice in: i = " + i);
                    break;
                }
            }

            // super.notifyAudioListeners(new AudioFrame(data, audioFormat, numBytesRead, frameId.incrementAndGet()));
        }
    }
}
