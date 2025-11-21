package com.example.toml_final;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

public class Generation {
    private int numToKeep;
    private double mutationChance, mutationRate;
    private Agent[] agents;

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

        Random rand = new Random();

        for (int i=best.length; i < agents.length; i++) {
            Agent parent = best[rand.nextInt(best.length)];
            nextGenAgents[i] = parent.generateChild(mutationChance, mutationRate);
        }
        return new Generation(nextGenAgents, numToKeep, mutationChance, mutationRate);
    }

    private Agent @NotNull [] getBest() {
        Arrays.sort(agents, Comparator.comparingDouble(Agent::getScore).reversed());
        return Arrays.copyOfRange(agents, 0, numToKeep);
    }


    public int getSize() {
        return agents.length;
    }

    public Agent[] getAgents() {
        return agents;
    }
}
