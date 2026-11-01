package com.tariffhelper.ocr;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads a picture with the Tesseract program installed on the server. The picture goes in through standard input and
 * the text comes back through standard output, so nothing is written to disk. The command and its options are fixed;
 * nothing from the upload is ever part of the command line.
 *
 * Privacy: neither the picture nor the recognised text is logged, and error messages never contain either.
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    private static final int MAX_CHARS = 50_000;
    private static final int MAX_MESSAGE_CHARS = 300;
    /** Tesseract can need a few hundred MB for a large picture, so only a couple run at the same time. */
    private static final int PARALLEL_READS = 2;
    private static final int WAIT_FOR_SLOT_SECONDS = 10;
    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();

    private final OcrProperties props;
    private final Semaphore slots = new Semaphore(PARALLEL_READS);

    public TesseractOcrEngine(OcrProperties props) {
        this.props = props;
    }

    @Override
    public String recognize(byte[] png) {
        if (!props.enabled()) {
            throw unavailable();
        }
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(WAIT_FOR_SLOT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new InvalidRequestException("The server is busy reading other pictures. Try again in a minute.");
            }
            return run(png);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidRequestException("The picture could not be read.");
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private String run(byte[] png) throws InterruptedException {
        Process process;
        try {
            // The pictures are prepared at about 300 DPI, which is also what Tesseract is told. Keeping the spaces
            // between words keeps the columns of a payslip table apart.
            process = new ProcessBuilder(props.command(), "stdin", "stdout", "-l", props.languages(), "--psm", "6",
                    "--dpi", "300", "-c", "preserve_interword_spaces=1")
                    .start();
        } catch (IOException e) {
            log.warn("OCR program could not be started: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
        try {
            IO.submit(() -> {
                try (OutputStream in = process.getOutputStream()) {
                    in.write(png);
                } catch (IOException ignored) {
                    // the program stopped early; its exit code is checked below
                }
            });
            Future<byte[]> output = IO.submit(() -> process.getInputStream().readNBytes(MAX_CHARS * 4));
            // Tesseract's own messages (missing language data, for example) never contain the recognised text.
            Future<byte[]> messages = IO.submit(() -> process.getErrorStream().readNBytes(MAX_MESSAGE_CHARS));
            long started = System.nanoTime();
            byte[] bytes = output.get(props.timeoutSeconds(), TimeUnit.SECONDS);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            long millis = (System.nanoTime() - started) / 1_000_000;
            if (!finished || process.exitValue() != 0) {
                log.warn("OCR program failed: exit={}, durationMs={}, message={}", finished ? process.exitValue() : "none",
                        millis, programMessage(messages));
                throw new InvalidRequestException("The picture could not be read.");
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            log.info("OCR read a picture: durationMs={}, textChars={}, lines={}", millis, text.length(),
                    text.lines().filter(line -> !line.isBlank()).count());
            if (text.isBlank()) {
                throw new InvalidRequestException("No text was found in the picture. Try a clearer picture.");
            }
            return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS);
        } catch (TimeoutException e) {
            log.warn("OCR program took too long");
            throw new InvalidRequestException("The picture took too long to read. Try a clearer or smaller picture.");
        } catch (ExecutionException e) {
            throw new InvalidRequestException("The picture could not be read.");
        } finally {
            process.destroyForcibly();
        }
    }

    private static String programMessage(Future<byte[]> messages) {
        try {
            String text = new String(messages.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            return text.replaceAll("\\s+", " ").strip();
        } catch (Exception e) {
            return "";
        }
    }

    private static InvalidRequestException unavailable() {
        return new InvalidRequestException(
                "Reading pictures is not available on this server. Upload a PDF or fill in the values by hand.");
    }
}
