package at.pepe.trader.model;

import java.io.Serializable;

public enum PositionStatus implements Serializable {
    WAITING_FOR_OPEN,
    OPENED,
    WAITING_FOR_CLOSE,
    FINISHED,
    CANCELLED
}
