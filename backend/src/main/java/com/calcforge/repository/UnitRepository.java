package com.calcforge.repository;

import com.calcforge.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findAllByCategoryOrderBySortOrderAsc(String category);
    Optional<Unit> findByCategoryAndSymbol(String category, String symbol);
    List<Unit> findAllByOrderByCategoryAscSortOrderAsc();

    @Query("SELECT DISTINCT u.category FROM Unit u ORDER BY u.category ASC")
    List<String> findAllCategories();
}
