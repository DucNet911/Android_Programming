package com.puzzlehub.sudoku.data.repository;

public record PlayerProfile(String name, String avatar, String email, boolean signedIn, String description,
                            String background, String country, String username, boolean demo) {
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
    public String getEmail() { return email; }
    public boolean getSignedIn() { return signedIn; }
    public String getDescription() { return description; }
    public String getBackground() { return background; }
    public String getCountry() { return country; }
    public String getUsername() { return username; }
    public boolean getDemo() { return demo; }
}
