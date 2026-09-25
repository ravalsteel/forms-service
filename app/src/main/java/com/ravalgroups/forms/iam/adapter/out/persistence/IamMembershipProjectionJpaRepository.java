package com.ravalgroups.forms.iam.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamMembershipProjectionJpaRepository extends JpaRepository<IamMembershipProjectionEntity, UUID> {

    Optional<IamMembershipProjectionEntity> findByCompanyIdAndEmployeeId(UUID companyId, String employeeId);

    Optional<IamMembershipProjectionEntity> findByCompanyIdAndIamUserId(UUID companyId, UUID iamUserId);

    Page<IamMembershipProjectionEntity> findByCompanyId(UUID companyId, Pageable pageable);

    @Query(
            """
            select m from IamMembershipProjectionEntity m
            where m.companyId = :companyId
              and (
                lower(coalesce(m.displayName, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.employeeId, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(m.email, '')) like lower(concat('%', :q, '%'))
              )
            """)
    Page<IamMembershipProjectionEntity> searchByCompany(
            @Param("companyId") UUID companyId, @Param("q") String q, Pageable pageable);

    List<IamMembershipProjectionEntity> findByIamUserId(UUID iamUserId);

    List<IamMembershipProjectionEntity> findByDepartmentId(UUID departmentId);

    long countByCompanyId(UUID companyId);
}
