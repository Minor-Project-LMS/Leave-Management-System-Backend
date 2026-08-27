package com.lms.Leave_Management_System_Backend.dto;

public class RoleDto {
    private Integer id;
    private String roleCode;
    private String roleDescription;

    public RoleDto() {
    }

    public RoleDto(Integer id, String roleCode, String roleDescription) {
        this.id = id;
        this.roleCode = roleCode;
        this.roleDescription = roleDescription;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleDescription() {
        return roleDescription;
    }

    public void setRoleDescription(String roleDescription) {
        this.roleDescription = roleDescription;
    }
}
