package com.smartspend.web.repo;

import com.smartspend.web.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndMonth(Long userId, String month);

    Optional<Budget> findByUserIdAndMonthAndCategory(Long userId, String month, String category);

    @Transactional
    void deleteByUserIdAndMonthAndCategory(Long userId, String month, String category);

    List<Budget> findByUserIdIsNull();
}
