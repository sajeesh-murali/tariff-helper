package com.tariffhelper.ocr;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Converts with the ImageMagick program installed on the server. The picture goes in through standard input and the
 * PNG comes back through standard output, so nothing is written to disk ({@code -limit disk 0}). The command and its
 * options are fixed, and the input format is forced to HEIC so ImageMagick never guesses a format from the upload.
 *
 * Privacy: neither the picture nor the program's messages are logged, and error messages never contain either.
 */
@Component
public class ImageMagickHeicConverter implements HeicConverter {

    private static final Logger log = LoggerFactory.getLogger(ImageMagickHeicConverter.class);

    private static final int MAX_PNG_BYTES = 60 * 1024 * 1024;
    private static final int PARALLEL_CONVERSIONS = 2;
    private static final int WAIT_FOR_SLOT_SECONDS = 10;
    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();

    private final OcrProperties props;
    private final Semaphore slots = new Semaphore(PARALLEL_CONVERSIONS);

    public ImageMagickHeicConverter(OcrProperties props) {
        this.props = props;
    }

    @Override
    public byte[] toPng(byte[] heic) {
        if (!props.enabled()) {
            throw unavailable();
        }
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(WAIT_FOR_SLOT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new InvalidRequestException("The server is busy reading other pictures. Try again in a minute.");
            }
            return run(heic);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidRequestException("The picture could not be read.");
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private byte[] run(byte[] heic) throws InterruptedException {
        Process process;
        String side = props.maxSide() + "x" + props.maxSide() + ">";
        try {
            process = new ProcessBuilder(props.heicCommand(),
                    "-limit", "memory", "512MiB", "-limit", "map", "1GiB", "-limit", "disk", "0",
                    "-limit", "area", "100MP", "-limit", "time", String.valueOf(props.timeoutSeconds()),
                    "heic:-", "-auto-orient", "-resize", side, "png:-")
                    .start();
        } catch (IOException e) {
            log.warn("HEIC converter could not be started: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
        try {
            IO.submit(() -> {
                try (OutputStream in = process.getOutputStream()) {
                    in.write(heic);
                } catch (IOException ignored) {
                    // the program stopped early; its exit code is checked below
                }
            });
            Future<byte[]> output = IO.submit(() -> process.getInputStream().readNBytes(MAX_PNG_BYTES));
            IO.submit(() -> process.getErrorStream().transferTo(OutputStream.nullOutputStream()));
            byte[] png = output.get(props.timeoutSeconds(), TimeUnit.SECONDS);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0 || png.length == 0) {
                log.warn("HEIC converter failed: exit={}", finished ? process.exitValue() : "none");
                throw new InvalidRequestException("The picture could not be read.");
            }
            return png;
        } catch (TimeoutException e) {
            log.warn("HEIC converter took too long");
            throw new InvalidRequestException("The picture took too long to read. Try a clearer or smaller picture.");
        } catch (ExecutionException e) {
            throw new InvalidRequestException("The picture could not be read.");
        } finally {
            process.destroyForcibly();
        }
    }

    private static InvalidRequestException unavailable() {
        return new InvalidRequestException(
                "Reading pictures is not available on this server. Upload a PDF or fill in the values by hand.");
    }
}
