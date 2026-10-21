package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    List<Admin> findByAtivoTrue();
}
