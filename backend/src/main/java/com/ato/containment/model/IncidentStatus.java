package com.ato.containment.model;

/**
 * OPEN -> INVESTIGATING -> CONTAINED -> RECOVERED -> CLOSED.
 * Valid transitions are enforced in incident/IncidentService, not here --
 * this enum just names the states.
 */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    CONTAINED,
    RECOVERED,
    CLOSED
}
