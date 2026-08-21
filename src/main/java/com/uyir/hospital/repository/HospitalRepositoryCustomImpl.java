package com.uyir.hospital.repository;

import com.uyir.hospital.model.Hospital;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class HospitalRepositoryCustomImpl implements HospitalRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Hospital> search(HospitalSearchCriteria criteria, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();

        if (criteria.getCity() != null && !criteria.getCity().isBlank()) {
            filters.add(Criteria.where("address.city").regex("^" + Pattern.quote(criteria.getCity()) + "$", "i"));
        }
        if (criteria.getState() != null && !criteria.getState().isBlank()) {
            filters.add(Criteria.where("address.state").regex("^" + Pattern.quote(criteria.getState()) + "$", "i"));
        }
        if (criteria.getHospitalType() != null) {
            filters.add(Criteria.where("hospitalType").is(criteria.getHospitalType()));
        }
        if (criteria.getOwnershipType() != null) {
            filters.add(Criteria.where("ownershipType").is(criteria.getOwnershipType()));
        }
        if (criteria.getActive() != null) {
            filters.add(Criteria.where("active").is(criteria.getActive()));
        }

        Query countQuery = new Query();
        Query findQuery = new Query().with(pageable);
        if (!filters.isEmpty()) {
            Criteria combined = new Criteria().andOperator(filters.toArray(new Criteria[0]));
            countQuery.addCriteria(combined);
            findQuery.addCriteria(combined);
        }

        long total = mongoTemplate.count(countQuery, Hospital.class);
        List<Hospital> content = mongoTemplate.find(findQuery, Hospital.class);

        return new PageImpl<>(content, pageable, total);
    }
}
