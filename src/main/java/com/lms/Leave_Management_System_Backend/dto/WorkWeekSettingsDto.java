package com.lms.Leave_Management_System_Backend.dto;

public class WorkWeekSettingsDto {

    private boolean saturdayWorking;

    public boolean isSaturdayWorking() {
        return saturdayWorking;
    }

    public void setSaturdayWorking(boolean saturdayWorking) {
        this.saturdayWorking = saturdayWorking;
    }
}
