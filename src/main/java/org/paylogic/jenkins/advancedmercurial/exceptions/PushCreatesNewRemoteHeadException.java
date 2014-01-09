package org.paylogic.jenkins.advancedmercurial.exceptions;

public class PushCreatesNewRemoteHeadException extends MercurialException {
    public PushCreatesNewRemoteHeadException(String message) {
        super(message);
    }
}
