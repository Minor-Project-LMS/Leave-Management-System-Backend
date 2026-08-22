package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Department;
import com.lms.Leave_Management_System_Backend.model.Holiday;
import com.lms.Leave_Management_System_Backend.repository.DepartmentRepository;
import com.lms.Leave_Management_System_Backend.repository.HolidayRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/holidays")
public class HolidaysController {

    private final HolidayRepository holidayRepository;
    private final DepartmentRepository departmentRepository;

    public HolidaysController(HolidayRepository holidayRepository, DepartmentRepository departmentRepository) {
        this.holidayRepository = holidayRepository;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<PaginatedResponse<HolidayDto>> listHolidays(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Holiday> holidays;

        if (year != null && month != null) {
            LocalDate startOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
            holidays = holidayRepository.findByDateBetween(startOfMonth, endOfMonth, pageable);
        } else if (year != null) {
            LocalDate startOfYear = LocalDate.of(year, 1, 1);
            LocalDate endOfYear = LocalDate.of(year, 12, 31);
            holidays = holidayRepository.findByDateBetween(startOfYear, endOfYear, pageable);
        } else {
            holidays = holidayRepository.findAll(pageable);
        }

        List<HolidayDto> holidayDtos = holidays.getContent().stream()
                .map(this::toHolidayDto)
                .collect(Collectors.toList());

        PageResponse pageResponse = new PageResponse(
                holidays.getNumber(),
                holidays.getSize(),
                holidays.getTotalElements(),
                holidays.getTotalPages()
        );

        return ResponseEntity.ok(new PaginatedResponse<>(true, holidayDtos, pageResponse));
    }

    @GetMapping("/upcoming")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<List<HolidayDto>> getUpcomingHolidays(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(30); // Next 30 days
        
        if (month != null && year != null) {
            LocalDate startOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
            return ResponseEntity.ok(holidayRepository.findByDateBetween(startOfMonth, endOfMonth).stream()
                    .map(this::toHolidayDto)
                    .collect(Collectors.toList()));
        } else {
            return ResponseEntity.ok(holidayRepository.findByDateBetween(today, endDate).stream()
                    .map(this::toHolidayDto)
                    .collect(Collectors.toList()));
        }
    }

    @GetMapping("/{holidayId}")
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<HolidayDto> getHoliday(@PathVariable Integer holidayId) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", holidayId));
        return ResponseEntity.ok(toHolidayDto(holiday));
    }

    @PostMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<HolidayDto> createHoliday(
            @RequestBody HolidayDto request) {
        
        Holiday holiday = new Holiday();
        holiday.setName(request.getName());
        holiday.setDate(request.getDate());
        holiday.setRestricted(request.isRestricted());
        
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            holiday.setDepartment(department);
        }

        Holiday saved = holidayRepository.save(holiday);
        HolidayDto dto = toHolidayDto(saved);
        
        return ResponseEntity.status(201).body(dto);
    }

    @PatchMapping("/{holidayId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<HolidayDto> updateHoliday(
            @PathVariable Integer holidayId,
            @RequestBody HolidayDto request) {
        
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", holidayId));

        if (request.getName() != null) {
            holiday.setName(request.getName());
        }
        if (request.getDate() != null) {
            holiday.setDate(request.getDate());
        }
        if (request.isRestricted() != holiday.isRestricted()) {
            holiday.setRestricted(request.isRestricted());
        }
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));
            holiday.setDepartment(department);
        }

        Holiday saved = holidayRepository.save(holiday);
        return ResponseEntity.ok(toHolidayDto(saved));
    }

    @DeleteMapping("/{holidayId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<?> deleteHoliday(@PathVariable Integer holidayId) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", holidayId));
        holidayRepository.delete(holiday);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<Map<String, Object>> importHolidays(
            @RequestParam("file") MultipartFile file) {
        
        // Check file size (10 MB limit as per OpenAPI spec)
        long maxSize = 10 * 1024 * 1024; // 10 MB in bytes
        if (file.getSize() > maxSize) {
            throw new IllegalStateException("File size exceeds the 10 MB limit");
        }

        // Simplified implementation - would parse CSV/XLSX and import holidays
        Map<String, Object> result = new HashMap<>();
        result.put("created", 0);
        result.put("skipped", 0);
        result.put("errors", List.of("Import functionality not fully implemented"));

        return ResponseEntity.ok(result);
    }

    private HolidayDto toHolidayDto(Holiday holiday) {
        HolidayDto dto = new HolidayDto();
        dto.setId(holiday.getId());
        dto.setName(holiday.getName());
        dto.setDate(holiday.getDate());
        dto.setRestricted(holiday.isRestricted());
        if (holiday.getDepartment() != null) {
            dto.setDepartmentId(holiday.getDepartment().getId());
            // Safe getName() call with null check
            String deptName = holiday.getDepartment().getName();
            dto.setDepartmentName(deptName != null ? deptName : "Unknown");
        }
        return dto;
    }
}
