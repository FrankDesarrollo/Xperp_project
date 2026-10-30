package com.xperp.notifications.repository;

import com.xperp.notifications.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  Page<Notification> findByUserId(Long userId, Pageable pageable);

  long countByUserIdAndReadAtIsNull(Long userId);

  List<Notification> findByUserIdAndReadAtIsNull(Long userId);
}
