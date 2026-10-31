package com.phenom.selfservicebooking.host.repository;

import com.phenom.selfservicebooking.host.entity.Host;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostRepository extends JpaRepository<Host, Long> {
}
