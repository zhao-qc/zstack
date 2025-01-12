package org.zstack.header.vm;

public enum VmPriorityLevel {
    Normal(512, 0),
    High(1024, -900);

    private int cpuShares;

    private int oomScoreAdj;

    VmPriorityLevel(int cpuShares, int oomScoreAdj) {
        this.cpuShares = cpuShares;
        this.oomScoreAdj = oomScoreAdj;
    }

    public int getCpuShares() {
        return cpuShares;
    }

    public void setCpuShares(int cpuShares) {
        this.cpuShares = cpuShares;
    }

    public int getOomScoreAdj() {
        return oomScoreAdj;
    }

    public void setOomScoreAdj(int oomScoreAdj) {
        this.oomScoreAdj = oomScoreAdj;
    }
}
