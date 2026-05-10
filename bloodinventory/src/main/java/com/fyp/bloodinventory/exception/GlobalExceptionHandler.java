package com.fyp.bloodinventory.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 Model model) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        LOGGER.warn("Handled bad request at {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneralException(Exception ex,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         Model model) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        LOGGER.error("Unhandled error at {}", request.getRequestURI(), ex);
        model.addAttribute("errorMessage", "An unexpected error occurred. Please contact the IT Administrator.");
        return "error";
    }
}
