package com.clist.domain.quiz.repository;

import com.clist.domain.md.entity.MdDocument;
import com.clist.domain.quiz.entity.QuizSession;
import com.clist.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {
    List<QuizSession> findAllByUser(User user);
    List<QuizSession> findAllByUserAndMdDocument_Title(User user, String mdTitle);
    List<QuizSession> findAllByMdDocument(MdDocument mdDocument);

    @Query("SELECT qs FROM QuizSession qs WHERE qs.user = :user AND qs.status = 'ACTIVE'")
    Optional<QuizSession> findActiveSessionByUser(@Param("user") User user);
}