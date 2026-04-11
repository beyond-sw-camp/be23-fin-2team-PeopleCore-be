package com.peoplecore.repository;

import com.peoplecore.document.SearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface SearchRepository extends ElasticsearchRepository<SearchDocument, String> {

    List<SearchDocument> findByTypeAndCompanyId(String type, String companyId);

    void deleteBySourceIdAndType(String sourceId, String type);
}
