package com.uyir.hospital.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurgicalNetwork {

    private boolean joinedSurgicalNetwork;

    // Populated only when joinedSurgicalNetwork is true
    private Integer operationTheatreCount;
    private boolean laserEquipmentAvailable;
    private boolean minimallyInvasiveEquipmentAvailable;
    private boolean postOpRecoveryRoomAvailable;
    private boolean endoscopyAvailable;
    private boolean laparoscopyUnitsAvailable;
}
