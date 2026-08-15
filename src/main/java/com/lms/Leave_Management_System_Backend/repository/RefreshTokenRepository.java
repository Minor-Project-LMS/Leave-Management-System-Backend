package com.lms.Leave_Management_System_Backend.repository;

import com.lms.Leave_Management_System_Backend.model.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Redis repository for managing refresh tokens.
 */
@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
}
