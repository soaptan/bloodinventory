package com.fyp.bloodinventory.dto;

public class SecuritySettingsRequest {

    private boolean sessionControlEnabled = true;
    private int maxConcurrentSessions = 1;
    private int sessionTimeoutMinutes = 15;
    private boolean preventNewLogin;
    private boolean rowLevelSecurityEnabled = true;

    public boolean isSessionControlEnabled() {
        return sessionControlEnabled;
    }

    public void setSessionControlEnabled(boolean sessionControlEnabled) {
        this.sessionControlEnabled = sessionControlEnabled;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public void setMaxConcurrentSessions(int maxConcurrentSessions) {
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    public boolean isPreventNewLogin() {
        return preventNewLogin;
    }

    public void setPreventNewLogin(boolean preventNewLogin) {
        this.preventNewLogin = preventNewLogin;
    }

    public boolean isRowLevelSecurityEnabled() {
        return rowLevelSecurityEnabled;
    }

    public void setRowLevelSecurityEnabled(boolean rowLevelSecurityEnabled) {
        this.rowLevelSecurityEnabled = rowLevelSecurityEnabled;
    }
}
