package toml_final;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

public class Generation {
    private final int numToKeep;
    private final double mutationChance, mutationRate;
    private final Agent[] agents;

    public Generation(Agent[] agents, int numToKeep, double mutationChance, double mutationRate) {
        this.agents = agents;
        this.numToKeep = numToKeep;
        this.mutationChance = mutationChance;
        this.mutationRate = mutationRate;
    }

    public Generation(NeuralNetwork template, int size, int numToKeep, double mutationChance, double mutationRate) {
        agents = new Agent[size];
        populateAgents(size, template);
        this.numToKeep = numToKeep;
        this.mutationChance = mutationChance;
        this.mutationRate = mutationRate;
    }

    private void populateAgents(int num, NeuralNetwork template) {
        for (int i=0; i < num; i++) {
            agents[i] = new Agent(template.randCopy());
        }
    }

    public Generation nextGeneration() {
        Agent[] best = getBest();
        for (Agent agent : best) {
            agent.resetScore();
        }
        Agent[] nextGenAgents = new Agent[agents.length];
        System.arraycopy(best, 0, nextGenAgents, 0, best.length);
        try {
            best[0].getNetwork().save("networks/Current Best");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Random rand = new Random();

        for (int i=best.length; i < agents.length; i++) {
            nextGenAgents[i] = randomCrossover(best, rand);
        }
        return new Generation(nextGenAgents, numToKeep, mutationChance, mutationRate);
    }

    private @NotNull Agent randomCrossover(Agent @NotNull [] best, @NotNull Random rand) {
        Agent child1 = best[rand.nextInt(best.length)].generateChild(mutationChance, mutationRate);
        Agent child2 = best[rand.nextInt(best.length)].generateChild(mutationChance, mutationRate);
        Layer curLayer1 = child1.getNetwork().getInputLayer();
        Layer curLayer2 = child2.getNetwork().getInputLayer();
        while (curLayer1.hasNext() && curLayer2.hasNext()) {
            float[][] weights1 = curLayer1.getWeights();
            float[][] weights2 = curLayer2.getWeights();
            for (int i = 0; i < weights1.length; i++) {
                if (rand.nextBoolean()) {
//                    System.arraycopy(weights2[i], 0, weights1[i], 0, weights1[i].length);
                    weights1[i] = weights2[i];
                }
            }
            curLayer1 =  curLayer1.getNext();
            curLayer2 = curLayer2.getNext();
        }
        return child1;
    }

    private Agent clone(Agent @NotNull [] best, @NotNull Random rand) {
        Agent parent = best[rand.nextInt(best.length)];
        return parent.generateChild(mutationChance, mutationRate);
    }

    private Agent @NotNull [] getBest() {
        Arrays.sort(agents, Comparator.comparingDouble(Agent::getScore).reversed());
        return Arrays.copyOfRange(agents, 0, numToKeep);
    }

    /**
     * Replaces all of the agents in this generation with children of one parent.
     * @param parent
     */
    public void baseOn(NeuralNetwork parent) {
        Agent parentAgent = new Agent(parent);

        for (int i=0; i < agents.length; i++) {
            agents[i] = parentAgent.generateChild(mutationChance, mutationRate);
        }
    }


    public int getSize() {
        return agents.length;
    }

    public Agent[] getAgents() {
        return agents;
    }
}
