package com.mcqbuddy.attempt.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExamInstituteAccessService {

    private static final String IS_ASSIGNED_SQL =
            """
            SELECT EXISTS (
              SELECT 1
              FROM institute.student s
              INNER JOIN institute.institute_class_exam ce
                ON ce.institute_class_id = s.institute_class_id
              INNER JOIN institute.institute_exam ie ON ie.id = ce.institute_exam_id
              WHERE lower(s.user_public_id) = lower(?)
                AND lower(ie.exam_public_key) = lower(?)
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public ExamInstituteAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isExamAssignedToStudent(String userPublicId, String examPublicKey) {
        if (userPublicId == null
                || userPublicId.isBlank()
                || examPublicKey == null
                || examPublicKey.isBlank()) {
            return false;
        }
        Boolean found =
                jdbcTemplate.queryForObject(
                        IS_ASSIGNED_SQL,
                        Boolean.class,
                        userPublicId.trim().toLowerCase(),
                        examPublicKey.trim().toLowerCase());
        return Boolean.TRUE.equals(found);
    }
}
