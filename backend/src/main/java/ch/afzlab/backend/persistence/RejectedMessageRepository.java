package ch.afzlab.backend.persistence;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectedMessageRepository extends JpaRepository<RejectedMessage, Long> {

    List<RejectedMessage> findAllByOrderByReceivedAtDesc(Limit limit);
}
