package com.fyp.bloodinventory.dto;

import java.sql.Timestamp;
import java.util.Locale;

public class AuditTrailDto {

    private Long auditId;
    private Timestamp eventTimestamp;
    private String eventTimestampUtc;
    private Long userId;
    private String username;
    private String role;
    private Long componentId;
    private Long donationId;
    private String eventCategory;
    private String operationType;
    private String actionType;
    private String tableName;
    private String rowPk;
    private String oldValue;
    private String newValue;
    private String deviceId;
    private String sourceIp;
    private String location;
    private String workflowPhase;
    private String requestPath;
    private String httpMethod;
    private String sessionIdHash;
    private String processContext;
    private String previousHash;
    private String integrityHash;

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public Timestamp getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Timestamp eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getEventTimestampUtc() {
        return eventTimestampUtc;
    }

    public void setEventTimestampUtc(String eventTimestampUtc) {
        this.eventTimestampUtc = eventTimestampUtc;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public Long getDonationId() {
        return donationId;
    }

    public void setDonationId(Long donationId) {
        this.donationId = donationId;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public void setEventCategory(String eventCategory) {
        this.eventCategory = eventCategory;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRowPk() {
        return rowPk;
    }

    public void setRowPk(String rowPk) {
        this.rowPk = rowPk;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getWorkflowPhase() {
        return workflowPhase;
    }

    public void setWorkflowPhase(String workflowPhase) {
        this.workflowPhase = workflowPhase;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getSessionIdHash() {
        return sessionIdHash;
    }

    public void setSessionIdHash(String sessionIdHash) {
        this.sessionIdHash = sessionIdHash;
    }

    public String getProcessContext() {
        return processContext;
    }

    public void setProcessContext(String processContext) {
        this.processContext = processContext;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    public void setIntegrityHash(String integrityHash) {
        this.integrityHash = integrityHash;
    }

    public String getActorLabel() {
        if (username != null && !username.isBlank()) {
            String normalized = username.trim();
            return "system".equalsIgnoreCase(normalized) ? "System" : normalized;
        }

        if (userId != null) {
            return "Staff #" + userId;
        }

        return "System";
    }

    public String getRoleLabel() {
        if ((role == null || role.isBlank()) && username != null && "system".equalsIgnoreCase(username.trim())) {
            return "System";
        }
        return readableCode(role);
    }

    public String getEventCategoryLabel() {
        return readableCode(eventCategory);
    }

    public String getOperationTypeLabel() {
        return readableCode(operationType);
    }

    public String getActionTypeLabel() {
        return readableCode(actionType);
    }

    public String getTargetLabel() {
        return readableCode(tableName);
    }

    public String getRequestLabel() {
        if (requestPath == null || requestPath.isBlank()) {
            return "-";
        }

        String method = httpMethod == null || httpMethod.isBlank() ? "" : httpMethod.trim().toUpperCase(Locale.ROOT) + " ";
        return method + requestPath.trim();
    }

    public String getIntegrityHashShort() {
        if (integrityHash == null || integrityHash.isBlank()) {
            return "-";
        }

        return integrityHash.length() <= 12 ? integrityHash : integrityHash.substring(0, 12);
    }

    private String readableCode(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        String[] parts = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').split("_+");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        return label.isEmpty() ? value : label.toString();
    }
}
