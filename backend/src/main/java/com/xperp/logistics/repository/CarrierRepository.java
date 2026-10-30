package com.xperp.logistics.repository;

import com.xperp.logistics.domain.Carrier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRepository extends JpaRepository<Carrier, Long> {

  List<Carrier> findAllByOrderByCodeAsc();
}
