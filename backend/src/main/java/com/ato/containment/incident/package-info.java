/**
 * Incident management (IncidentService) and account containment
 * (ContainmentService) -- the two are tightly linked: containing an account
 * always opens or updates an incident, and the incident lifecycle tracks
 * what happens to a contained account afterward.
 */
package com.ato.containment.incident;
