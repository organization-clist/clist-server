package com.clist.domain.quiz.repository;

import com.clist.domain.quiz.entity.QuizQuestion;
import com.clist.domain.quiz.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {
    List<QuizQuestion> findAllByQuizSession(QuizSession quizSession);

    @Query("SELECT qq FROM QuizQuestion qq WHERE qq.quizSession = :session AND qq.userAnswer IS NULL ORDER BY qq.createdAt ASC LIMIT 1")
    Optional<QuizQuestion> findFirstUnansweredBySession(@Param("session") QuizSession session);

    @Query("SELECT qq FROM QuizQuestion qq WHERE qq.quizSession = :session AND qq.isCorrect IS NOT NULL")
    List<QuizQuestion> findAnsweredBySession(@Param("session") QuizSession session);
}