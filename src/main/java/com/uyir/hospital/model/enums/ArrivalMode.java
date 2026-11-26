package com.uyir.hospital.model.enums;

// How an emergency patient is getting to the hospital, as the patient told us.
// UYIR_AMBULANCE: an ambulance booked in the Uyir app; the hospital follows that ambulance's GPS.
// OTHER_AMBULANCE: 108, a private ambulance, etc.; the hospital follows the patient's phone.
// OWN_WAY: their own vehicle, a taxi, etc.; the hospital follows the patient's phone.
public enum ArrivalMode {
    UYIR_AMBULANCE,
    OTHER_AMBULANCE,
    OWN_WAY
}
