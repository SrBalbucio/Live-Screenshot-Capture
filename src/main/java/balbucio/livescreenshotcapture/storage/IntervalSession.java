package balbucio.livescreenshotcapture.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Metadata for one interval/auto capture run. Written to {@code session.json} inside the
 * session folder so a future timelapse builder knows how the frames were produced.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntervalSession(String sessionId, String profileId, String profileName,
        String layoutId, String layoutName, String regionId, String regionName,
        int intervalSeconds, int maxMinutes, String format, long startedAtMillis,
        Long endedAtMillis, String endReason, int frameCount, String appVersion) {

    public IntervalSession withIntervalSeconds(int seconds) {
        return new IntervalSession(sessionId, profileId, profileName, layoutId, layoutName,
                regionId, regionName, seconds, maxMinutes, format, startedAtMillis,
                endedAtMillis, endReason, frameCount, appVersion);
    }

    public IntervalSession ended(long endedAtMillis, String reason, int frameCount) {
        return new IntervalSession(sessionId, profileId, profileName, layoutId, layoutName,
                regionId, regionName, intervalSeconds, maxMinutes, format, startedAtMillis,
                endedAtMillis, reason, frameCount, appVersion);
    }
}
