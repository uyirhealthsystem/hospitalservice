package com.uyir.hospital.repository;

import com.uyir.hospital.model.Doctor;
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
public class DoctorRepositoryCustomImpl implements DoctorRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Doctor> search(DoctorSearchCriteria criteria, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();

        if (criteria.getSpecialty() != null && !criteria.getSpecialty().isBlank()) {
            filters.add(Criteria.where("specialties").regex("^" + Pattern.quote(criteria.getSpecialty()) + "$", "i"));
        }
        if (criteria.getHospitalId() != null && !criteria.getHospitalId().isBlank()) {
            filters.add(Criteria.where("hospitalAssociations.hospitalId").is(criteria.getHospitalId()));
        }
        if (criteria.getEngagementType() != null) {
            filters.add(Criteria.where("engagementType").is(criteria.getEngagementType()));
        }
        if (criteria.getDoctorCategory() != null) {
            filters.add(Criteria.where("doctorCategory").is(criteria.getDoctorCategory()));
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

        long total = mongoTemplate.count(countQuery, Doctor.class);
        List<Doctor> content = mongoTemplate.find(findQuery, Doctor.class);

        return new PageImpl<>(content, pageable, total);
    }
}
