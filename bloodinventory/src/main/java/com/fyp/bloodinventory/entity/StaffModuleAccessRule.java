package com.fyp.bloodinventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "staff_module_access",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_staff_module_access_role_key",
                columnNames = {"staff_type", "module_key"}
        )
)
public class StaffModuleAccessRule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "access_id")
    private Long accessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_type", nullable = false, length = 40)
    private StaffRole staffType;

    @Column(name = "module_key", nullable = false, length = 80)
    private String moduleKey;

    @Column(name = "module_name", nullable = false, length = 120)
    private String moduleName;

    @Column(name = "url_pattern", nullable = false, length = 180)
    private String urlPattern;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    public Long getAccessId() {
        return accessId;
    }

    public void setAccessId(Long accessId) {
        this.accessId = accessId;
    }

    public StaffRole getStaffType() {
        return staffType;
    }

    public void setStaffType(StaffRole staffType) {
        this.staffType = staffType;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

}
