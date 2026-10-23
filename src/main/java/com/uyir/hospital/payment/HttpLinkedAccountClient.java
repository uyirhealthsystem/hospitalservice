package com.uyir.hospital.payment;

import com.uyir.hospital.dto.PayoutAccountRequest;
import com.uyir.hospital.exception.ConflictException;
import com.uyir.hospital.exception.ForbiddenException;
import com.uyir.hospital.exception.PaymentUnavailableException;
import com.uyir.hospital.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// PUT  {baseUrl}/api/payments/linked-accounts                 onboard / continue / change bank account
// POST {baseUrl}/api/payments/linked-accounts/{id}/refresh    activation status from Razorpay now
//   Authorization: Bearer <the hospital user's JWT>   (payment-service keys the account to the token's sub)
// payment-service answers errors as RFC 7807 ProblemDetail; a 400 carries Razorpay's reason
// ("The pan field is invalid.") and is passed on to the hospital as a 400.
@Component
@ConditionalOnProperty(name = "hospital.payout.provider", havingValue = "http", matchIfMissing = true)
public class HttpLinkedAccountClient implements LinkedAccountClient {

    private static final Logger log = LoggerFactory.getLogger(HttpLinkedAccountClient.class);
    private static final String PATH = "/api/payments/linked-accounts";

    private final RestClient http;
    private final PayoutProperties props;
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpLinkedAccountClient(PayoutProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        this.http = RestClient.builder().requestFactory(factory).baseUrl(props.baseUrl()).build();
    }

    @Override
    public LinkedAccount onboard(String hospitalId, PayoutAccountRequest request, String bearerToken) {
        requireToken(bearerToken);
        Map<String, Object> body = body(hospitalId, request);
        return call(() -> http.put().uri(PATH)
                .header("Authorization", "Bearer " + bearerToken)
                .body(body)
                .retrieve()
                .body(LinkedAccount.class), "onboard payout account for hospital " + hospitalId);
    }

    @Override
    public LinkedAccount refresh(String linkedAccountId, String bearerToken) {
        requireToken(bearerToken);
        return call(() -> http.post().uri(PATH + "/{id}/refresh", linkedAccountId)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .body(LinkedAccount.class), "refresh payout account " + linkedAccountId);
    }

    private Map<String, Object> body(String hospitalId, PayoutAccountRequest r) {
        PayoutAccountRequest.RegisteredAddress a = r.getAddress();
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("street1", a.getStreet1());
        address.put("street2", a.getStreet2());
        address.put("city", a.getCity());
        address.put("state", a.getState());
        address.put("postalCode", a.getPostalCode());
        address.put("country", "IN");

        PayoutAccountRequest.Stakeholder s = r.getStakeholder();
        Map<String, Object> stakeholder = new LinkedHashMap<>();
        stakeholder.put("name", s.getName());
        stakeholder.put("email", s.getEmail());
        stakeholder.put("phone", s.getPhone());
        stakeholder.put("pan", s.getPan());

        PayoutAccountRequest.BankAccount b = r.getBankAccount();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ownerType", OWNER_TYPE);
        body.put("referenceId", hospitalId);
        body.put("legalBusinessName", r.getLegalBusinessName());
        body.put("businessType", r.getBusinessType());
        body.put("contactName", r.getContactName());
        body.put("email", r.getEmail());
        body.put("phone", r.getPhone());
        body.put("category", props.category());
        body.put("subcategory", props.subcategory());
        body.put("address", address);
        body.put("pan", r.getPan());
        body.put("gst", r.getGst());
        body.put("stakeholder", stakeholder);
        body.put("bankAccount", Map.of(
                "accountNumber", b.getAccountNumber(),
                "ifscCode", b.getIfscCode(),
                "beneficiaryName", b.getBeneficiaryName()));
        body.put("tncAccepted", Boolean.TRUE.equals(r.getTncAccepted()));
        return body;
    }

    private LinkedAccount call(Supplier<LinkedAccount> request, String what) {
        LinkedAccount account;
        try {
            account = request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String detail = detail(e.getResponseBodyAsString());
            log.warn("payment-service {} failed: HTTP {} {}", what, status, detail);
            String message = detail != null ? detail : "Payment service refused the request (HTTP " + status + ")";
            throw switch (status) {
                case 400 -> new IllegalArgumentException(message);
                case 401, 403 -> new ForbiddenException(message);
                case 404 -> new ResourceNotFoundException(message);
                case 409 -> new ConflictException(message);
                default -> new PaymentUnavailableException("Payment service unavailable, please retry");
            };
        } catch (RestClientException e) {
            log.error("payment-service {} failed: {}", what, e.getMessage());
            throw new PaymentUnavailableException("Payment service unavailable, please retry");
        }
        if (account == null || account.id() == null || account.status() == null) {
            log.error("payment-service {}: incomplete response", what);
            throw new PaymentUnavailableException("Payment service unavailable, please retry");
        }
        return account;
    }

    private String detail(String problemJson) {
        try {
            JsonNode detail = json.readTree(problemJson).path("detail");
            return detail.isString() && !detail.asString().isBlank() ? detail.asString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void requireToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new ForbiddenException("An Authorization bearer token is required to set up the payout account");
        }
    }
}
