package com.phenom.selfservicebooking.auth.repository;

import com.phenom.selfservicebooking.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserNameAndDeletedFalse(String userName);

    boolean existsByUserNameAndDeletedFalse(String userName);
}
