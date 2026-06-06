package com.loyalty.saas.domain.repository;

import com.loyalty.saas.domain.entity.MemberUniqueKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberUniqueKeyRepository extends JpaRepository<MemberUniqueKey, Long> {

    @Query("SELECT k.targetMemberId FROM MemberUniqueKey k WHERE k.programCode=:pc AND k.keyType=:type AND k.keyValue=:val")
    Optional<String> findMemberId(@Param("pc") String programCode, @Param("type") String keyType, @Param("val") String keyValue);

    @Query("SELECT k FROM MemberUniqueKey k WHERE k.programCode=:pc AND k.targetMemberId=:mid")
    List<MemberUniqueKey> findByTargetMemberId(@Param("pc") String programCode, @Param("mid") String memberId);
}