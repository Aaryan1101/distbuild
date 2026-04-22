package com.example;

import com.google.common.base.Strings;

public class App {
    private final String name;
    
    public App(String name) {
        this.name = Strings.nullToEmpty(name);
    }
    
    public String getGreeting() {
        return "Hello from " + name + "!";
    }
    
    public static void main(String[] args) {
        App app = new App(args.length > 0 ? args[0] : "World");
        System.out.println(app.getGreeting());
    }
}
