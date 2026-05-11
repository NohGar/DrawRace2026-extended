package backend.drawrace.global.globalexceptionhandler;

import static org.springframework.http.HttpStatus.*;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import backend.drawrace.global.exception.ServiceException;
import backend.drawrace.global.rsdata.RsData;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 처리 (ServiceException)
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RsData<Void>> handle(
            ServiceException exception, HttpServletRequest request, HttpServletResponse response) {
        RsData<Void> rsData = exception.getRsData();
        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    // 유효성 검증 실패 (400) - @Valid 실패 시 (request body)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handle(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(BAD_REQUEST).body(new RsData<>("400-2", message));
    }

    // 유효성 검증 실패 (400) - @Validated 실패 시 (request param, path variable)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RsData<Void>> handle(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(BAD_REQUEST).body(new RsData<>("400-2", message));
    }

    // 잘못된 인자 예외 (400)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RsData<Void>> handle(IllegalArgumentException exception) {
        return ResponseEntity.status(BAD_REQUEST).body(new RsData<>("400-1", exception.getMessage()));
    }

    // 권한 부족 예외 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RsData<Void>> handle(AccessDeniedException exception) {
        return ResponseEntity.status(FORBIDDEN).body(new RsData<>("403-1", "접근 권한이 없습니다."));
    }

    // 자원 없음 예외 (404)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RsData<Void>> handle(NoResourceFoundException exception) {
        return ResponseEntity.status(NOT_FOUND).body(new RsData<>("404-1", "요청하신 리소스를 찾을 수 없습니다."));
    }

    // 그 외 예상치 못한 서버 에러 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RsData<Void>> handle(Exception exception) {
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new RsData<>("500-1", "예상치 못한 서버 오류가 발생했습니다."));
    }
}
