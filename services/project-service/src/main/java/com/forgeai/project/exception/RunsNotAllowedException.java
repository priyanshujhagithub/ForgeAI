package com.forgeai.project.exception;

public class RunsNotAllowedException extends RuntimeException {

    public RunsNotAllowedException() {
        super("This ForgeAI deployment only starts runs for invited accounts");
    }
}
