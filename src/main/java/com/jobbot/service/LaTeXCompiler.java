package com.jobbot.service;

import com.jobbot.exception.LaTeXCompilationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LaTeXCompiler {
    private static final Logger logger = LoggerFactory.getLogger(LaTeXCompiler.class);

    /**
     * Compiles LaTeX content to PDF using pdflatex.
     * Runs pdflatex TWICE (to resolve cross-references).
     * Saves PDF to <user.dir>/pdfs/resume_job_{jobId}.pdf
     * Cleans up temp directory after completion.
     *
     * @throws LaTeXCompilationException if pdflatex exits non-zero or times out
     */
    public String compileToPdf(String latexContent, Long jobId) {
        logger.info("Starting LaTeX compilation for jobId={}", jobId);

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("jobbot_latex_");
        } catch (IOException e) {
            throw new LaTeXCompilationException("Failed to create temp directory", e);
        }

        try {
            Path texFile = tempDir.resolve("resume.tex");
            logger.info("Writing LaTeX content to {}", texFile);
            Files.writeString(texFile, latexContent, StandardCharsets.UTF_8);

            String outputDir = System.getProperty("user.dir") + "/pdfs";
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
                logger.info("Created output directory: {}", outputDir);
            }

            for (int run = 1; run <= 2; run++) {
                logger.info("Running pdflatex pass {} for jobId={}", run, jobId);
                runPdflatex(tempDir, texFile, run);
            }

            Path generatedPdf = tempDir.resolve("resume.pdf");
            if (!Files.exists(generatedPdf)) {
                throw new LaTeXCompilationException("pdflatex produced no PDF");
            }

            Path outputPdf = Paths.get(outputDir, "resume_job_" + jobId + ".pdf");
            logger.info("Copying PDF to {}", outputPdf);
            Files.copy(generatedPdf, outputPdf, StandardCopyOption.REPLACE_EXISTING);

            return outputDir + "/resume_job_" + jobId + ".pdf";

        } catch (IOException e) {
            throw new LaTeXCompilationException("Failed to start pdflatex", e);
        } finally {
            logger.info("Cleaning up temp directory: {}", tempDir);
            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                logger.warn("Failed to clean up temp directory: {}", tempDir, e);
            }
        }
    }

    private void runPdflatex(Path tempDir, Path texFile, int run) {
        Process process;
        try {
            process = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    "-output-directory", tempDir.toString(),
                    texFile.toString()
            )
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new LaTeXCompilationException("Failed to start pdflatex", e);
        }

        boolean finished;
        try {
            finished = process.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new LaTeXCompilationException("pdflatex timed out after 30s");
        }

        if (!finished) {
            process.destroyForcibly();
            throw new LaTeXCompilationException("pdflatex timed out after 30s");
        }

        if (run == 2 && process.exitValue() != 0) {
            String logTail = readLogTail(tempDir.resolve("resume.log"), 50);
            throw new LaTeXCompilationException("pdflatex failed:\n" + logTail);
        }
    }

    private String readLogTail(Path logFile, int lines) {
        try {
            if (!Files.exists(logFile)) {
                return "(no log file found)";
            }
            List<String> allLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, allLines.size() - lines);
            return String.join("\n", allLines.subList(from, allLines.size()));
        } catch (IOException e) {
            return "(could not read log: " + e.getMessage() + ")";
        }
    }
}
