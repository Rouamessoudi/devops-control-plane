package com.dcp.auth.repo;

import com.dcp.auth.domain.Role;
import com.dcp.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRolesContaining(Role role);

    @Query("select distinct account from User account left join fetch account.roles order by account.email asc")
    List<User> findAllWithRoles();
}
