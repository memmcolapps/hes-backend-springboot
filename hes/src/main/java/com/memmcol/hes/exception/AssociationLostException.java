package com.memmcol.hes.exception;

public class AssociationLostException extends Exception  {
    public AssociationLostException() {
        super("Association with the meter has been lost.");
    }

    public AssociationLostException(String message) {
        super(message);
    }
}
