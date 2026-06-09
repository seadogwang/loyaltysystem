package com.loyalty.platform.domain.repository;

import com.loyalty.platform.common.repository.BaseRepository;
import com.loyalty.platform.domain.entity.MemberUniqueKey;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberUniqueKeyRepository extends BaseRepository<MemberUniqueKey, Long> {

    List<MemberUniqueKey> findByProgramCodeAndKeyCombinationAndKeyValue(
        @Param("pc") String programCode, @Param("kc") String keyCombination, @Param("kv") String keyValue);

    List<MemberUniqueKey> findByProgramCodeAndMemberId(
        @Param("pc") String programCode, @Param("mid") Long memberId);
}