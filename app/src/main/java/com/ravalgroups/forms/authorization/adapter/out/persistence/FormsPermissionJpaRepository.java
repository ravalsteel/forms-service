package com.ravalgroups.forms.authorization.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormsPermissionJpaRepository extends JpaRepository<FormsPermissionEntity, String> {

    List<FormsPermissionEntity> findAllByOrderByCodeAsc();
}
