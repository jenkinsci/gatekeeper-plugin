package org.paylogic.jenkins.advancedscm.backends;

import org.paylogic.jenkins.advancedscm.Branch;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by bubenkoff on 4/19/14.
 */
public class BaseBackend {
    /**
     * Get Mercurial branches from command line output,
     * and put them in a List so it's nice to work with.
     * @param all: get all or only open branches
     * @return List of String
     */
    public List<String> getBranchNames(boolean all) {
        List<String> list = new ArrayList<String>();
        for (Branch branch: this.getBranches(all)) {
            list.add(branch.getBranchName());
        }
        return list;
    }
}
