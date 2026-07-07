package gg.MC7DZ.teamify.team;

public enum TeamRole {
    MEMBER(0),
    MODERATOR(1),
    CO_LEADER(2),
    LEADER(3);

    private final int weight;

    TeamRole(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isAtLeast(TeamRole other) {
        return this.weight >= other.weight;
    }

    public TeamRole next() {
        TeamRole[] values = values();
        int idx = ordinal();
        return idx + 1 < values.length ? values[idx + 1] : this;
    }

    public TeamRole previous() {
        TeamRole[] values = values();
        int idx = ordinal();
        return idx - 1 >= 0 ? values[idx - 1] : this;
    }
}
