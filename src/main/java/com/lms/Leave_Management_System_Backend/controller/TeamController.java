package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.User;
import com.lms.Leave_Management_System_Backend.repository.UserRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    private final UserRepository userRepository;

    public TeamController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/members")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<List<UserDto>>> getTeamMembers(Authentication authentication) {
        String email = authentication.getName();
        User currentUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        List<User> teamMembers = userRepository.findByReportsToId(currentUser.getId());
        
        List<UserDto> memberDtos = teamMembers.stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new ApiResponse<>(true, memberDtos));
    }

    @GetMapping("/members/{memberId}/leave-balance")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getTeamMemberLeaveBalance(
            @PathVariable Long memberId,
            Authentication authentication) {
        
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User", memberId));

        // Simplified implementation
        java.util.Map<String, Object> balance = new java.util.HashMap<>();
        balance.put("userId", member.getId());
        balance.put("userName", member.getName());
        balance.put("balances", java.util.Map.of(
                "ANNUAL", 12.0,
                "SICK", 6.0,
                "CASUAL", 3.0
        ));

        return ResponseEntity.ok(new ApiResponse<>(true, balance));
    }

    @GetMapping("/members/{memberId}/leave-requests")
    @RequireRole({"MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<List<UserDto>>> getTeamMemberLeaveRequests(
            @PathVariable Long memberId,
            @RequestParam(required = false) String status) {
        
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User", memberId));

        // Simplified - return user info
        UserDto userDto = toUserDto(member);
        return ResponseEntity.ok(new ApiResponse<>(true, List.of(userDto)));
    }

    private UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().getRoleCode());
        dto.setEmployeeCode(user.getEmployeeCode());
        if (user.getDepartment() != null) {
            dto.setDepartmentId(user.getDepartment().getId());
            dto.setDepartmentName(user.getDepartment().getName());
        }
        if (user.getReportsTo() != null) {
            dto.setManagerId(user.getReportsTo().getId());
            dto.setManagerName(user.getReportsTo().getName());
        }
        dto.setDateOfJoining(user.getDateOfJoining());
        dto.setEmploymentStatus(user.getEmploymentStatus().name());
        return dto;
    }
}
