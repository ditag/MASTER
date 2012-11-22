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
package hamlet.inheritance;

import beast.util.Randomizer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hamlet.Population;
import hamlet.ReactionGroup;
import hamlet.State;
import hamlet.Trajectory;
import java.util.List;
import java.util.Map;

/**
 * A class representing an inheritance graph generated under a particular
 * stochastic population dynamics model.  Inheritance trees are a special
 * case in which children have only one parent.
 * 
 * <p>Things to keep in mind if you're reading this code:
 * 
 * <ol>
 * <li>Construction of the graph proceeds in a top-down fashion beginning
 * with the earliest nodes (provided in spec.initNodes), implementing
 * changes as reactions occur and finishing when one of the end conditions
 * is met.</li>
 * 
 * <li>During graph construction, activeLineages maintains a list of node
 * objects that represent single lineages extant at the current time.  The fact
 * that they represent lineages rather than regular graph nodes is important, as
 * it means that the node objects contained in activeLineages may have only
 * one parent, as having more would mean the node represents more than one
 * lineage.</li>
 *</ol>
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class InheritanceGraph extends Trajectory {

    // List of nodes present at the start of the simulation
    public List <Node> startNodes;
    
    // Simulation specification.
    private InheritanceGraphSpec graphSpec;
    
    /**
     * Build an inheritance graph corrsponding to a set of lineages
     * embedded within populations evolving under a birth-death process.
     * 
     * I'm sure there's a more efficient and less ugly way of implementing
     * this.  Suggestions are very welcome. (Tim)
     * 
     * @param spec Inheritanc graph simulation specification.
     */
    public InheritanceGraph(InheritanceGraphSpec spec) {
        
        // Keep a record of the simulation spec and the starting nodes.
        this.graphSpec = spec;
        startNodes = spec.initNodes;
        
        double simulationTime = spec.getSimulationTime();
        
        // Initialise time and activeLineages:
        double t = 0.0;
        List<Node> activeLineages = Lists.newArrayList();
        for (Node node : spec.initNodes) {
            node.setTime(t);
            Node child = new Node(node.population);
            node.addChild(child);
            activeLineages.add(child);
        }
        
        // Initialise system state:
        State currentState = new State(spec.getInitState());
        
        // Integration loop:
        while (true) {
            
            // Check whether any end conditions are met:
            boolean endConditionMet = false;
            for (InheritanceGraphEndCondition graphEndCondition
                    : spec.graphEndConditions) {
                if (graphEndCondition.isMet(activeLineages))
                    endConditionMet = true;
                    break;
            }
            if (endConditionMet)
                break;
            
            // Calculate propensities
            double totalPropensity = 0.0;
            for (ReactionGroup reactionGroup : spec.getModel().getReactionGroups()) {
                reactionGroup.calcPropensities(currentState);
                for (double propensity : reactionGroup.propensities)
                    totalPropensity += propensity;
            }
            
            // End simulation if propensity reaches zero
            // (occurs if we reach an absorbing state)
            if (totalPropensity == 0) {
                t = simulationTime;
                break;
            }
            
            // Draw time of next reaction
            t += Randomizer.nextExponential(totalPropensity);
            
            // Break if new time exceeds end time:
            if (t>simulationTime) {
                t = simulationTime;
                break;
            }
            
            // Choose reaction to implement
            double u = Randomizer.nextDouble()*totalPropensity;
            boolean found = false;
            InheritanceReactionGroup chosenReactionGroup = null;
            int chosenReaction = 0;
            for (InheritanceReactionGroup reactionGroup :
                    spec.inheritanceModel.inheritanceReactionGroups) {
                
                for (int ridx = 0; ridx<reactionGroup.propensities.size(); ridx++) {
                    u -= reactionGroup.propensities.get(ridx);
                    if (u<0) {
                        found = true;
                        chosenReactionGroup = reactionGroup;
                        chosenReaction = ridx;
                    }
                }
                
                if (found)
                    break;
            }
            
            // Select lineages involved in reaction.  This is done by sampling
            // without replacement from the individuals present in the current
            // state.
            Map<Population, Integer> popsSeen = Maps.newHashMap();
            Map<Population, Integer> popsChosen = Maps.newHashMap();
            List<Node> nodesInvolved = Lists.newArrayList();
            List<Node> reactNodesInvolved = Lists.newArrayList();
            for (Node node : activeLineages) {
                if (!chosenReactionGroup.reactCounts
                        .get(chosenReaction).containsKey(node.population))
                    continue;
                
                // Calculate probability that lineage is involved in reaction:
                int m = chosenReactionGroup.reactCounts
                        .get(chosenReaction).get(node.population);
                double N = currentState.get(node.population);
                
                if (popsChosen.containsKey(node.population))
                    m -= popsChosen.get(node.population);
                
                if (popsSeen.containsKey(node.population))
                    N -= popsSeen.get(node.population);
                
                // Decide whether lineage is involved
                if (Randomizer.nextDouble() < m/N) {
                    nodesInvolved.add(node);
                    
                    // Select particular reactant node to use:
                    int idx = Randomizer.nextInt(m);
                    for (Node reactNode :
                            chosenReactionGroup.reactNodes.get(chosenReaction)) {
                        if (reactNode.population != node.population)
                            continue;
                        
                        if (idx == 0) {
                            reactNodesInvolved.add(reactNode);
                            break;
                        } else
                            idx -= 1;
                    }
                    
                    // Update popsChosen and popsSeen
                    if (popsChosen.containsKey(node.population))
                        popsChosen.put(node.population,
                                popsChosen.get(node.population)+1);
                    else
                        popsChosen.put(node.population, 1);
                }
                
                // Keep track of populations seen for future lineage selections:
                if (popsSeen.containsKey(node.population))
                    popsSeen.put(node.population, popsSeen.get(node.population)+1);
                else
                    popsSeen.put(node.population,1);
            }
            
            // Attach reaction graph to inheritance graph
            Map<Node, Node> nextLevelNodes = Maps.newHashMap();
            for (int i=0; i<nodesInvolved.size(); i++) {
                Node node = nodesInvolved.get(i);
                Node reactNode = reactNodesInvolved.get(i);
                
                for (Node reactChild : reactNode.children) {
                    if (nextLevelNodes.containsKey(reactChild))
                        node.addChild(nextLevelNodes.get(reactChild));
                    else {
                        Node child = new Node(reactChild.population);
                        nextLevelNodes.put(reactChild, child);
                        node.addChild(child);
                    }
                }
            }
            
            // Update activeLineages:
            for (Node node : nodesInvolved) {
                                
                if (node.children.size()==1
                        && (node.parents.get(0).population == node.children.get(0).population)) {
                    // Node does not represent a state change
                    
                    // Active lineages are nodes having exactloy one parent:
                    Node parent = node.parents.get(0);                    
                    Node child = node.children.get(0);
                    
                    // Prune from graph
                    int nodeIdx = parent.children.indexOf(node);
                    parent.children.set(nodeIdx, node.children.get(0));
                    
                    nodeIdx = child.parents.indexOf(node);
                    child.parents.set(nodeIdx, parent);
                }
                
                // Ensure node has current time.
                node.setTime(t);
                    
                // Remove from active lineage list
                activeLineages.remove(node);
                    
                // Ensure any children are in active nodes list
                for (Node child : node.children) {
                    if (!activeLineages.contains(child))
                        activeLineages.add(child);
                }

            }
            
            // Deal with multi-parent nodes:
            for (Node node : nextLevelNodes.values()) {
                if (node.parents.size()>1) {
                    node.setTime(t);
                    activeLineages.remove(node);
                    
                    Node child = new Node(node.population);
                    node.addChild(child);
                    activeLineages.add(child);
                }
            }
            
            // Implement state change due to reaction:
            currentState.implementReaction(chosenReactionGroup, chosenReaction, 1);
            
            // End simulation if there are no active lineages remaining.
            if (activeLineages.isEmpty())
                break;
        }
        
        // Fix final time of any remaining active lineages.
        for (Node node : activeLineages)
            node.setTime(t);
    }
    
    /**
     * Retrieve inheritance graph simulation specification.
     * 
     * @return InheritanceGraphSpec object.
     */
    @Override
    public InheritanceGraphSpec getSpec() {
        return graphSpec;
    }
            

    /**
     * Construct inheritance graph from existing network of nodes.
     * 
     * @param startNodes 
     */
    public InheritanceGraph(Node ... startNodes) {
        this.graphSpec = null;
        this.startNodes = Lists.newArrayList(startNodes);
    }
    
}