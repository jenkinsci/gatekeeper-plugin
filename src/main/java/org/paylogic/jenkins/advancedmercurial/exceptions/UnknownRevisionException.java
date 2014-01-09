package org.paylogic.jenkins.advancedmercurial.exceptions;

public class UnknownRevisionException extends MercurialException {
    public UnknownRevisionException(String message) {
        super(message);
    }
}
