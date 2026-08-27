package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDate;

public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String employeeCode;
    private Integer departmentId;
    private String departmentName;
    private Long managerId;
    private String managerName;
    private LocalDate dateOfJoining;
    private String employmentStatus;
    private String phone;
    private String designation;
    private String workLocation;
    private String employmentType;
    private Long reportsToId;
    private String reportsToName;

    public UserDto() {
    }

    public UserDto(Long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public UserDto(Long id, String name, String email, String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long managerId) {
        this.managerId = managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
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

    public Long getReportsToId() {
        return reportsToId;
    }

    public void setReportsToId(Long reportsToId) {
        this.reportsToId = reportsToId;
    }

    public String getReportsToName() {
        return reportsToName;
    }

    public void setReportsToName(String reportsToName) {
        this.reportsToName = reportsToName;
    }

    // Method to support both Long and Integer
    public Integer getIdAsInteger() {
        return id != null ? id.intValue() : null;
    }

    public void setIdAsInteger(Integer id) {
        this.id = id != null ? id.longValue() : null;
    }
}
