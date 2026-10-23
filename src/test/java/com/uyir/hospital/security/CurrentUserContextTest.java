package com.uyir.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.model.Hospital;
import com.uyir.hospital.repository.HospitalRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CurrentUserContextTest {

    @Mock
    private HospitalRepository hospitalRepository;

    private CurrentUserContext context(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(CurrentUserContext.USER_ID_HEADER, userId);
        }
        if (role != null) {
            request.addHeader(CurrentUserContext.USER_ROLE_HEADER, role);
        }
        return new CurrentUserContext(request, hospitalRepository);
    }

    @Test
    void requireHospitalId_resolvesHospitalCreatedByUser() {
        when(hospitalRepository.findByUserId("u1"))
                .thenReturn(Optional.of(Hospital.builder().id("h1").userId("u1").build()));

        assertThat(context("u1", "HOSPITAL").requireHospitalId()).isEqualTo("h1");
    }

    @Test
    void requireHospitalId_userHasNoHospital_throwsForbidden() {
        when(hospitalRepository.findByUserId("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> context("u1", "HOSPITAL").requireHospitalId())
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("u1");
    }

    @Test
    void requireHospitalId_notHospitalRole_throwsWithoutLookup() {
        assertThatThrownBy(() -> context("p1", "PATIENT").requireHospitalId())
                .isInstanceOf(ForbiddenException.class);

        verify(hospitalRepository, never()).findByUserId(any());
    }
}
