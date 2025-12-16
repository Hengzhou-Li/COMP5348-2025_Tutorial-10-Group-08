package com.example.store.service;

import com.example.store.model.Product;
import com.example.store.model.Warehouse;
import java.util.Collections;
import java.util.List;

public class AllocationPlan {

    private final List<AllocationLine> lines;

    public AllocationPlan(List<AllocationLine> lines) {
        this.lines = List.copyOf(lines);
    }

    public List<AllocationLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public record AllocationLine(Warehouse warehouse, Product product, int quantity) {
    }
}
