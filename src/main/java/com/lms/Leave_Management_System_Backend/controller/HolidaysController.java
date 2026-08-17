package com.lms.Leave_Management_System_Backend.controller;

import com.lms.Leave_Management_System_Backend.dto.*;
import com.lms.Leave_Management_System_Backend.exception.ResourceNotFoundException;
import com.lms.Leave_Management_System_Backend.model.Holiday;
import com.lms.Leave_Management_System_Backend.repository.HolidayRepository;
import com.lms.Leave_Management_System_Backend.security.RequireRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/holidays")
public class HolidaysController {

    private final HolidayRepository holidayRepository;

    public HolidaysController(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @GetMapping
    @RequireRole({"EMPLOYEE", "MANAGER", "HR_ADMIN"})
    public ResponseEntity<ApiResponse<List<HolidayDto>>> listHolidays(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        
        List<Holiday> holidays;
        if (fromDate != null && toDate != null) {
            holidays = holidayRepository.findByDateBetween(
                    LocalDate.parse(fromDate),
                    LocalDate.parse(toDate));
        } else if (fromDate != null) {
            holidays = holidayRepository.findByDateAfter(LocalDate.parse(fromDate));
        } else {
            holidays = holidayRepository.findAll();
        }

        List<HolidayDto> holidayDtos = holidays.stream()
                .map(this::toHolidayDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new ApiResponse<>(true, holidayDtos));
    }

    @PostMapping
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<ApiResponse<HolidayDto>> createHoliday(
            @RequestBody HolidayDto request) {
        
        Holiday holiday = new Holiday();
        holiday.setName(request.getName());
        holiday.setDate(request.getDate());
        holiday.setRecurring(request.isRecurring());

        Holiday saved = holidayRepository.save(holiday);
        HolidayDto dto = toHolidayDto(saved);
        
        return ResponseEntity.status(201).body(new ApiResponse<>(true, dto));
    }

    @DeleteMapping("/{holidayId}")
    @RequireRole({"HR_ADMIN"})
    public ResponseEntity<?> deleteHoliday(@PathVariable Integer holidayId) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", holidayId));
        holidayRepository.delete(holiday);
        return ResponseEntity.noContent().build();
    }

    private HolidayDto toHolidayDto(Holiday holiday) {
        HolidayDto dto = new HolidayDto();
        dto.setId(holiday.getId());
        dto.setName(holiday.getName());
        dto.setDate(holiday.getDate());
        dto.setRecurring(holiday.isRecurring());
        return dto;
    }
}
