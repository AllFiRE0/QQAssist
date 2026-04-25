package ru.allfire.qqassist.database.models;

import java.util.UUID;

public class Report {

    private final long id;
    private final UUID reporterUuid;
    private final String reporterName;
    private final UUID targetUuid;
    private final String targetName;
    private final String category;
    private final String comment;
    private final String world;
    private final double x, y, z;
    private String status;
    private UUID assigneeUuid;
    private String assigneeName;
    private final long createdAt;
    private long updatedAt;
    private long closedAt;
    private int rewardAmount;

    public Report(long id, UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
                  String category, String comment, String world, double x, double y, double z,
                  String status, long createdAt) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.reporterName = reporterName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.category = category;
        this.comment = comment;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public long getId() { return id; }
    public UUID getReporterUuid() { return reporterUuid; }
    public String getReporterName() { return reporterName; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public String getCategory() { return category; }
    public String getComment() { return comment; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getAssigneeUuid() { return assigneeUuid; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssignee(UUID uuid, String name) { this.assigneeUuid = uuid; this.assigneeName = name; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long time) { this.updatedAt = time; }
    public long getClosedAt() { return closedAt; }
    public void setClosedAt(long time) { this.closedAt = time; }
    public int getRewardAmount() { return rewardAmount; }
    public void setRewardAmount(int amount) { this.rewardAmount = amount; }
}
