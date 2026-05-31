package com.loyalty.saas.domain.repository;

import com.loyalty.saas.common.repository.BaseRepository;
import com.loyalty.saas.domain.entity.Member;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends BaseRepository<Member, String> {

    @Query("SELECT m FROM Member m WHERE m.programCode = :pc AND m.memberId = :mid")
    Optional<Member> findByMemberId(@Param("pc") String programCode, @Param("mid") Long memberId);

    @Query("SELECT m FROM Member m WHERE m.programCode = :pc AND m.memberId = :mid")
    Optional<Member> findByMemberIdForUpdate(@Param("pc") String programCode, @Param("mid") Long memberId);
}