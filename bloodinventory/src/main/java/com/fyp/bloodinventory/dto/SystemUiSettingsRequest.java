package com.fyp.bloodinventory.dto;

public class SystemUiSettingsRequest {

    private double fontScale = 1.0;
    private String accentColor = "#2f80ed";

    public double getFontScale() {
        return fontScale;
    }

    public void setFontScale(double fontScale) {
        this.fontScale = fontScale;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }
}
