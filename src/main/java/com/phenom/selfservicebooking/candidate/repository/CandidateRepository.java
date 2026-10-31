package com.phenom.selfservicebooking.candidate.repository;

import com.phenom.selfservicebooking.candidate.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
}
