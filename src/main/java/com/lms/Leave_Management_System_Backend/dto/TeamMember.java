package com.lms.Leave_Management_System_Backend.dto;

public class TeamMember {
    private Long id;
    private String employeeCode;
    private String fullName;
    private Integer departmentId;
    private String departmentName;
    private String designation;
    private String email;
    private String phone;
    private String status;
    private String avatarUrl;

    // Constructors
    public TeamMember() {}

    public TeamMember(Long id, String employeeCode, String fullName, Integer departmentId, 
                      String departmentName, String designation, String email, String phone, 
                      String status, String avatarUrl) {
        this.id = id;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.designation = designation;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.avatarUrl = avatarUrl;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}