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

    List<MemberUniqueKey> findByProgramCodeAndKeyCombinationAndKeyValue(
        @Param("pc") String programCode, @Param("kc") String keyCombination, @Param("kv") String keyValue);

    List<MemberUniqueKey> findByProgramCodeAndMemberId(
        @Param("pc") String programCode, @Param("mid") Long memberId);
}