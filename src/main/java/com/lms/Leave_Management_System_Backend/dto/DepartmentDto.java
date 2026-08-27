package com.lms.Leave_Management_System_Backend.dto;

public class DepartmentDto {
    private Integer id;
    private String departmentName;
    private Long departmentHeadId;
    private String departmentHeadName;
    private Integer memberCount;

    public DepartmentDto() {
    }

    public DepartmentDto(Integer id, String name) {
        this.id = id;
        this.departmentName = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Long getDepartmentHeadId() {
        return departmentHeadId;
    }

    public void setDepartmentHeadId(Long departmentHeadId) {
        this.departmentHeadId = departmentHeadId;
    }

    public String getDepartmentHeadName() {
        return departmentHeadName;
    }

    public void setDepartmentHeadName(String departmentHeadName) {
        this.departmentHeadName = departmentHeadName;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    // Backward compatibility
    public String getName() {
        return departmentName;
    }

    public void setName(String name) {
        this.departmentName = name;
    }
}
