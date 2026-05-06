package com.jobbot.exception;

public class LaTeXCompilationException extends RuntimeException {
    public LaTeXCompilationException(String message) {
        super(message);
    }

    public LaTeXCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
