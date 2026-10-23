package com.uyir.hospital.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.dto.PayoutAccountResponse;
import com.uyir.hospital.exception.PaymentUnavailableException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import com.uyir.hospital.model.HospitalPayoutAccount;
import com.uyir.hospital.model.enums.PayoutAccountStatus;
import com.uyir.hospital.payment.LinkedAccountClient;
import com.uyir.hospital.payment.LinkedAccountClient.LinkedAccount;
import com.uyir.hospital.repository.HospitalPayoutAccountRepository;
import com.uyir.hospital.repository.HospitalRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HospitalPayoutAccountServiceImplTest {

    @Mock
    private HospitalPayoutAccountRepository payoutAccountRepository;

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private LinkedAccountClient linkedAccountClient;

    private HospitalPayoutAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HospitalPayoutAccountServiceImpl(payoutAccountRepository, hospitalRepository, linkedAccountClient);
    }

    @Test
    void setUpStoresWhatPaymentServiceReports() {
        PayoutAccountRequest request = request();
        when(hospitalRepository.existsById("h1")).thenReturn(true);
        when(linkedAccountClient.onboard("h1", request, "token")).thenReturn(linked(PayoutAccountStatus.UNDER_REVIEW));
        when(payoutAccountRepository.findById("h1")).thenReturn(Optional.empty());
        when(payoutAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayoutAccountResponse response = service.setUp("h1", request, "token");

        assertThat(response.getHospitalId()).isEqualTo("h1");
        assertThat(response.getStatus()).isEqualTo(PayoutAccountStatus.UNDER_REVIEW);
        assertThat(response.isPayoutsEnabled()).isFalse();
        assertThat(response.getBankAccountLast4()).isEqualTo("9012");
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void setUpForAnUnknownHospitalNeverReachesPaymentService() {
        when(hospitalRepository.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.setUp("nope", request(), "token")).isInstanceOf(ResourceNotFoundException.class);
        verify(linkedAccountClient, never()).onboard(anyString(), any(), anyString());
    }

    @Test
    void getRefreshesAnAccountStillInReview() {
        when(payoutAccountRepository.findById("h1")).thenReturn(Optional.of(stored(PayoutAccountStatus.UNDER_REVIEW)));
        when(linkedAccountClient.refresh("la-1", "token")).thenReturn(linked(PayoutAccountStatus.ACTIVATED));
        when(payoutAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayoutAccountResponse response = service.get("h1", "token");

        assertThat(response.getStatus()).isEqualTo(PayoutAccountStatus.ACTIVATED);
        assertThat(response.isPayoutsEnabled()).isTrue();
    }

    @Test
    void getReturnsTheLastKnownStatusWhenPaymentServiceIsDown() {
        when(payoutAccountRepository.findById("h1")).thenReturn(Optional.of(stored(PayoutAccountStatus.UNDER_REVIEW)));
        when(linkedAccountClient.refresh("la-1", "token")).thenThrow(new PaymentUnavailableException("down"));

        assertThat(service.get("h1", "token").getStatus()).isEqualTo(PayoutAccountStatus.UNDER_REVIEW);
    }

    @Test
    void anActivatedAccountIsNotRefreshed() {
        when(payoutAccountRepository.findById("h1")).thenReturn(Optional.of(stored(PayoutAccountStatus.ACTIVATED)));

        assertThat(service.get("h1", "token").isPayoutsEnabled()).isTrue();
        verify(linkedAccountClient, never()).refresh(anyString(), anyString());
    }

    @Test
    void payoutsEnabledOnlyWhenActivated() {
        when(payoutAccountRepository.findById("h1")).thenReturn(Optional.of(stored(PayoutAccountStatus.NEEDS_CLARIFICATION)));
        when(payoutAccountRepository.findById("h2")).thenReturn(Optional.empty());

        assertThat(service.payoutsEnabled("h1")).isFalse();
        assertThat(service.payoutsEnabled("h2")).isFalse();
    }

    private static LinkedAccount linked(PayoutAccountStatus status) {
        return new LinkedAccount("la-1", status, "acc_123", List.of(), "Sri Ram Hospital Pvt Ltd", "9012", "HDFC0000317",
                "Sri Ram Hospital", status == PayoutAccountStatus.ACTIVATED ? Instant.now() : null);
    }

    private static HospitalPayoutAccount stored(PayoutAccountStatus status) {
        return HospitalPayoutAccount.builder()
                .hospitalId("h1")
                .linkedAccountId("la-1")
                .status(status)
                .bankAccountLast4("9012")
                .createdAt(Instant.now())
                .build();
    }

    private static PayoutAccountRequest request() {
        return PayoutAccountRequest.builder()
                .legalBusinessName("Sri Ram Hospital Pvt Ltd")
                .businessType("private_limited")
                .contactName("Ravi Kumar")
                .email("accounts@sriram.in")
                .phone("9876543210")
                .address(PayoutAccountRequest.RegisteredAddress.builder()
                        .street1("12 Anna Salai").city("Chennai").state("Tamil Nadu").postalCode("600002").build())
                .pan("AAACS1234C")
                .stakeholder(PayoutAccountRequest.Stakeholder.builder()
                        .name("Ravi Kumar").email("ravi@sriram.in").pan("ABCPK1234D").build())
                .bankAccount(PayoutAccountRequest.BankAccount.builder()
                        .accountNumber("123456789012").ifscCode("HDFC0000317").beneficiaryName("Sri Ram Hospital").build())
                .tncAccepted(true)
                .build();
    }
}
