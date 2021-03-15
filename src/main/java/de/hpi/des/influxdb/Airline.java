package de.hpi.des.influxdb;

import java.io.Serializable;

public class Airline implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String icaoCode;
    public String name;

    public Airline() {}
    public Airline(String icaoCode, String name) {
        this.icaoCode = icaoCode;
        this.name = name;
    }
}
