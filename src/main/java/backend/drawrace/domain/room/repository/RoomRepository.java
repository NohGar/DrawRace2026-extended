package backend.drawrace.domain.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.drawrace.domain.room.entity.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {}
