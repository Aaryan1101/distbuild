package com.example.data;

import com.example.shared.StringUtils;

public class UserRepository {
    public boolean createUser(String email, String name) {
        if (!StringUtils.isValidEmail(email)) {
            return false;
        }
        
        String formattedName = StringUtils.capitalize(name);
        
        // Simulate database operation
        System.out.println("Created user: " + formattedName + " (" + email + ")");
        return true;
    }
    
    public boolean updateUser(String email, String newName) {
        if (!StringUtils.isValidEmail(email)) {
            return false;
        }
        
        String formattedName = StringUtils.capitalize(newName);
        System.out.println("Updated user: " + email + " -> " + formattedName);
        return true;
    }
}
