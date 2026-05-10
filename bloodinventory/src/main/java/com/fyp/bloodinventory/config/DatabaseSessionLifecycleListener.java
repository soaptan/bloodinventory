package com.fyp.bloodinventory.config;

import com.fyp.bloodinventory.service.DatabaseSessionControlService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSessionLifecycleListener implements HttpSessionListener {

    private final DatabaseSessionControlService sessionControlService;

    public DatabaseSessionLifecycleListener(DatabaseSessionControlService sessionControlService) {
        this.sessionControlService = sessionControlService;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        try {
            sessionControlService.endSession(event.getSession().getId(), "DESTROYED");
        } catch (DataAccessException ignored) {
            // The application may already be shutting down with the datasource closed.
        }
    }
}
