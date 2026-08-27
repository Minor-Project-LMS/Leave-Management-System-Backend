package com.lms.Leave_Management_System_Backend.dto;

import java.time.LocalDateTime;

public class CommentDto {
    private Integer id;
    private Long requestId;
    private Long authorId;
    private String authorName;
    private String message;
    private LocalDateTime createdAt;

    // Constructors
    public CommentDto() {}

    public CommentDto(Long id, Long requestId, Long authorId, String authorName, String message, LocalDateTime createdAt) {
        this.id = id.intValue();
        this.requestId = requestId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.message = message;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Backward compatibility
    public Integer getCommentId() {
        return id;
    }

    public void setCommentId(Integer commentId) {
        this.id = commentId;
    }

    public UserDto getAuthor() {
        UserDto author = new UserDto();
        author.setId(authorId);
        author.setName(authorName);
        return author;
    }

    public void setAuthor(UserDto author) {
        this.authorId = author.getId();
        this.authorName = author.getName();
    }

    public String getComment() {
        return message;
    }

    public void setComment(String comment) {
        this.message = comment;
    }
}