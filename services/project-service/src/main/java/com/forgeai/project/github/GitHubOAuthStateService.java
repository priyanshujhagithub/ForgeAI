package com.forgeai.project.github;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubOAuthStateService {

    private final Map<String,Long> states=new ConcurrentHashMap<>();

    public String createState(Long userId){
        String state= UUID.randomUUID().toString();
        states.put(state,userId);
        return state;
    }

    public Long consumeState(String state){
        return states.remove(state);
    }
}
