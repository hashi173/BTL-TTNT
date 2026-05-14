package com.coffeeshop.controller;

import com.coffeeshop.entity.Order;
import com.coffeeshop.entity.OrderStatus;
import com.coffeeshop.entity.Product;
import com.coffeeshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/** Admin financial history - monthly revenue with Chart.js visualization. */
@Slf4j
@Controller
@RequestMapping("/admin/history")
@RequiredArgsConstructor
public class AdminHistoryController {

    private final OrderRepository orderRepository;

    @GetMapping
    public String history(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        Map<String, MonthlyStats> statsMap = new HashMap<>();

        try {
            // Aggregate monthly revenue from completed orders
            List<Object[]> revenueData = orderRepository.findMonthlyRevenue();
            for (Object[] row : revenueData) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                double total = ((Number) row[2]).doubleValue();
                String key = year + "-" + month;
                statsMap.putIfAbsent(key, new MonthlyStats(year, month));
                statsMap.get(key).setRevenue(total);
            }

            for (MonthlyStats stat : statsMap.values()) {
                List<Object[]> topProductData = orderRepository.findTopSellingProductByMonth(
                        stat.getMonth(), stat.getYear(), PageRequest.of(0, 1));
                if (!topProductData.isEmpty()) {
                    Object[] topRow = topProductData.get(0);
                    stat.setTopProductName((String) topRow[0]);
                    stat.setTopProductQuantity(((Number) topRow[1]).longValue());

                    stat.setTopProductImage(Product.resolveImagePath(topRow[2] != null ? topRow[2].toString() : null));
                }
            }

        } catch (Exception e) {
            log.error("Failed to build admin history statistics", e);
        }

        // Sort descending: newest first
        List<MonthlyStats> monthlyStats = new ArrayList<>(statsMap.values());
        monthlyStats.sort((a, b) -> {
            if (a.year != b.year) return b.year - a.year;
            return b.month - a.month;
        });

        // Ensure at least the current month is shown
        if (monthlyStats.isEmpty()) {
            java.time.LocalDate now = java.time.LocalDate.now();
            monthlyStats.add(new MonthlyStats(now.getYear(), now.getMonthValue()));
        }

        if (search != null && !search.isEmpty()) {
            String lowerSearch = search.toLowerCase();
            monthlyStats = monthlyStats.stream()
                    .filter(s -> s.getMonthLabel().toLowerCase().contains(lowerSearch) ||
                            String.valueOf(s.year).contains(lowerSearch) ||
                            s.getTopProductName().toLowerCase().contains(lowerSearch))
                    .collect(Collectors.toList());
        }

        int pageSize = 10;
        int totalItems = monthlyStats.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalItems);
        List<MonthlyStats> pagedStats = (start > totalItems) ? new ArrayList<>() : monthlyStats.subList(start, end);

        model.addAttribute("monthlyStats", pagedStats);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("search", search);

        // Chart data - last 12 months in chronological order
        List<String> labels = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();

        int chartLimit = Math.min(monthlyStats.size(), 12);
        for (int i = chartLimit - 1; i >= 0; i--) {
            MonthlyStats stat = monthlyStats.get(i);
            labels.add(stat.getMonthShortLabel());
            revenues.add(stat.revenue);
        }

        model.addAttribute("chartLabels", labels);
        model.addAttribute("chartRevenues", revenues);

        return "admin/history";
    }

    /** Shows detailed orders for a specific month with pagination. */
    @GetMapping("/details")
    public String details(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        int pageSize = 20;
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Order> ordersPage = orderRepository.findAllByStatusAndCreatedAtBetween(
                OrderStatus.COMPLETED, start, end, pageable);

        // Compute full-month totals (not just current page)
        List<Order> allOrdersForSum = orderRepository.findAllByStatusAndCreatedAtBetween(
                OrderStatus.COMPLETED, start, end);
        double revenueSum = allOrdersForSum.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0).sum();

        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("monthName", yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        model.addAttribute("detailsBaseUrl", "/admin/history/details?month=" + month + "&year=" + year);

        model.addAttribute("ordersPage", ordersPage);
        model.addAttribute("orders", ordersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        model.addAttribute("totalItems", ordersPage.getTotalElements());

        model.addAttribute("totalRevenue", revenueSum);

        return "admin/history_details";
    }

    /** Inner DTO for monthly aggregated statistics. */
    @lombok.Data
    public static class MonthlyStats {
        private int year;
        private int month;
        private double revenue = 0.0;
        private String topProductName = "No sales";
        private long topProductQuantity = 0;
        private String topProductImage = "/images/no-image.png";

        public MonthlyStats(int year, int month) {
            this.year = year;
            this.month = month;
        }

        public String getMonthName() {
            return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        }

        public String getMonthLabel() {
            return getMonthName() + " " + year;
        }

        public String getMonthShortLabel() {
            return Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
        }
    }
}
