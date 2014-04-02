/*
 * Copyright (C) 2012 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package master.endconditions;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import master.model.Node;
import master.model.Population;

/**
 * A condition which is met when the simulation includes a specific
 * number of lineages.
 * 
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */

@Description("Lineage count end condition for an inheritance trajectory.")
public class LineageEndCondition extends EndCondition {
    
    public Input<List<Population>> populationInput = new Input<List<Population>>(
            "population",
            "Specific population to which lineages belong (optional).",
            new ArrayList<Population>());
    
    public Input<Integer> nLineagesInput = new Input<Integer>(
            "nLineages",
            "Linage count threshold for end condition.",
            Validate.REQUIRED);
    
    public Input<Boolean> exceedCondInput = new Input<Boolean>(
            "exceedCondition",
            "Whether condition is size>=threshold. False implies <=threshold.",
            true);
    
    private List<Population> pops;
    private int nlineages;
    private boolean exceed;
    
    public LineageEndCondition() { }
    
    @Override
    public void initAndValidate() {
        
        pops = populationInput.get();        
        nlineages = nLineagesInput.get();
        exceed = exceedCondInput.get();
    }

    /**
     * Returns true iff the given activeLineages meets the end condition.
     * 
     * @param activeLineages
     * @return true if the end condition is met.
     */
    public boolean isMet(Map<Population,List<Node>> activeLineages) {
        if (isPost())
            return false;
        
        int size;
        if (pops.isEmpty()) {
            size = 0;
            for (List<Node> nodeList : activeLineages.values())
                size += nodeList.size();
        } else {
            size = 0;
            for (Population pop : pops) {
                if (activeLineages.containsKey(pop))
                    size += activeLineages.get(pop).size();
            }
        }
        
        if (exceed)
            return size >= nlineages;
        else
            return size <= nlineages;
    }
    
    /**
     * Returns true iff the given activeLineages meets the end condition.
     * 
     * @param activeLineages
     * @return true if the end condition is met.
     */
    public boolean isMetPost(Map<Population,List<Node>> activeLineages) {
        if (!isPost())
            return false;
        
        int size;
        if (pops.isEmpty()) {
            size = 0;
            for (List<Node> nodeList : activeLineages.values())
                size += nodeList.size();
        } else {
            size = 0;
            for (Population pop : pops) {
                if (activeLineages.containsKey(pop))
                    size += activeLineages.get(pop).size();
            }
        }
        
        if (exceed)
            return size >= nlineages;
        else
            return size <= nlineages;
    }

    /**
     * @return Description of the end condition.
     */
    public String getConditionDescription() {
        return "Condition met when number of lineages reaches " + nlineages;
    }
    
}
