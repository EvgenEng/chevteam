package ru.chevteam.onestep.intent;

/** Куда человеку важно быстро добираться от отеля. */
public enum TransitKind {
    AIRPORT("аэропорта"),
    RAIL("ж/д вокзала"),
    METRO("метро"),
    BUS("автовокзала");

    private final String genitive;

    TransitKind(String genitive) {
        this.genitive = genitive;
    }

    /** «до аэропорта», «до метро». */
    public String genitive() {
        return genitive;
    }
}
