package org.paylogic.jenkins.advancedmercurial.exceptions;

public class NothingChangedException extends MercurialException {
    public NothingChangedException(String message) {
        super(message);
    }
}
