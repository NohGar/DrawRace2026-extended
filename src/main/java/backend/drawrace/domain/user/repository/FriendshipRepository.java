package backend.drawrace.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import backend.drawrace.domain.user.entity.Friendship;
import backend.drawrace.domain.user.entity.FriendshipStatus;
import backend.drawrace.domain.user.entity.User;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query(
            "SELECT f FROM Friendship f WHERE (f.requester = :a AND f.receiver = :b) OR (f.requester = :b AND f.receiver = :a)")
    Optional<Friendship> findByUsers(@Param("a") User a, @Param("b") User b);

    @Query("SELECT f FROM Friendship f JOIN FETCH f.requester WHERE f.receiver = :receiver AND f.status = :status")
    List<Friendship> findAllByReceiverAndStatus(
            @Param("receiver") User receiver, @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f JOIN FETCH f.receiver WHERE f.requester = :requester AND f.status = :status")
    List<Friendship> findAllByRequesterAndStatus(
            @Param("requester") User requester, @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f " + "JOIN FETCH f.requester "
            + "JOIN FETCH f.receiver "
            + "WHERE (f.requester = :user OR f.receiver = :user) "
            + "AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriends(@Param("user") User user);
}
