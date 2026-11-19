package com.uyir.hospital.model.embedded;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uyir.hospital.model.enums.AmbulanceType;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyServices {

    private boolean handlesEmergencies;

    // Populated only when handlesEmergencies is true. Official names from the 16 SOS emergency conditions
    // (EmergencyConditionCatalog), enforced on save. A doctor on duty here must handle the condition too.
    private List<String> specialtyEmergencyConditionsHandled;

    private boolean ambulanceAvailable;
    private List<AmbulanceType> ambulanceTypes;
    private boolean ambulance24x7Available;
    private boolean willingToAttachAmbulanceToUdhs;

    // Emergency mode is a lease: turning it on keeps it on until this instant (hospital.emergency.on-duration,
    // default 6h, from the moment it was turned on). Set only by the server - a client-sent value is ignored.
    // After it passes the hospital counts as off at once (onAt), and EmergencyServicesExpiryJob also stores
    // handlesEmergencies=false within a minute. Null while on = turned on before leases existed; the job gives
    // it a fresh lease on its next run.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant emergencyOnUntil;

    // When the "about to switch off" warning went out for the current lease (once per lease; cleared on renewal).
    // Internal bookkeeping for EmergencyServicesExpiryJob, never in the API.
    @JsonIgnore
    private Instant expiryWarningSentAt;

    // Whether emergency mode is really on at `now`: the flag is set and its lease hasn't ended.
    public boolean onAt(Instant now) {
        return handlesEmergencies && (emergencyOnUntil == null || emergencyOnUntil.isAfter(now));
    }

    // The hospital is in emergency mode right now and chose this condition.
    public boolean handlesEmergencyType(String emergencyType) {
        return onAt(Instant.now())
                && specialtyEmergencyConditionsHandled != null
                && specialtyEmergencyConditionsHandled.stream().anyMatch(condition -> condition.equalsIgnoreCase(emergencyType));
    }
}
