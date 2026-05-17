package com.clist.domain.feedback.repository;

import com.clist.domain.feedback.entity.FeedbackSession;
import com.clist.domain.md.entity.MdDocument;
import com.clist.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackSessionRepository extends JpaRepository<FeedbackSession, UUID> {
    List<FeedbackSession> findAllByUser(User user);
    List<FeedbackSession> findAllByMdDocument(MdDocument mdDocument);

    @Query("SELECT fs FROM FeedbackSession fs WHERE fs.user = :user AND fs.status = 'ACTIVE'")
    Optional<FeedbackSession> findActiveSessionByUser(@Param("user") User user);
}