package com.ravalgroups.forms.authorization.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "forms_permission")
public class FormsPermissionEntity {

    @Id
    @Column(length = 128)
    private String code;

    @Column(nullable = false, length = 64)
    private String resource;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 512)
    private String description;

    protected FormsPermissionEntity() {}

    public String getCode() {
        return code;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }
}
