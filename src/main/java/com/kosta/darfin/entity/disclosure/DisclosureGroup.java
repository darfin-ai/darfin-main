package com.kosta.darfin.entity.disclosure;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

// DisclosureGroup.java
@Entity
@Table(name = "disclosure_group")
@Getter
@NoArgsConstructor
public class DisclosureGroup {
    @Id
    @Column(length = 20)
    private String groupCode;

    @Column(nullable = false, length = 50)
    private String groupName;
}