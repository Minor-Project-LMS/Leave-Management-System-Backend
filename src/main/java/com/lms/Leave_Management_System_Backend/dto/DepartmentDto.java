package com.lms.Leave_Management_System_Backend.dto;

public class DepartmentDto {
    private Integer id;
    private String name;
    private Long departmentHeadId;
    private String departmentHeadName;

    public DepartmentDto() {
    }

    public DepartmentDto(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
