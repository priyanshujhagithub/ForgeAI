package com.forgeai.project.exception;

public class RunLimitReachedException extends RuntimeException {

    public RunLimitReachedException(int dailyLimit) {
        super("You have started " + dailyLimit + " runs today, the daily limit."
                + " It resets at midnight UTC");
    }
}
