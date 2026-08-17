/* eslint-disable max-len, require-jsdoc, object-curly-spacing, operator-linebreak */
import { initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onValueWritten } from "firebase-functions/v2/database";
import { logger } from "firebase-functions/logger";

initializeApp();

const database = getDatabase();
const rootRef = database.ref();
const floorPlansRef = database.ref("floorPlans");
const usageReportsRef = database.ref("usageReports");
const safetyAlertsRef = database.ref("safetyAlerts");

type DeviceRecord = {
  id?: string;
  name?: string;
  type?: string;
  status?: string;
  maxOnDurationMinutes?: number;
  lastTurnedOnAt?: number | null;
  scheduleStart?: string | null;
  scheduleEnd?: string | null;
};

type PolicyRunSummary = {
  dryRun: boolean;
  devicesChecked: number;
  safetyCutoffs: number;
  scheduleCorrections: number;
  alertsCreated: number;
  updatesQueued: number;
};

function parseMinutesOfDay(time: string): number | null {
  const parts = time.split(":");
  if (parts.length !== 2) {
    return null;
  }

  const hours = Number(parts[0]);
  const minutes = Number(parts[1]);

  if (!Number.isInteger(hours) || !Number.isInteger(minutes)) {
    return null;
  }

  if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
    return null;
  }

  return hours * 60 + minutes;
}

function isWithinSchedule(currentMinutes: number, startMinutes: number, endMinutes: number): boolean {
  if (startMinutes <= endMinutes) {
    return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
  }

  return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
}

async function createSafetyAlert(device: DeviceRecord, reason: string, durationMinutes?: number): Promise<void> {
  await safetyAlertsRef.push({
    deviceId: device.id ?? null,
    deviceName: device.name ?? "Unknown device",
    deviceType: device.type ?? "Unknown",
    reason,
    durationMinutes: durationMinutes ?? null,
    createdAt: Date.now(),
  });
}

async function createUsageReport(device: DeviceRecord, durationMinutes: number, powerConsumedWh: number): Promise<void> {
  await usageReportsRef.push({
    id: `auto-${Date.now()}-${device.id ?? "device"}`,
    deviceName: device.name ?? "Unknown device",
    deviceType: device.type ?? "Unknown",
    durationMinutes,
    powerConsumedWh,
    timestamp: Date.now(),
  });
}

async function runSmartHomePolicyScan(options: { dryRun: boolean; source: string }): Promise<PolicyRunSummary> {
  const { dryRun, source } = options;
  const now = Date.now();
  const currentMinutes = new Date(now).getHours() * 60 + new Date(now).getMinutes();
  const updates: Record<string, unknown> = {};

  let devicesChecked = 0;
  let safetyCutoffs = 0;
  let scheduleCorrections = 0;
  let alertsCreated = 0;

  const floorPlansSnapshot = await floorPlansRef.get();

  floorPlansSnapshot.forEach((floorSnap) => {
    floorSnap.child("rooms").forEach((roomSnap) => {
      roomSnap.child("devices").forEach((deviceSnap) => {
        const device = deviceSnap.val() as DeviceRecord | null;
        if (!device) {
          return;
        }

        devicesChecked += 1;

        const floorId = floorSnap.key;
        const roomId = roomSnap.key;
        const deviceId = deviceSnap.key;

        if (!floorId || !roomId || !deviceId) {
          return;
        }

        const devicePath = `floorPlans/${floorId}/rooms/${roomId}/devices/${deviceId}`;

        if (device.type === "SafetyDevice") {
          const maxDurationMinutes = Number(device.maxOnDurationMinutes ?? 15);
          const lastTurnedOnAt = Number(device.lastTurnedOnAt ?? 0);
          const isOverLimit =
            device.status === "ON" &&
            lastTurnedOnAt > 0 &&
            now - lastTurnedOnAt > maxDurationMinutes * 60_000;

          if (isOverLimit) {
            safetyCutoffs += 1;
            updates[`${devicePath}/status`] = "OFF";

            const elapsedMinutes = Math.max(1, Math.floor((now - lastTurnedOnAt) / 60_000));

            void createSafetyAlert(device, "max_on_duration_breached", elapsedMinutes);
            void createUsageReport(device, elapsedMinutes, elapsedMinutes * 30);
            alertsCreated += 1;
          }
        }

        if (device.type === "Light") {
          const startMinutes = device.scheduleStart ? parseMinutesOfDay(device.scheduleStart) : null;
          const endMinutes = device.scheduleEnd ? parseMinutesOfDay(device.scheduleEnd) : null;

          if (startMinutes != null && endMinutes != null) {
            const shouldBeOn = isWithinSchedule(currentMinutes, startMinutes, endMinutes);

            if (shouldBeOn && device.status !== "ON") {
              scheduleCorrections += 1;
              updates[`${devicePath}/status`] = "ON";
            } else if (!shouldBeOn && device.status !== "OFF") {
              scheduleCorrections += 1;
              updates[`${devicePath}/status`] = "OFF";
            }
          }
        }
      });
    });
  });

  if (!dryRun && Object.keys(updates).length > 0) {
    await rootRef.update(updates);
  }

  logger.info("Smart Home policy scan completed", {
    source,
    dryRun,
    devicesChecked,
    safetyCutoffs,
    scheduleCorrections,
    alertsCreated,
    updatesQueued: Object.keys(updates).length,
  });

  return {
    dryRun,
    devicesChecked,
    safetyCutoffs,
    scheduleCorrections,
    alertsCreated,
    updatesQueued: Object.keys(updates).length,
  };
}

export const smartHomeWatchdog = onSchedule(
  {
    schedule: "every 1 minutes",
    timeZone: "Asia/Colombo",
  },
  async () => {
    await runSmartHomePolicyScan({ dryRun: false, source: "scheduled-watchdog" });
  }
);

export const testSmartHomeWatchdog = onRequest(async (req, res) => {
  if (req.method !== "GET" && req.method !== "POST") {
    res.status(405).json({ error: "Use GET or POST to run the test watchdog." });
    return;
  }

  const dryRun = req.query.dryRun !== "false";
  const result = await runSmartHomePolicyScan({
    dryRun,
    source: "http-test-watchdog",
  });

  res.status(200).json({
    message: dryRun
      ? "Dry run completed. No database updates were written."
      : "Policy scan completed and database updates were applied.",
    result,
  });
});

export const stampSafetyDeviceTurnOnTime = onValueWritten(
  {
    ref: "floorPlans/{floorId}/rooms/{roomId}/devices/{deviceId}",
  },
  async (event) => {
    const after = event.data.after.val() as DeviceRecord | null;
    if (!after || after.type !== "SafetyDevice" || after.status !== "ON") {
      return;
    }

    const before = event.data.before.val() as DeviceRecord | null;
    const turnedOnJustNow = before?.status !== "ON";

    if (!turnedOnJustNow && after.lastTurnedOnAt != null) {
      return;
    }

    const floorId = event.params.floorId;
    const roomId = event.params.roomId;
    const deviceId = event.params.deviceId;

    await database
      .ref(`floorPlans/${floorId}/rooms/${roomId}/devices/${deviceId}`)
      .update({ lastTurnedOnAt: Date.now() });

    logger.info("Stamped safety device turn-on time", {
      floorId,
      roomId,
      deviceId,
    });
  }
);
