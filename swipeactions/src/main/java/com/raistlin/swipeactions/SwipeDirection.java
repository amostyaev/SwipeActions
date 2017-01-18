package com.raistlin.swipeactions;

public enum SwipeDirection {

    NONE, LEFT, RIGHT;

    int getMultiplier() {
        switch (this) {
            case NONE:
                return 0;
            case LEFT:
                return 1;
            case RIGHT:
                return -1;
        }
        return 0;
    }
}
