package com.fyp.bloodinventory.dto;

public class MedicalSafeMatchRequest {
    private String recipientBloodGroup;
    private String componentType;

    public String getRecipientBloodGroup() {
        return recipientBloodGroup;
    }

    public void setRecipientBloodGroup(String recipientBloodGroup) {
        this.recipientBloodGroup = recipientBloodGroup;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }
}
