package com.peoplecore.filevault.permission.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FileBoxAclId implements Serializable {
    private Long folderId;
    private Long empId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileBoxAclId that)) return false;
        return Objects.equals(folderId, that.folderId) && Objects.equals(empId, that.empId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folderId, empId);
    }
}
