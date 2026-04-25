package ru.allfire.qqassist.database.models;

import java.util.UUID;

public class PlayerProfile {
    private final long id;
    private UUID minecraftUuid;
    private String telegramId;
    private String minecraftName;
    private String telegramName;
    private boolean linked;
    private int checkinPoints;
    private long lastCheckin;
    private int checkinStreak;
    private int totalCheckins;
    private int giftedPoints;
    private int receivedPoints;
    private long linkedAt;
    
    public PlayerProfile(long id, UUID minecraftUuid, String telegramId, String minecraftName,
                         String telegramName, boolean linked, int checkinPoints, long lastCheckin,
                         int checkinStreak, int totalCheckins, int giftedPoints, int receivedPoints, long linkedAt) {
        this.id = id;
        this.minecraftUuid = minecraftUuid;
        this.telegramId = telegramId;
        this.minecraftName = minecraftName;
        this.telegramName = telegramName;
        this.linked = linked;
        this.checkinPoints = checkinPoints;
        this.lastCheckin = lastCheckin;
        this.checkinStreak = checkinStreak;
        this.totalCheckins = totalCheckins;
        this.giftedPoints = giftedPoints;
        this.receivedPoints = receivedPoints;
        this.linkedAt = linkedAt;
    }
    
    // Геттеры и сеттеры
    public long getId() { return id; }
    public UUID getMinecraftUuid() { return minecraftUuid; }
    public void setMinecraftUuid(UUID uuid) { this.minecraftUuid = uuid; }
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String id) { this.telegramId = id; }
    public String getMinecraftName() { return minecraftName; }
    public void setMinecraftName(String name) { this.minecraftName = name; }
    public String getTelegramName() { return telegramName; }
    public void setTelegramName(String name) { this.telegramName = name; }
    public boolean isLinked() { return linked; }
    public void setLinked(boolean linked) { this.linked = linked; }
    public int getCheckinPoints() { return checkinPoints; }
    public void setCheckinPoints(int points) { this.checkinPoints = points; }
    public void addCheckinPoints(int points) { this.checkinPoints += points; this.totalCheckins++; }
    public void removeCheckinPoints(int points) { this.checkinPoints = Math.max(0, this.checkinPoints - points); }
    public long getLastCheckin() { return lastCheckin; }
    public void setLastCheckin(long time) { this.lastCheckin = time; }
    public int getCheckinStreak() { return checkinStreak; }
    public void incrementStreak() { this.checkinStreak++; }
    public int getTotalCheckins() { return totalCheckins; }
    public int getGiftedPoints() { return giftedPoints; }
    public void addGiftedPoints(int points) { this.giftedPoints += points; }
    public int getReceivedPoints() { return receivedPoints; }
    public void addReceivedPoints(int points) { this.receivedPoints += points; }
    public long getLinkedAt() { return linkedAt; }
    public void setLinkedAt(long time) { this.linkedAt = time; }
}
