package com.medicare.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One test in an order. Name, price, unit and reference range are copied from the catalog when ordered.
 * The result value and comment are typed by the ordering doctor; the system never interprets them.
 */
@Entity
@Table(name = "lab_order_items")
public class LabOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private LabTest test;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    @Column(name = "price_at_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrder;

    @Column(length = 30)
    private String unit;

    @Column(name = "reference_range", length = 100)
    private String referenceRange;

    @Column(name = "result_value", length = 100)
    private String resultValue;

    @Column(name = "result_comment", length = 500)
    private String resultComment;

    protected LabOrderItem() {
        // for JPA
    }

    public LabOrderItem(LabOrder order, LabTest test) {
        this.order = order;
        this.test = test;
        this.testName = test.getName();
        this.priceAtOrder = test.getPrice();
        this.unit = test.getUnit();
        this.referenceRange = test.getReferenceRange();
    }

    public void recordResult(String value, String comment) {
        this.resultValue = value;
        this.resultComment = comment;
    }

    public Long getId() { return id; }
    public LabOrder getOrder() { return order; }
    public LabTest getTest() { return test; }
    public String getTestName() { return testName; }
    public BigDecimal getPriceAtOrder() { return priceAtOrder; }
    public String getUnit() { return unit; }
    public String getReferenceRange() { return referenceRange; }
    public String getResultValue() { return resultValue; }
    public String getResultComment() { return resultComment; }
}
