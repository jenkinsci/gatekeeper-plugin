package org.paylogic.jenkins;

import hudson.model.Cause;

public class ABuildCause extends Cause {
    @Override
    public String getShortDescription() {
        return "Triggered by test.";
    }
}
