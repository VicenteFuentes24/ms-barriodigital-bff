package cl.barriodigital.barriodigitalbff.exception;

import org.springframework.http.HttpStatus;

public class DownstreamServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String path;

    public DownstreamServiceException(String message, String path, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.SERVICE_UNAVAILABLE;
        this.path = path;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getPath() {
        return path;
    }
}
