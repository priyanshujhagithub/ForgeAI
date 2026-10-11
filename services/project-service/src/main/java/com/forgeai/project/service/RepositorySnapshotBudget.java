package com.forgeai.project.service;

import org.springframework.stereotype.Component;

@Component
public class RepositorySnapshotBudget {

    private static final int MAX_FILES=200;
    private static final int MAX_FILES_SIZE_BYTES=500*1024;
    private static final long MAX_TOTAL_SIZE_BYTES=5L*1024*1024;

    public boolean canIncludeFile(
            int currentFileCount,
            long currentTotalSize,
            Integer fileSize
    ){
        if(currentFileCount>=MAX_FILES){
            return false;
        }

        if(fileSize==null || fileSize<0){
            return false;
        }

        if(fileSize>MAX_FILES_SIZE_BYTES){
            return false;
        }

        return currentTotalSize+fileSize<=MAX_TOTAL_SIZE_BYTES;
    }
}
