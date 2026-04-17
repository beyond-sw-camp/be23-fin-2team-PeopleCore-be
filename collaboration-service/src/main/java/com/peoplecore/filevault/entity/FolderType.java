package com.peoplecore.filevault.entity;

/**
 * 파일함(폴더) 타입.
 *
 * <ul>
 *   <li>{@link #PERSONAL} — 개인 파일함 (본인만 접근, {@code ownerEmpId} 필수)</li>
 *   <li>{@link #COMPANY}  — 전사 공용 (전 직원 read, 쓰기는 capability)</li>
 *   <li>{@link #DEPT}     — 부서 파일함 ({@code deptId} 필수)</li>
 * </ul>
 */
public enum FolderType {
    PERSONAL,
    COMPANY,
    DEPT
}
