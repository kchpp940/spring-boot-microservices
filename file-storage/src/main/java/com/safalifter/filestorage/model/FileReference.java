package com.safalifter.filestorage.model;

import lombok.*;

import javax.persistence.*;

@Entity(name = "file_references")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fileId", "entityType", "entityId"})
})
public class FileReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileId;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private String entityId;
}
