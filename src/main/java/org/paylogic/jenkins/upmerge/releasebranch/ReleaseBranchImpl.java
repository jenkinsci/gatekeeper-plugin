package org.paylogic.jenkins.upmerge.releasebranch;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReleaseBranchImpl extends ReleaseBranch {
    private static String RELEASEBRANCH_REGEX = "r\\d{4}";  // TODO: parametrize correctly
    private final DecimalFormat df;

    private int year;
    private int week;
    private String DEFAULT = "default";
    private boolean tip;

    /**
     * Constructor of ReleaseBranch.
     * Split the name of the branch if needed and store locally.
     *
     * @param startBranch Name of branch to start with (String).
     */
    public ReleaseBranchImpl(String startBranch) throws ReleaseBranchInvalidException {
        super(startBranch);
        this.df = new DecimalFormat("00");
        if (startBranch == DEFAULT) {
            this.tip = true;
        }
        else {
            if (!startBranch.matches(RELEASEBRANCH_REGEX)) {
                throw new ReleaseBranchInvalidException("Release branch " + startBranch + " is invalid.");
            }

            String sYear = startBranch.substring(1, 3);
            String sWeek = startBranch.substring(3, 5);

            this.year = Integer.parseInt(sYear);
            this.week = Integer.parseInt(sWeek);
            this.tip = false;
        }
    }

    public ReleaseBranchImpl(String startBranch, String default_branch) throws ReleaseBranchInvalidException {
        this(startBranch);
        this.DEFAULT = default_branch;
    }

    /**
     * Sets the object to the next release.
     * Does not return representation of release.
     */
    @Override
    public void next() {
        this.week += 1;
        if (this.week > 52) {
            this.week = 1;
            this.year++;
        }
    }

    /**
     * Sets the object to the next release.
     * Does not return representation of release.
     */
    @Override
    public void next(List<String> branches) {
        branches = new ArrayList<String>(branches);
        CollectionUtils.filter(branches, new Predicate(){
            public boolean evaluate(Object input ) {
                return ((String)input).matches(RELEASEBRANCH_REGEX);
            }
        });
        String maxBranch = Collections.max(branches);
        this.next();
        while (!branches.contains(this.getName()) && !this.tip) {
            this.next();
            if (this.getName().compareTo(maxBranch) > 0) {
                this.tip = true;
                break;
            }
        }
    }

    /**
     * Returns the current branch name as String
     *
     * @return current branch name
     */
    @Override
    public String getName() {
        if (this.tip) {
            return DEFAULT;
        }
        return String.format("r%s%s", df.format(this.year), df.format(this.week));
    }

    /**
     * Create a new ReleaseBranch object with the current release branch.
     *
     * @return new ReleaseBranch
     */
    @Override
    public ReleaseBranch copy() throws ReleaseBranchInvalidException {
        return new ReleaseBranchImpl(this.getName(), this.DEFAULT);
    }
}
