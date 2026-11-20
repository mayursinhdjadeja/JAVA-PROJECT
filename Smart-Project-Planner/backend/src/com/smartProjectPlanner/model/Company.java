package com.smartProjectPlanner.model;

import java.sql.Timestamp;

/**
 * Tenant Company Model.
 */
public class Company {
    private int companyId;
    private String companyName;
    private String companyCode;
    private Timestamp createdAt;

    public Company() {}

    public Company(int companyId, String companyName, String companyCode) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.companyCode = companyCode;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Company{" +
                "companyId=" + companyId +
                ", companyName='" + companyName + '\'' +
                ", companyCode='" + companyCode + '\'' +
                '}';
    }
}
