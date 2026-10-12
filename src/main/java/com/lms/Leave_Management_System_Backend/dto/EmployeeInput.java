package com.lms.Leave_Management_System_Backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

import java.time.LocalDate;

public class EmployeeInput {

    // Validation groups: EmployeeInput is shared by POST /employees
    // (create, where these fields must all be present) and PATCH
    // /employees/{id} (update, which is a genuine partial update — e.g.
    // the "Manage Roles & Access" modal sends only { role: "MANAGER" }).
    // Both groups extend Default so format constraints like @Email/@Size
    // still apply whenever a field IS provided; only the "must be
    // present at all" (@NotNull/@NotBlank) checks are create-only.
    public interface OnCreate extends Default {}
    public interface OnUpdate extends Default {}

    @Size(max = 20)
    private String employeeCode;

    @NotBlank(groups = OnCreate.class)
    @Size(max = 120)
    private String fullName;

    @NotBlank(groups = OnCreate.class)
    @Email
    @Size(max = 150)
    private String email;

    private String phone;

    @NotNull(groups = OnCreate.class)
    private String role;

    @NotNull(groups = OnCreate.class)
    private Integer departmentId;

    private String designation;

    private Integer reportsTo;

    @NotNull(groups = OnCreate.class)
    private LocalDate dateOfJoining;

    private String employmentStatus;

    private String workLocation;

    private String employmentType;

    // Getters and Setters
    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Integer getReportsTo() {
        return reportsTo;
    }

    public void setReportsTo(Integer reportsTo) {
        this.reportsTo = reportsTo;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(String workLocation) {
        this.workLocation = workLocation;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }
}