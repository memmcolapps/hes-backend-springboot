package com.memmcol.hes.service;

import org.springframework.stereotype.Service;

public class AssociationLostException extends Exception  {
    public AssociationLostException() {
        super("Association with the meter has been lost.");
    }

    public AssociationLostException(String message) {
        super(message);
    }

    public AssociationLostException(String message, Throwable cause) {
        super(message, cause);
    }

    public AssociationLostException(Throwable cause) {
        super("Association with the meter has been lost.", cause);
    }

}
