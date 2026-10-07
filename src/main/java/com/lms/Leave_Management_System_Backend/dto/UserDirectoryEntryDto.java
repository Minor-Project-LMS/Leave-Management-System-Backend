package com.lms.Leave_Management_System_Backend.dto;

// Deliberately minimal — used to populate a "pick a colleague" list (e.g.
// the leave donation recipient picker), so it exposes only what's needed to
// identify someone in a dropdown, not full profile/contact data.
public class UserDirectoryEntryDto {

    private Long id;
    private String fullName;
    private String employeeCode;
    private String departmentName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }
}
