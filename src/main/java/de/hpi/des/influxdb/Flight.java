package de.hpi.des.influxdb;

import java.io.Serializable;

public class Flight implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String flightNumber;
    public String airlineIcaoCode;
    public String airlineName;
    public Boolean isOnGround;
    public Long seenAt;

    public Flight() {}
    public Flight(String flightNumber, String airlineIcaoCode, String airlineName, Boolean isOnGround, Long seenAt) {
        this.flightNumber = flightNumber;
        this.airlineIcaoCode = airlineIcaoCode;
        this.airlineName = airlineName;
        this.isOnGround = isOnGround;
        this.seenAt = seenAt;
    }

    public String toString() {
        String out = flightNumber;
        if(this.airlineName != null) {
            out += String.format(" (%s)", airlineName);
        }
        return out;
    }
}
